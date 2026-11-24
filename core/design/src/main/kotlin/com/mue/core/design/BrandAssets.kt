package com.mue.core.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Direct references to official brand drawables
 */
object BrandDrawables {
    val logoPlate: Int = R.drawable.logo_mue_air_plate
    val logoWhite: Int = R.drawable.logo_mue_air_white
    val watermark: Int = R.drawable.logo_mue_air_watermark
    val lt: Int = R.drawable.l_t
    val logoHeader: Int = R.drawable.ic_mue_logo
}

/**
 * Official MuE-Air Header Logo (from assets/branding/MuE-Logo.png).
 */
@Composable
fun MuEHeaderLogo(
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    contentDescription: String? = "MuE-Air"
) {
    Image(
        painter = painterResource(id = BrandDrawables.logoHeader),
        contentDescription = contentDescription,
        modifier = modifier.height(height),
        contentScale = ContentScale.Fit
    )
}

/**
 * Official MuE-Air Symbol Composable (Badge on white circular plate).
 */
@Composable
fun MuESymbol(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    contentDescription: String? = "MuE-Air"
) {
    Image(
        painter = painterResource(id = BrandDrawables.logoPlate),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit
    )
}

/**
 * Official MuE-Air White Monochrome Logo Composable.
 */
@Composable
fun MuELogoWhite(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    contentDescription: String? = "MuE-Air"
) {
    Image(
        painter = painterResource(id = BrandDrawables.logoWhite),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit
    )
}

/**
 * Official MuE-Air Wordmark ("MuE-Air" + Subtitle).
 */
@Composable
fun MuEWordmark(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MuE-Air",
            color = PureWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            fontFamily = FontFamily.SansSerif
        )
        Text(
            text = "MUSIC IN THE AIR",
            color = MutedGrey,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp,
            fontFamily = FontFamily.SansSerif
        )
    }
}

/**
 * Official MuE-Air Full Logo.
 * Automatically adapts between a horizontal lockup (when symbolSize <= 48.dp, ideal for top bar)
 * and a vertical centered hero lockup (when symbolSize > 48.dp, ideal for onboarding and about pages).
 */
@Composable
fun MuELogo(
    modifier: Modifier = Modifier,
    symbolSize: Dp = 96.dp,
    horizontal: Boolean = symbolSize <= 48.dp
) {
    if (horizontal) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MuESymbol(size = symbolSize)
            Column {
                Text(
                    text = "MuE-Air",
                    color = PureWhite,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Quick Picks",
                    color = MutedGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MuESymbol(size = symbolSize)
            Spacer(modifier = Modifier.height(16.dp))
            MuEWordmark()
        }
    }
}
