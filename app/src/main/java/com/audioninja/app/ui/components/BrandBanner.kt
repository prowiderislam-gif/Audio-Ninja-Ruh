package com.audioninja.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioninja.app.ui.theme.NeonRed
import com.audioninja.app.ui.theme.OnDarkPrimary

@Composable
fun BrandBanner(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Made By ", fontStyle = FontStyle.Italic, fontSize = 18.sp, color = OnDarkPrimary)
            Text("RUH,", fontStyle = FontStyle.Italic, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NeonRed)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("With ", fontStyle = FontStyle.Italic, fontSize = 18.sp, color = OnDarkPrimary)
            Text("LOVE", fontStyle = FontStyle.Italic, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NeonRed)
        }
        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            tint = NeonRed,
            modifier = Modifier.size(14.dp).padding(top = 2.dp)
        )
    }
}
