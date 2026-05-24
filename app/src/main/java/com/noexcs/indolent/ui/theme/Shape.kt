package com.noexcs.indolent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// M3 Expressive shape tokens — semantic hierarchy for shape contrast
//
//   extraSmall (4dp)  – decorative dots, color swatches
//   small      (8dp)  – chips, badges, mini previews, compact history items
//   medium     (16dp) – content cards (TaskCard, NoteCard, GroupCard, TriggerCard)
//   large      (20dp) – navigation cards, section containers, assistant bubbles
//   extraLarge (28dp) – hero cards, FABs, bottom sheet top corners
//
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Pill shape for text fields, search bars, full-rounded inputs
val PillShape = RoundedCornerShape(50)
