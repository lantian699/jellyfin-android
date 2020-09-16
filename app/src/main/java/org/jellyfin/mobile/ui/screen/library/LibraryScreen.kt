package org.jellyfin.mobile.ui.screen.library

import androidx.compose.runtime.Composable
import org.jellyfin.mobile.model.dto.UserViewInfo
import org.jellyfin.mobile.ui.ScreenScaffold
import org.jellyfin.mobile.ui.screen.AbstractScreen

class LibraryScreen(private val viewInfo: UserViewInfo) : AbstractScreen() {

    @Composable
    override fun Content() {
        ScreenScaffold(
            title = viewInfo.name,
            canGoBack = true,
        ) {

        }
    }
}
