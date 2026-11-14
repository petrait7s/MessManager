package com.messmanager.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messmanager.app.data.*
import com.messmanager.app.domain.Accounting
import com.messmanager.app.domain.Dates
import com.messmanager.app.domain.MonthSummary
import com.messmanager.app.domain.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class Boot(val loaded: Boolean = false, val mess: Mess? = null, val hasPin: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class AppVm(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app, AppDatabase.get(app))
    val prefs = Prefs(app)
    val backup = BackupManager(repo) { File(app.filesDir, "receipts") }
    private val dao = repo.dao

    val boot: StateFlow<Boot> = combine(dao.observeMess(), dao.observeSetting("pin_hash")) { m, p -> Boot(true, m, p != null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Boot())
    val unlocked = MutableStateFlow(false)
    /** Recovery code to show once after setup / PIN change. */
    val recovery = MutableStateFlow<String?>(null)
    val month = MutableStateFlow(Dates.currentMonth())
    val uiPrefs: StateFlow<UiPrefs> = prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UiPrefs())

    val snapshot: StateFlow<Snapshot> = repo.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, Snapshot())
    val summary: StateFlow<MonthSummary> = combine(snapshot, month) { s, m -> Accounting.summary(s, m) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, Accounting.summary(Snapshot(), Dates.currentMonth()))
    val duties: StateFlow<List<BazarDuty>> = dao.observeDuties().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notes: StateFlow<List<Note>> = dao.observeNotes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val audit: StateFlow<List<AuditLog>> = dao.observeAudit().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val msgs = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private var lastStop = 0L

    init {
        viewModelScope.launch { autoBackupIfDue() }
    }

    fun toast(s: String) { msgs.tryEmit(s) }

    /** Run a repository action, surface errors as messages. */
    fun run(okMsg: String? = null, onDone: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                okMsg?.let { toast(it) }
                onDone()
            } catch (e: ValidationException) { toast(e.message ?: "Invalid input")
            } catch (e: ClosedMonthException) { toast(e.message ?: "Month closed")
            } catch (e: BackupException) { toast(e.message ?: "Backup error")
            } catch (e: Exception) { toast("Error: ${e.message}") }
        }
    }

    fun changePin(oldPin: String, newPin: String, onDone: () -> Unit) {
        viewModelScope.launch {
            if (!repo.verifyPin(oldPin)) { toast("Wrong PIN / ভুল পিন"); return@launch }
            run(null, onDone) {
                val code = Security.newRecoveryCode()
                repo.setPin(newPin, code)
                repo.audit("PIN_CHANGE", "security", 0, "PIN changed", "")
                recovery.value = code
            }
        }
    }

    fun shiftMonth(n: Long) { month.value = Dates.plus(month.value, n) }

    // lock handling
    fun onStop() { lastStop = System.currentTimeMillis() }
    fun onStart() { if (lastStop != 0L && System.currentTimeMillis() - lastStop > 30_000) unlocked.value = false }
    fun lock() { unlocked.value = false }

    fun unlock(pin: String, onFail: () -> Unit) {
        viewModelScope.launch {
            if (repo.verifyPin(pin)) unlocked.value = true else onFail()
        }
    }

    fun verifyPinThen(pin: String, onFail: () -> Unit, ok: () -> Unit) {
        viewModelScope.launch { if (repo.verifyPin(pin)) ok() else onFail() }
    }

    private suspend fun autoBackupIfDue() {
        val p = prefs.flow.first()
        if (!p.autoBackup || System.currentTimeMillis() - p.lastAutoBackup < 24L * 3600_000) return
        if (dao.allMess().isEmpty()) return
        try { writeAutoBackup(); prefs.setLastAuto(System.currentTimeMillis()) } catch (_: Exception) {}
    }

    suspend fun writeAutoBackup(): File {
        val ctx = getApplication<Application>()
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "auto_backups").also { it.mkdirs() }
        val f = File(dir, "auto-${System.currentTimeMillis()}.zip")
        f.outputStream().use { backup.export(it) }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(7)?.forEach { it.delete() }
        return f
    }

    fun exportTo(uri: Uri) = run("Backup saved / ব্যাকআপ সংরক্ষিত") {
        val ctx = getApplication<Application>()
        val out = ctx.contentResolver.openOutputStream(uri) ?: throw ValidationException("Cannot write file")
        out.use { backup.export(it) }
    }

    fun restoreFrom(uri: Uri) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val input = ctx.contentResolver.openInputStream(uri) ?: throw BackupException("Cannot open file")
                val r = input.use { backup.restore(it) }
                unlocked.value = false
                toast("Restored: $r")
            } catch (e: BackupException) { toast(e.message ?: "Restore failed")
            } catch (e: Exception) { toast("Restore failed: ${e.message}") }
        }
    }
}
