package com.messmanager.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessDao {
    // mess
    @Query("SELECT * FROM mess WHERE id = 1") fun observeMess(): Flow<Mess?>
    @Query("SELECT * FROM mess") suspend fun allMess(): List<Mess>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMess(m: Mess)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMessAll(l: List<Mess>)
    @Query("DELETE FROM mess") suspend fun clearMess()

    // member
    @Query("SELECT * FROM member ORDER BY id") fun observeMembers(): Flow<List<Member>>
    @Query("SELECT * FROM member ORDER BY id") suspend fun allMembers(): List<Member>
    @Insert suspend fun insertMember(m: Member): Long
    @Update suspend fun updateMember(m: Member)
    @Delete suspend fun deleteMember(m: Member)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMemberAll(l: List<Member>)
    @Query("DELETE FROM member") suspend fun clearMembers()
    @Query(
        "SELECT (SELECT COUNT(*) FROM expense_split WHERE memberId=:id) + (SELECT COUNT(*) FROM deposit WHERE memberId=:id) + " +
            "(SELECT COUNT(*) FROM payment WHERE memberId=:id) + (SELECT COUNT(*) FROM bazar_duty WHERE memberId=:id) + " +
            "(SELECT COUNT(*) FROM expense WHERE payerId=:id) + (SELECT COUNT(*) FROM bazar_entry WHERE purchasedById=:id)"
    )
    suspend fun memberRefCount(id: Long): Int

    // expense
    @Query("SELECT * FROM expense ORDER BY day DESC, id DESC") fun observeExpenses(): Flow<List<Expense>>
    @Query("SELECT * FROM expense ORDER BY id") suspend fun allExpenses(): List<Expense>
    @Query("SELECT * FROM expense WHERE id=:id") suspend fun expense(id: Long): Expense?
    @Insert suspend fun insertExpense(e: Expense): Long
    @Update suspend fun updateExpense(e: Expense)
    @Query("DELETE FROM expense WHERE id=:id") suspend fun deleteExpense(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExpenseAll(l: List<Expense>)
    @Query("DELETE FROM expense") suspend fun clearExpenses()

    // split
    @Query("SELECT * FROM expense_split") fun observeSplits(): Flow<List<ExpenseSplit>>
    @Query("SELECT * FROM expense_split ORDER BY id") suspend fun allSplits(): List<ExpenseSplit>
    @Insert suspend fun insertSplits(l: List<ExpenseSplit>)
    @Query("DELETE FROM expense_split WHERE expenseId=:id") suspend fun deleteSplitsOf(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSplitAll(l: List<ExpenseSplit>)
    @Query("DELETE FROM expense_split") suspend fun clearSplits()

    // payment
    @Query("SELECT * FROM payment ORDER BY day DESC, id DESC") fun observePayments(): Flow<List<Payment>>
    @Query("SELECT * FROM payment ORDER BY id") suspend fun allPayments(): List<Payment>
    @Query("SELECT * FROM payment WHERE id=:id") suspend fun payment(id: Long): Payment?
    @Insert suspend fun insertPayment(p: Payment): Long
    @Query("DELETE FROM payment WHERE id=:id") suspend fun deletePayment(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPaymentAll(l: List<Payment>)
    @Query("DELETE FROM payment") suspend fun clearPayments()

    // deposit
    @Query("SELECT * FROM deposit ORDER BY day DESC, id DESC") fun observeDeposits(): Flow<List<Deposit>>
    @Query("SELECT * FROM deposit ORDER BY id") suspend fun allDeposits(): List<Deposit>
    @Query("SELECT * FROM deposit WHERE id=:id") suspend fun deposit(id: Long): Deposit?
    @Insert suspend fun insertDeposit(d: Deposit): Long
    @Query("DELETE FROM deposit WHERE id=:id") suspend fun deleteDeposit(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDepositAll(l: List<Deposit>)
    @Query("DELETE FROM deposit") suspend fun clearDeposits()

    // bazar
    @Query("SELECT * FROM bazar_entry ORDER BY day DESC, id DESC") fun observeBazar(): Flow<List<BazarEntry>>
    @Query("SELECT * FROM bazar_entry ORDER BY id") suspend fun allBazar(): List<BazarEntry>
    @Query("SELECT * FROM bazar_entry WHERE id=:id") suspend fun bazarEntry(id: Long): BazarEntry?
    @Insert suspend fun insertBazar(e: BazarEntry): Long
    @Update suspend fun updateBazar(e: BazarEntry)
    @Query("DELETE FROM bazar_entry WHERE id=:id") suspend fun deleteBazar(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBazarAll(l: List<BazarEntry>)
    @Query("DELETE FROM bazar_entry") suspend fun clearBazar()

    @Query("SELECT * FROM bazar_item") fun observeItems(): Flow<List<BazarItem>>
    @Query("SELECT * FROM bazar_item ORDER BY id") suspend fun allItems(): List<BazarItem>
    @Insert suspend fun insertItems(l: List<BazarItem>)
    @Query("DELETE FROM bazar_item WHERE entryId=:id") suspend fun deleteItemsOf(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertItemAll(l: List<BazarItem>)
    @Query("DELETE FROM bazar_item") suspend fun clearItems()

    // duty
    @Query("SELECT * FROM bazar_duty ORDER BY day") fun observeDuties(): Flow<List<BazarDuty>>
    @Query("SELECT * FROM bazar_duty ORDER BY day") suspend fun allDuties(): List<BazarDuty>
    @Query("SELECT * FROM bazar_duty WHERE day=:day") suspend fun dutyOn(day: Long): BazarDuty?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDuty(d: BazarDuty): Long
    @Query("DELETE FROM bazar_duty WHERE id=:id") suspend fun deleteDuty(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDutyAll(l: List<BazarDuty>)
    @Query("DELETE FROM bazar_duty") suspend fun clearDuties()

    // notes
    @Query("SELECT * FROM note ORDER BY pinned DESC, day DESC, id DESC") fun observeNotes(): Flow<List<Note>>
    @Query("SELECT * FROM note ORDER BY id") suspend fun allNotes(): List<Note>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertNote(n: Note): Long
    @Query("DELETE FROM note WHERE id=:id") suspend fun deleteNote(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNoteAll(l: List<Note>)
    @Query("DELETE FROM note") suspend fun clearNotes()

    // cycle
    @Query("SELECT * FROM monthly_cycle") fun observeCycles(): Flow<List<MonthlyCycle>>
    @Query("SELECT * FROM monthly_cycle") suspend fun allCycles(): List<MonthlyCycle>
    @Query("SELECT * FROM monthly_cycle WHERE month=:m") suspend fun cycle(m: String): MonthlyCycle?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCycle(c: MonthlyCycle)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCycleAll(l: List<MonthlyCycle>)
    @Query("DELETE FROM monthly_cycle") suspend fun clearCycles()

    // audit
    @Query("SELECT * FROM audit_log ORDER BY id DESC LIMIT 300") fun observeAudit(): Flow<List<AuditLog>>
    @Query("SELECT * FROM audit_log ORDER BY id") suspend fun allAudit(): List<AuditLog>
    @Insert suspend fun insertAudit(a: AuditLog)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAuditAll(l: List<AuditLog>)
    @Query("DELETE FROM audit_log") suspend fun clearAudit()

    // settings
    @Query("SELECT value FROM app_settings WHERE `key`=:k") fun observeSetting(k: String): Flow<String?>
    @Query("SELECT value FROM app_settings WHERE `key`=:k") suspend fun setting(k: String): String?
    @Query("SELECT * FROM app_settings") suspend fun allSettings(): List<AppSettings>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSetting(s: AppSettings)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSettingAll(l: List<AppSettings>)
    @Query("DELETE FROM app_settings") suspend fun clearSettings()
}
