package com.audioninja.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.audioninja.app.ui.theme.NeonRed
import com.audioninja.app.ui.theme.NinjaSurfaceElevated

@Composable
fun AppHeaderBar(sourceLabel: String = "INTERNAL AUDIO") {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(40.dp)
                .background(NinjaSurfaceElevated, RoundedCornerShape(10.dp))
                .border(1.dp, NeonRed, RoundedCornerShape(10.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text("AUDIO", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("NINJA", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NeonRed)
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .background(NinjaSurfaceElevated, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(8.dp).background(NeonRed, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(sourceLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}
