package org.jellyfin.mobile.ui

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Providers
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticAmbientOf
import com.github.zsoltk.compose.router.BackStack
import com.github.zsoltk.compose.router.Router
import org.jellyfin.mobile.controller.ServerController
import org.jellyfin.mobile.model.dto.UserViewInfo
import org.jellyfin.mobile.model.state.LoginState
import org.jellyfin.mobile.ui.screen.SetupScreen
import org.jellyfin.mobile.ui.screen.home.HomeScreen
import org.jellyfin.mobile.ui.screen.library.LibraryScreen

@Composable
fun AppContent() {
    val serverController: ServerController by inject()
    Crossfade(current = serverController.loginState) { loginState ->
        when (loginState) {
            LoginState.PENDING -> Unit // do nothing
            LoginState.NOT_LOGGED_IN -> injectContent<SetupScreen>()
            LoginState.LOGGED_IN -> AppRouter()
        }
    }
}

val BackStackAmbient = staticAmbientOf<BackStack<Routing>>()

@Composable
fun AppRouter() {
    Router<Routing>("App", Routing.Home) { backStack ->
        Providers(values = arrayOf(BackStackAmbient provides backStack)) {
            Crossfade(current = backStack.last()) { route ->
                when (route) {
                    is Routing.Home -> injectContent<HomeScreen>()
                    is Routing.Library -> remember(route.info) { LibraryScreen(route.info) }.Content()
                }
            }
        }
    }
}

sealed class Routing {
    object Home : Routing()
    class Library(val info: UserViewInfo) : Routing()
}
