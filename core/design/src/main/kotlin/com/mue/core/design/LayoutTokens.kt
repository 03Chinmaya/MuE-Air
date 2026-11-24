package com.mue.core.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object LayoutTokens {
    // MiniPlayer (~72dp) + FloatingBottomBar (~60dp) + nav insets (~24dp) + breathing (16dp)
    val ListBottomPaddingWithMiniPlayer: Dp = 172.dp

    // MiniPlayer only (~72dp) + nav insets (~24dp) + breathing (16dp) for subscreens without BottomNav
    val ListBottomPaddingMiniPlayerOnly: Dp = 112.dp
}
