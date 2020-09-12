import java.util.*

object Dependencies {
    object Versions {
        // Gradle plugins
        const val dependencyUpdates = "0.29.0"

        // KotlinX
        const val coroutinesCore = "1.3.5"
        const val coroutinesAndroid = "1.3.0"

        // Core
        const val koin = "2.1.6"
        const val appCompat = "1.1.0"
        const val androidxCore = "1.4.0-alpha01"
        const val activity = "1.2.0-alpha01"
        const val fragment = "1.2.5"
        const val exoPlayer = "2.12.0"

        // Lifecycle
        const val lifecycleExtensions = "2.2.0"

        // UI
        const val constraintLayout = "1.1.3"
        const val material = "1.2.1"
        const val webkitX = "1.2.0"
        const val modernAndroidPreferences = "1.1.0-alpha3"

        // Compose
        const val compose = "1.0.0-alpha06"
        const val composeRouter = "0.21.0"
        const val accompanistCoil = "0.3.2"

        // Room
        const val room = "2.2.5"

        // Network
        const val apiclient = "0.7.7"
        const val okHttp = "4.8.0"
        const val coil = "1.0.0-rc2"

        // Media
        const val media = "1.2.0"

        // Cast
        const val mediaRouter = "1.2.0"
        const val playServicesCast = "19.0.0"

        // Health
        const val timber = "4.7.1"
        const val leakCanary = "2.4"
        const val junit = "5.6.2"
        const val kotest = "4.2.2"
        const val mockk = "1.10.0"
        const val androidXRunner = "1.2.0"
        const val androidXEspresso = "3.2.0"

        fun isStable(version: String): Boolean {
            return listOf("alpha", "beta", "dev", "rc", "m").none {
                version.toLowerCase(Locale.ROOT).contains(it)
            }
        }
    }

    object Kotlin {
        val coroutinesCore = kotlinx("coroutines-core", Versions.coroutinesCore)
        val coroutinesAndroid = kotlinx("coroutines-android", Versions.coroutinesAndroid)
    }

    object Core {
        const val koin = "org.koin:koin-android:${Versions.koin}"
        val appCompat = androidx("appcompat", Versions.appCompat)
        val androidx = androidxKtx("core", Versions.androidxCore)
        val activity = androidxKtx("activity", Versions.activity)
        val fragment = androidxKtx("fragment", Versions.fragment)
        const val koinFragment = "org.koin:koin-androidx-fragment:${Versions.koin}"
        val exoPlayer = exoPlayer("core")
    }

    object Lifecycle {
        val viewModel = lifecycle("viewmodel-ktx")
        val liveData = lifecycle("livedata-ktx")
        val runtime = lifecycle("runtime-ktx")
        val common = lifecycle("common-java8")
        val process = lifecycle("process")
    }

    object UI {
        val constraintLayout = androidx("constraintlayout", Versions.constraintLayout)
        const val material = "com.google.android.material:material:${Versions.material}"
        val webkitX = androidx("webkit", Versions.webkitX)
        val exoPlayer = exoPlayer("ui")
        const val modernAndroidPreferences = "de.Maxr1998.android:modernpreferences:${Versions.modernAndroidPreferences}"
    }

    object Compose {
        val runtime = compose("runtime")
        val ui = compose("ui")
        val uiTooling = androidx("ui", "tooling", Versions.compose)
        val foundation = compose("foundation")
        val animation = compose("animation")
        val material = compose("material")
        val materialIcons = compose("material", "icons-core")
        val materialIconsExtended = compose("material", "icons-extended")
        val runtimeLiveData = compose("runtime", "livedata")
        const val composeRouter = "com.github.zsoltk:compose-router:${Versions.composeRouter}"
        const val accompanistCoil = "dev.chrisbanes.accompanist:accompanist-coil:${Versions.accompanistCoil}"
    }

    object Room {
        val runtime = room("runtime")
        val compiler = room("compiler")
    }

    object Network {
        const val apiclient = "org.jellyfin.apiclient:android:${Versions.apiclient}"
        const val okHttp = "com.squareup.okhttp3:okhttp:${Versions.okHttp}"
        const val coil = "io.coil-kt:coil:${Versions.coil}"
        val exoPlayerHLS = exoPlayer("hls")
    }

    object Media {
        val media = androidx("media", Versions.media)
        var exoPlayerMediaSession = "com.google.android.exoplayer:extension-mediasession:${Versions.exoPlayer}"
    }

    object Cast {
        val mediaRouter = androidx("mediarouter", Versions.mediaRouter)
        const val exoPlayerCastExtension = "com.google.android.exoplayer:extension-cast:${Versions.exoPlayer}"
        const val playServicesCast = "com.google.android.gms:play-services-cast:${Versions.playServicesCast}"
        const val playServicesCastFramework = "com.google.android.gms:play-services-cast-framework:${Versions.playServicesCast}"
    }

    /**
     * Includes logging, debugging, and testing
     */
    object Health {
        const val timber = "com.jakewharton.timber:timber:${Versions.timber}"
        const val leakCanary = "com.squareup.leakcanary:leakcanary-android:${Versions.leakCanary}"
        const val junit = "org.junit.jupiter:junit-jupiter-api:${Versions.junit}"
        const val junitEngine = "org.junit.jupiter:junit-jupiter-engine:${Versions.junit}"
        const val kotestAssertions = "io.kotest:kotest-assertions-core-jvm:${Versions.kotest}"
        const val kotestProperty = "io.kotest:kotest-property-jvm:${Versions.kotest}"
        const val kotestRunner = "io.kotest:kotest-runner-junit5-jvm:${Versions.kotest}"
        const val mockk = "io.mockk:mockk:${Versions.mockk}"
        const val androidXRunner = "androidx.test:runner:${Versions.androidXRunner}"
        const val androidXEspresso = "androidx.test.espresso:espresso-core:${Versions.androidXEspresso}"
    }

    // Helpers
    private fun androidx(module: String, version: String) = androidx(module, null, version)
    private fun androidx(module: String, variant: String?, version: String) = "androidx.$module:$module${variant?.let { "-$it" } ?: ""}:$version"
    private fun androidxKtx(module: String, version: String) = "androidx.$module:$module-ktx:$version"
    private fun kotlinx(module: String, version: String) = "org.jetbrains.kotlinx:kotlinx-$module:$version"
    private fun lifecycle(module: String) = "androidx.lifecycle:lifecycle-$module:${Versions.lifecycleExtensions}"
    private fun room(module: String) = "androidx.room:room-$module:${Versions.room}"
    private fun compose(module: String, variant: String? = null) = "androidx.compose.$module:$module${variant?.let { "-$it" } ?: ""}:${Versions.compose}"
    private fun exoPlayer(module: String) = "com.google.android.exoplayer:exoplayer-$module:${Versions.exoPlayer}"
}
