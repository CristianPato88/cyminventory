package com.cym.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Píldora de filtro: icono opcional, texto y contador opcional. [idleColor] cambia el fondo sin seleccionar (p. ej. dentro de hojas). */
@Composable
internal fun FilterPill(label: String, icon: String?, count: Int?, selected: Boolean, idleColor: Color? = null, onClick: () -> Unit) {
    val colors = LocalPalette.current
    Row(Modifier.clip(CircleShape).background(if (selected) colors.pine else idleColor ?: colors.paper).clickable(onClick = onClick)
        .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) Text(icon, fontSize = 13.sp)
        Text(label, color = if (selected) colors.onPine else colors.muted, fontFamily = Manrope,
            fontWeight = FontWeight.Bold, fontSize = 11.sp)
        if (count != null) Text(count.toString(), color = if (selected) colors.mint else colors.pineLight,
            fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

/** Fila de opciones excluyentes con título, para hojas de filtros. */
@Composable
internal fun <T> OptionRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), color = colors.pineLight, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = 10.sp, letterSpacing = 1.2.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                val active = value == selected
                Box(Modifier.clip(CircleShape).background(if (active) colors.pine else colors.cream)
                    .clickable { onSelect(value) }.padding(horizontal = 13.dp, vertical = 9.dp)) {
                    Text(text, color = if (active) colors.onPine else colors.muted, fontFamily = Manrope,
                        fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

/** Selector compacto de orden: muestra el criterio actual y abre un menú. */
@Composable
internal fun <T> SortSelector(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.clip(RoundedCornerShape(10.dp)).background(colors.paper).clickable { open = true }
            .padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("↕ " + (options.firstOrNull { it.first == selected }?.second ?: ""), color = colors.pine,
                fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text, fontFamily = Manrope, fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(value); open = false })
            }
        }
    }
}

/** Interruptor de dos o más segmentos (p. ej. Lista / Casa). */
@Composable
internal fun <T> SegmentedToggle(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = LocalPalette.current
    Row(Modifier.fillMaxWidth().background(colors.paper, RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, text) ->
            val active = value == selected
            Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (active) colors.pine else Color.Transparent)
                .clickable { onSelect(value) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(text, color = if (active) colors.onPine else colors.muted, fontFamily = Manrope,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

/** Botón suave secundario a ancho libre. */
@Composable
internal fun SoftButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalPalette.current
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(colors.cream).clickable(onClick = onClick)
        .padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = colors.pine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/** Confirmación antes de borrar algo compartido: lo que se borra desaparece en los dos móviles. */
@Composable
internal fun ConfirmDeleteDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = Syne, fontWeight = FontWeight.Bold) },
        text = { Text(message, fontFamily = Manrope) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Eliminar", color = colors.danger, fontFamily = Manrope, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = colors.muted, fontFamily = Manrope, fontWeight = FontWeight.Bold) }
        },
    )
}
