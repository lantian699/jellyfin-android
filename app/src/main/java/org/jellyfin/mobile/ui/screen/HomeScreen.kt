package org.jellyfin.mobile.ui.screen

import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.mobile.ui.inject

class HomeScreen : AbstractScreen() {
    @Composable
    override fun Content() {
        val apiClient: ApiClient by inject()
        Surface {
            Text(
                text = "Welcome, ${apiClient.currentUserId}",
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h4,
            )
        }
    }
}
