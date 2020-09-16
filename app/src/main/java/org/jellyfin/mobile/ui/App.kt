package org.jellyfin.mobile.ui

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import com.github.zsoltk.compose.router.Router
import org.jellyfin.mobile.controller.ServerController
import org.jellyfin.mobile.model.state.LoginState
import org.jellyfin.mobile.ui.screen.SetupScreen
import org.jellyfin.mobile.ui.screen.home.HomeScreen

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

@Composable
fun AppRouter() {
    Router<Routing>("App", Routing.Home) { backStack ->
        when (val routing = backStack.last()) {
            is Routing.Home -> injectContent<HomeScreen>()
        }
    }
}

sealed class Routing {
    object Home : Routing()
}
