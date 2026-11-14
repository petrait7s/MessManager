@file:OptIn(ExperimentalMaterial3Api::class)

package com.messmanager.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messmanager.app.domain.Dates
import com.messmanager.app.domain.Money

/** Card with perspective tilt following touch / mouse pointer, layered shadow and moving highlight. */
@Composable
fun TiltCard(
    modifier: Modifier = Modifier,
    accent: Color = Emerald,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduce = LocalReduceMotion.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var nx by remember { mutableFloatStateOf(0f) }
    var ny by remember { mutableFloatStateOf(0f) }
    var active by remember { mutableStateOf(false) }
    val spec = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
    val ax by animateFloatAsState(nx, spec, label = "tiltX")
    val ay by animateFloatAsState(ny, spec, label = "tiltY")
    val sc by animateFloatAsState(if (active && !reduce) 0.985f else 1f, spring(), label = "cardScale")
    val elev by animateDpAsState(if (active && !reduce) 20.dp else 10.dp, label = "cardElev")
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(22.dp)
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier
            .graphicsLayer {
                rotationX = -ay * 7f; rotationY = ax * 7f; scaleX = sc; scaleY = sc
                cameraDistance = 14f * density
            }
            .shadow(elev, shape, ambientColor = accent, spotColor = accent)
            .clip(shape)
            .background(cardBrush(accent))
            .border(1.dp, outline, shape)
            .onSizeChanged { size = it }
            .pointerInput(reduce) {
                if (reduce) return@pointerInput
                awaitEachGesture {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull() ?: break
                        when (ev.type) {
                            PointerEventType.Press, PointerEventType.Move, PointerEventType.Enter -> if (size.width > 0 && size.height > 0) {
                                nx = (ch.position.x / size.width * 2f - 1f).coerceIn(-1f, 1f)
                                ny = (ch.position.y / size.height * 2f - 1f).coerceIn(-1f, 1f)
                                active = true
                            }
                            PointerEventType.Release, PointerEventType.Exit -> { nx = 0f; ny = 0f; active = false }
                            else -> {}
                        }
                        if (ev.changes.none { it.pressed } && ev.type != PointerEventType.Move && ev.type != PointerEventType.Enter) break
                    }
                    nx = 0f; ny = 0f; active = false
                }
            }
            .drawWithContent {
                drawContent()
                if (active && !reduce) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset((nx + 1f) / 2f * size.width, (ny + 1f) / 2f * size.height),
                            radius = maxOf(size.width, size.height) * 0.7f,
                        )
                    )
                }
            }
            .then(clickMod)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun PressButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val sc by animateFloatAsState(if (pressed && !reduce) 0.94f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "btnScale")
    val el by animateDpAsState(if (pressed) 1.dp else 6.dp, label = "btnElev")
    Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = sc; scaleY = sc },
        enabled = enabled,
        interactionSource = src,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = contentColor),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = el, pressedElevation = 1.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun animatedLong(target: Long): Long {
    val reduce = LocalReduceMotion.current
    val f = remember { Animatable(1f) }
    var from by remember { mutableStateOf(0L) }
    var to by remember { mutableStateOf(0L) }
    LaunchedEffect(target) {
        if (reduce) { from = target; to = target; return@LaunchedEffect }
        from = from + ((to - from).toDouble() * f.value).toLong()
        to = target
        f.snapTo(0f)
        f.animateTo(1f, tween(650))
    }
    return from + ((to - from).toDouble() * f.value).toLong()
}

@Composable
fun MoneyText(amount: Long, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleLarge, color: Color = Color.Unspecified, animate: Boolean = true, prefix: String = "৳ ") {
    val v = if (animate) animatedLong(amount) else amount
    Text(prefix + Money.format(v), modifier = modifier, style = style.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { contentDescription = text })
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Emerald.copy(alpha = .35f), Electric.copy(alpha = .35f)))))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun AppField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), numeric: Boolean = false, multiline: Boolean = false, password: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = !multiline, minLines = if (multiline) 3 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.NumberPassword else if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(14.dp), modifier = modifier,
    )
}

@Composable
fun <T> DropdownField(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: "", onValueChange = {}, readOnly = true,
            label = { Text(label) }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true }.semantics { contentDescription = label })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o.second) }, onClick = { onSelect(o.first); open = false }) }
        }
    }
}

@Composable
fun DateField(label: String, day: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(value = Dates.fmt(day), onValueChange = {}, readOnly = true, label = { Text(label) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().clickable { open = true }.semantics { contentDescription = "$label ${Dates.fmt(day)}" })
    }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = day * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { onChange(Math.floorDiv(it, 86_400_000L)) }; open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = st) }
    }
}

@Composable
fun Sheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String = "Confirm", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel / বাতিল") } },
    )
}

@Composable
fun BalanceBadge(balance: Long, modifier: Modifier = Modifier) {
    val (txt, col) = when {
        balance < 0 -> "Due ${Money.format(-balance)}" to Danger
        balance > 0 -> "Advance ${Money.format(balance)}" to Emerald
        else -> "Settled" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(txt, modifier = modifier.clip(RoundedCornerShape(50)).background(col.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = col, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), maxLines = 1)
}

@Composable
fun BarChart(values: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val g by animateFloatAsState(if (go || reduce) 1f else 0f, tween(800), label = "bars")
    val max = (values.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val bar = Emerald
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Canvas(modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Daily spending chart" }) {
        if (values.isEmpty()) return@Canvas
        val slot = size.width / values.size
        val w = (slot * 0.6f).coerceAtMost(18.dp.toPx())
        drawLine(track, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        values.forEachIndexed { i, v ->
            val h = size.height * (v.second.toFloat() / max) * g
            if (v.second > 0) drawRoundRect(
                Brush.verticalGradient(listOf(bar, Electric), startY = size.height - h, endY = size.height),
                topLeft = Offset(i * slot + (slot - w) / 2f, size.height - h), size = Size(w, h), cornerRadius = CornerRadius(w / 3f),
            )
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Emerald) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "progress")
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(color, Electric))))
    }
}

@Composable
fun MiniStat(label: String, amount: Long, accent: Color, modifier: Modifier = Modifier) {
    TiltCard(modifier, accent) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        MoneyText(amount, style = MaterialTheme.typography.titleMedium)
    }
}

fun categoryColor(c: String): Color = when (c) {
    "Rent" -> Color(0xFF8B5CF6); "WiFi" -> Electric; "Electricity" -> Amber; "Khala/Cook" -> Color(0xFFEC4899)
    "Gas" -> Color(0xFFF97316); "Water" -> Color(0xFF06B6D4); "Bazar" -> Emerald; "Maintenance" -> Color(0xFF64748B)
    else -> Color(0xFFA3A3A3)
}
