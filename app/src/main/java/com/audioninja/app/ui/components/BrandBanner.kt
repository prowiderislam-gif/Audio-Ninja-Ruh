package com.audioninja.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

@Composable
fun BrandBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bannerResId = remember {
        context.resources.getIdentifier("banner", "drawable", context.packageName)
    }
    if (bannerResId != 0) {
        Image(
            painter = painterResource(id = bannerResId),
            contentDescription = "Made By RUH, With Love",
            contentScale = ContentScale.FillWidth,
            modifier = modifier.fillMaxWidth()
        )
    }
}
