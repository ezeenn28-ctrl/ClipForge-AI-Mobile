package com.clipforge.mobile

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*

@Composable fun HighlightBatchControls(count:Int,selected:Set<Int>,reviewed:Boolean,enabled:Boolean,onSelect:(Set<Int>)->Unit,onReviewed:(Boolean)->Unit,onQueue:()->Unit){
    Text("${selected.size}/$count highlights selected")
    Row{TextButton(enabled=enabled,onClick={onSelect((0 until count).toSet())}){Text("Select all highlights")};TextButton(enabled=enabled,onClick={onSelect(emptySet())}){Text("Clear selection")}}
    Row{Checkbox(reviewed,{onReviewed(it)},enabled=enabled&&selected.isNotEmpty());Text("I reviewed the current edits of all selected highlights")}
    Button(enabled=enabled&&reviewed&&selected.isNotEmpty(),onClick=onQueue){Text("Approve ${selected.size} highlights and add to queue")}
    Text("Each highlight becomes a separate MP4 when you start the approved queue. Each clip keeps its own saved music, voice, captions, effects and SFX.",style=MaterialTheme.typography.bodySmall)
}
