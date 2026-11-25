package com.weenas.castbay.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.R

/** The CastBay mark beside the app's name (CastBay, or 映湾投屏 in Chinese), as on the home and About screens. */
@Composable
fun BrandTitle(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_castbay_mark),
            contentDescription = null,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 48.sp,
            // Room for a second line on a narrow screen ("CastBay Dev"); the theme's is 24 sp.
            lineHeight = 54.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/** The slogan (一投即达，自在映湾), in the app's lighter purple, under [BrandTitle]. */
@Composable
fun BrandSlogan(
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 24.sp,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null
) {
    Text(
        text = stringResource(R.string.app_slogan),
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        color = SLOGAN_COLOR,
        textAlign = textAlign,
        modifier = modifier
    )
}

private val SLOGAN_COLOR = Color(0xFFA48BF5)
