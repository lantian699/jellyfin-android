// Contains code adapted from https://github.com/android/uamp/blob/main/common/src/main/java/com/example/android/uamp/media/MediaService.kt

package org.jellyfin.mobile.media

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouterParams
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.*
import org.jellyfin.apiclient.interaction.AndroidDevice
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.apiclient.model.dto.BaseItemDto
import org.jellyfin.apiclient.model.dto.BaseItemType
import org.jellyfin.apiclient.model.dto.ImageOptions
import org.jellyfin.apiclient.model.entities.CollectionType
import org.jellyfin.apiclient.model.entities.ImageType
import org.jellyfin.apiclient.model.entities.SortOrder
import org.jellyfin.apiclient.model.playlists.PlaylistItemQuery
import org.jellyfin.apiclient.model.querying.*
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.R
import org.jellyfin.mobile.utils.*
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.net.URLEncoder
import java.util.*
import com.google.android.exoplayer2.MediaItem as ExoPlayerMediaItem

class MediaService : MediaBrowserServiceCompat() {

    private val appPreferences: AppPreferences by inject()
    private val apiClient: ApiClient by inject()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var isForegroundService = false

    // The current player will either be an ExoPlayer (for local playback) or a CastPlayer (for
    // remote playback through a Cast device).
    private lateinit var currentPlayer: Player

    private lateinit var notificationManager: MediaNotificationManager
    private lateinit var mediaController: MediaControllerCompat
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector
    private lateinit var mediaRouteSelector: MediaRouteSelector
    private lateinit var mediaRouter: MediaRouter
    private val mediaRouterCallback = MediaRouterCallback()

    private val device: AndroidDevice by lazy { AndroidDevice.fromContext(this) }

    private var currentPlaylistItems: MutableList<MediaDescriptionCompat> = mutableListOf()

    private val playerAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private val playerListener = PlayerEventListener()

    private val exoPlayer: SimpleExoPlayer by lazy {
        SimpleExoPlayer.Builder(this).build().apply {
            setAudioAttributes(playerAudioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            addListener(playerListener)
        }
    }

    private val castPlayer: CastPlayer by lazy {
        CastPlayer(CastContext.getSharedInstance(this)).apply {
            setSessionAvailabilityListener(CastSessionAvailabilityListener())
            addListener(playerListener)
        }
    }

    /**
     * List of different views when browsing media
     * Libraries is the initial view
     */
    private enum class MediaItemType {
        Libraries,
        Library,
        LibraryLatest,
        LibraryAlbums,
        LibraryArtists,
        LibrarySongs,
        LibraryGenres,
        LibraryPlaylists,
        Album,
        Artist,
        Shuffle,
        Playlist,
    }

    override fun onCreate() {
        super.onCreate()

        // Build a PendingIntent that can be used to launch the UI.
        val sessionActivityPendingIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
            PendingIntent.getActivity(this, 0, sessionIntent, 0)
        }

        // Create a new MediaSession.
        mediaSession = MediaSessionCompat(this, "MediaService").apply {
            setSessionActivity(sessionActivityPendingIntent)
            isActive = true
        }

        sessionToken = mediaSession.sessionToken

        notificationManager = MediaNotificationManager(
            this,
            mediaSession.sessionToken,
            PlayerNotificationListener()
        )

        mediaController = MediaControllerCompat(this, mediaSession)

        mediaSessionConnector = MediaSessionConnector(mediaSession).apply {
            setPlayer(exoPlayer)
            setPlaybackPreparer(MediaPlaybackPreparer(exoPlayer, appPreferences))
            setQueueNavigator(MediaQueueNavigator(mediaSession))
        }

        mediaRouter = MediaRouter.getInstance(this)
        mediaRouter.setMediaSessionCompat(mediaSession)
        mediaRouteSelector = MediaRouteSelector.Builder().apply {
            addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
        }.build()
        mediaRouter.routerParams = MediaRouterParams.Builder().build()
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)

        switchToPlayer(
            previousPlayer = null,
            newPlayer = if (castPlayer.isCastSessionAvailable) castPlayer else exoPlayer
        )
        notificationManager.showNotificationForPlayer(currentPlayer)

    }

    override fun onDestroy() {
        mediaSession.run {
            isActive = false
            release()
        }

        // Cancel coroutines when the service is going away.
        serviceJob.cancel()

        // Free ExoPlayer resources.
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()

        // Stop listening for route changes.
        mediaRouter.removeCallback(mediaRouterCallback)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        val rootExtras = Bundle().apply {
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
        }
        return BrowserRoot(MediaItemType.Libraries.toString(), rootExtras)
    }

    /**
     * The parent id will be in various formats such as
     * Libraries
     * Library|{id}
     * LibraryAlbums|{id}
     * Album|{id}
     */
    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        if (appPreferences.instanceUrl == null || appPreferences.instanceAccessToken == null || appPreferences.instanceUserId == null) {
            // the required properties for calling the api client are not available
            // this should only occur if the user does not have a server connection in the app
            result.sendError(null)
            return
        }

        // ensure the api client is up to date
        apiClient.ChangeServerLocation(appPreferences.instanceUrl)
        apiClient.SetAuthenticationInfo(
            appPreferences.instanceAccessToken,
            appPreferences.instanceUserId
        )

        result.detach()

        serviceScope.launch {
            withContext(Dispatchers.IO) {
                loadView(parentId, result)
            }
        }
    }

    @WorkerThread
    private suspend fun loadView(parentId: String, result: Result<MutableList<MediaItem>>) {
        when (parentId) {
            /**
             * View that shows all the music libraries
             */
            MediaItemType.Libraries.toString() -> {
                val response = apiClient.getUserViews(appPreferences.instanceUserId!!)
                if (response != null) {
                    val views = response.items
                        .filter { item -> item.collectionType == CollectionType.Music }
                        .map { item ->
                            val itemImageUrl = apiClient.GetImageUrl(item, ImageOptions().apply {
                                imageType = ImageType.Primary
                                maxWidth = 1080
                                quality = 90
                            })

                            val description: MediaDescriptionCompat =
                                MediaDescriptionCompat.Builder().apply {
                                    setMediaId(MediaItemType.Library.toString() + "|" + item.id)
                                    setTitle(item.name)
                                    setIconUri(Uri.parse(itemImageUrl))
                                }.build()

                            MediaItem(description, MediaItem.FLAG_BROWSABLE)
                        }

                    result.sendResult(views.toMutableList())

                } else {
                    result.sendError(null)
                }
            }

            /**
             * Views that show albums, artists, songs, genres, etc for a specific library
             */
            else -> {
                val mediaItemTypeSplit = parentId.split("|")
                val primaryType = mediaItemTypeSplit[0]
                val primaryItemId = mediaItemTypeSplit[1]
                val secondaryItemId =
                    if (mediaItemTypeSplit.count() > 2) mediaItemTypeSplit[2] else null

                if (primaryType == MediaItemType.Library.toString()) {
                    /**
                     * The default view for a library that lists various ways to browse
                     */
                    val libraryViews = arrayOf(
                        Pair(MediaItemType.LibraryLatest, R.string.mediaservice_library_latest),
                        Pair(MediaItemType.LibraryAlbums, R.string.mediaservice_library_albums),
                        Pair(
                            MediaItemType.LibraryArtists,
                            R.string.mediaservice_library_artists
                        ),
                        Pair(MediaItemType.LibrarySongs, R.string.mediaservice_library_songs),
                        Pair(MediaItemType.LibraryGenres, R.string.mediaservice_library_genres),
                        Pair(
                            MediaItemType.LibraryPlaylists,
                            R.string.mediaservice_library_playlists
                        ),
                    )

                    val views = libraryViews.map { item ->
                        val description: MediaDescriptionCompat =
                            MediaDescriptionCompat.Builder().apply {
                                setMediaId(item.first.toString() + "|" + primaryItemId)
                                setTitle(getString(item.second))
                            }.build()

                        MediaItem(description, MediaItem.FLAG_BROWSABLE)
                    }

                    result.sendResult(views.toMutableList())
                    return
                }

                /**
                 * Processes items from api responses
                 * Updates the current play list items and sends results back to android auto
                 */
                fun processItems(items: Array<BaseItemDto>) {
                    val mediaItems = items
                        .map { item -> createMediaItem(primaryType, primaryItemId, item) }
                        .toMutableList()

                    currentPlaylistItems = mediaItems
                        .filter { item -> item.isPlayable }
                        .map { item -> item.description }
                        .toMutableList()

                    if (currentPlaylistItems.count() > 1) {
                        val description: MediaDescriptionCompat =
                            MediaDescriptionCompat.Builder().apply {
                                setMediaId(primaryType + "|" + primaryItemId + "|" + MediaItemType.Shuffle)
                                setTitle(getString(R.string.mediaservice_shuffle))
                            }.build()

                        mediaItems.add(0, MediaItem(description, MediaItem.FLAG_PLAYABLE))
                    }

                    result.sendResult(mediaItems)
                }

                fun processItemsResponse(response: ItemsResult?) {
                    if (response != null) {
                        processItems(response.items)
                    } else {
                        result.sendError(null)
                    }
                }

                when (primaryType) {
                    /**
                     * View for a specific album
                     */
                    MediaItemType.Album.toString() -> {
                        val query = ItemQuery()
                        query.parentId = primaryItemId
                        query.userId = appPreferences.instanceUserId
                        query.sortBy = arrayOf(ItemSortBy.SortName)

                        processItemsResponse(apiClient.getItems(query))
                    }

                    /**
                     * View for a specific artist
                     */
                    MediaItemType.Artist.toString() -> {
                        val query = ItemQuery()
                        query.artistIds = arrayOf(primaryItemId)
                        query.userId = appPreferences.instanceUserId
                        query.sortBy = arrayOf(ItemSortBy.SortName)
                        query.sortOrder = SortOrder.Ascending
                        query.recursive = true
                        query.imageTypeLimit = 1
                        query.enableImageTypes = arrayOf(ImageType.Primary)
                        query.limit = 100
                        query.includeItemTypes = arrayOf(BaseItemType.MusicAlbum.name)

                        processItemsResponse(apiClient.getItems(query))
                    }

                    /**
                     * View for a specific playlist
                     */
                    MediaItemType.Playlist.toString() -> {
                        val query = PlaylistItemQuery()
                        query.id = primaryItemId
                        query.userId = appPreferences.instanceUserId

                        processItemsResponse(apiClient.getPlaylistItems(query))
                    }

                    /**
                     * View for albums / songs in a library
                     */
                    MediaItemType.LibraryAlbums.toString(),
                    MediaItemType.LibrarySongs.toString(),
                    MediaItemType.LibraryPlaylists.toString() -> {
                        val query = ItemQuery()
                        query.parentId = primaryItemId
                        query.userId = appPreferences.instanceUserId
                        query.sortBy = arrayOf(ItemSortBy.SortName)
                        query.sortOrder = SortOrder.Ascending
                        query.recursive = true
                        query.imageTypeLimit = 1
                        query.enableImageTypes = arrayOf(ImageType.Primary)
                        query.limit = 100

                        when (primaryType) {
                            MediaItemType.LibraryAlbums.toString() -> {
                                if (secondaryItemId != null) {
                                    query.parentId = null
                                    query.artistIds = arrayOf(secondaryItemId)
                                }

                                query.includeItemTypes = arrayOf(BaseItemType.MusicAlbum.name)
                            }
                            MediaItemType.LibrarySongs.toString() -> {
                                query.includeItemTypes = arrayOf(BaseItemType.Audio.name)
                            }
                            MediaItemType.LibraryPlaylists.toString() -> {
                                query.includeItemTypes = arrayOf(BaseItemType.Playlist.name)
                            }
                        }

                        processItemsResponse(apiClient.getItems(query))
                    }

                    /**
                     * View for "Latest Music" in a library
                     */
                    MediaItemType.LibraryLatest.toString() -> {
                        val query = LatestItemsQuery()
                        query.parentId = primaryItemId
                        query.userId = appPreferences.instanceUserId
                        query.includeItemTypes = arrayOf(BaseItemType.Audio.name)
                        query.limit = 100

                        val response = apiClient.getLatestItems(query)
                        if (response != null) {
                            processItems(response)
                        } else {
                            result.sendError(null)
                        }
                    }

                    /**
                     * View for artists in a library
                     */
                    MediaItemType.LibraryArtists.toString() -> {
                        val query = ArtistsQuery()
                        query.parentId = primaryItemId
                        query.userId = appPreferences.instanceUserId
                        query.sortBy = arrayOf(ItemSortBy.SortName)
                        query.sortOrder = SortOrder.Ascending
                        query.recursive = true
                        query.imageTypeLimit = 1
                        query.enableImageTypes = arrayOf(ImageType.Primary)

                        processItemsResponse(apiClient.getArtists(query))
                    }

                    /**
                     * View for genres
                     */
                    MediaItemType.LibraryGenres.toString() -> {
                        if (secondaryItemId != null) {
                            /**
                             * View for a specific genre in a library
                             */
                            val query = ItemQuery()
                            query.parentId = primaryItemId
                            query.userId = appPreferences.instanceUserId
                            query.sortBy = arrayOf(ItemSortBy.IsFolder, ItemSortBy.SortName)
                            query.sortOrder = SortOrder.Ascending
                            query.recursive = true
                            query.imageTypeLimit = 1
                            query.enableImageTypes = arrayOf(ImageType.Primary)
                            query.includeItemTypes = arrayOf(BaseItemType.MusicAlbum.name)
                            query.genreIds = arrayOf(secondaryItemId)

                            processItemsResponse(apiClient.getItems(query))

                        } else {
                            /**
                             * View for genres in a library
                             */
                            val query = ItemsByNameQuery()
                            query.parentId = primaryItemId
                            query.userId = appPreferences.instanceUserId
                            query.sortBy = arrayOf(ItemSortBy.SortName)
                            query.sortOrder = SortOrder.Ascending
                            query.recursive = true

                            processItemsResponse(apiClient.getGenres(query))
                        }
                    }

                    /**
                     * Unhandled view
                     */
                    else -> {
                        result.sendError(null)
                    }
                }
            }
        }
    }

    private fun createMediaItem(
        primaryType: String,
        primaryItemId: String,
        item: BaseItemDto
    ): MediaItem {
        val extras = Bundle()
        extras.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, item.album)
        extras.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, item.albumArtist)

        val isSong = item.baseItemType == BaseItemType.Audio

        var mediaSubtitle: String? = null
        var mediaImageUri: Uri? = null

        val imageOptions = ImageOptions().apply {
            imageType = ImageType.Primary
            maxWidth = 1080
            quality = 90
        }
        val primaryImageUrl = when {
            item.hasPrimaryImage -> apiClient.GetImageUrl(item, imageOptions)
            item.albumId != null -> apiClient.GetImageUrl(item.albumId, imageOptions)
            else -> null
        }

        if (isSong) {
            mediaSubtitle = item.albumArtist

            // show album art when playing the song
            extras.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, primaryImageUrl)
            extras.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.name)
            extras.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.albumArtist)

            if (item.indexNumber != null) {
                extras.putInt(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, item.indexNumber)
            }
        } else {
            mediaImageUri = primaryImageUrl?.let(Uri::parse)
        }

        // songs are playable. everything else is browsable (clicks into another view)
        val flag =
            if (isSong) MediaItem.FLAG_PLAYABLE else MediaItem.FLAG_BROWSABLE

        // the media id controls the view if it's a browsable item
        // otherwise it will control the item that is played when clicked
        val mediaId: String = when {
            item.baseItemType == BaseItemType.MusicAlbum -> {
                MediaItemType.Album.name + "|" + item.id
            }
            item.baseItemType == BaseItemType.MusicArtist -> {
                MediaItemType.Artist.name + "|" + item.id
            }
            item.baseItemType == BaseItemType.Playlist -> {
                MediaItemType.Playlist.name + "|" + item.id
            }
            flag == MediaItem.FLAG_PLAYABLE -> item.id
            else -> primaryType + "|" + primaryItemId + "|" + item.id
        }

        val description: MediaDescriptionCompat =
            MediaDescriptionCompat.Builder().apply {
                setMediaId(mediaId)
                setTitle(item.name)
                setSubtitle(mediaSubtitle)
                setExtras(extras)
                setIconUri(mediaImageUri)
            }.build()

        return MediaItem(description, flag)
    }

    private fun switchToPlayer(previousPlayer: Player?, newPlayer: Player) {
        if (previousPlayer == newPlayer) {
            return
        }
        currentPlayer = newPlayer
        if (previousPlayer != null) {
            val playbackState = previousPlayer.playbackState
            if (currentPlaylistItems.isEmpty()) {
                // We are joining a playback session.
                // Loading the session from the new player is not supported, so we stop playback.
                currentPlayer.stop(true)
            } else if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
                /*preparePlaylist(
                    metadataList = currentPlaylistItems,
                    itemToPlay = currentPlaylistItems[previousPlayer.currentWindowIndex],
                    playWhenReady = previousPlayer.playWhenReady,
                    playbackStartPositionMs = previousPlayer.currentPosition
                )*/
            }
        }
        mediaSessionConnector.setPlayer(newPlayer)
        previousPlayer?.stop(true)
    }

    private inner class CastSessionAvailabilityListener : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            switchToPlayer(currentPlayer, castPlayer)
        }

        override fun onCastSessionUnavailable() {
            switchToPlayer(currentPlayer, exoPlayer)
        }
    }

    private inner class MediaQueueNavigator(mediaSession: MediaSessionCompat) :
        TimelineQueueNavigator(mediaSession) {
        override fun getMediaDescription(
            player: Player,
            windowIndex: Int
        ): MediaDescriptionCompat {
            return currentPlaylistItems[windowIndex]
        }
    }

    private inner class MediaPlaybackPreparer(
        private val exoPlayer: ExoPlayer,
        private val appPreferences: AppPreferences
    ) : MediaSessionConnector.PlaybackPreparer {

        override fun getSupportedPrepareActions(): Long =
            PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID

        override fun onPrepare(playWhenReady: Boolean) {}

        override fun onPrepareFromMediaId(mediaId: String, playWhenReady: Boolean, extras: Bundle?) {
            val shouldShuffle = mediaId.endsWith(MediaItemType.Shuffle.toString())

            if (shouldShuffle) {
                currentPlaylistItems.shuffle()
            }

            val mediaItems = currentPlaylistItems.map { item ->
                createMediaItem(item.mediaId!!)
            }

            var mediaItemIndex = 0

            if (!shouldShuffle) {
                mediaItemIndex = currentPlaylistItems.indexOfFirst { item ->
                    item.mediaId == mediaId
                }
            }

            exoPlayer.setMediaItems(mediaItems)
            exoPlayer.prepare()
            if (mediaItemIndex in 0 until currentPlaylistItems.size)
                exoPlayer.seekTo(mediaItemIndex, 0)
        }

        override fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle?) {}

        override fun onPrepareFromUri(uri: Uri, playWhenReady: Boolean, extras: Bundle?) = Unit

        override fun onCommand(
            player: Player,
            controlDispatcher: ControlDispatcher,
            command: String,
            extras: Bundle?,
            cb: ResultReceiver?
        ): Boolean = false

        fun createMediaItem(mediaId: String): ExoPlayerMediaItem {
            val url = "${appPreferences.instanceUrl}/Audio/${mediaId}/universal?" +
                "UserId=${appPreferences.instanceUserId}&" +
                "DeviceId=${URLEncoder.encode(device.deviceId, Charsets.UTF_8.name())}&" +
                "MaxStreamingBitrate=140000000&" +
                "Container=opus,mp3|mp3,aac,m4a,m4b|aac,flac,webma,webm,wav,ogg&" +
                "TranscodingContainer=ts&" +
                "TranscodingProtocol=hls&" +
                "AudioCodec=aac&" +
                "api_key=${appPreferences.instanceAccessToken}&" +
                "PlaySessionId=${UUID.randomUUID()}&" +
                "EnableRemoteMedia=true"

            return ExoPlayerMediaItem.fromUri(url)
        }
    }

    /**
     * Listen for notification events.
     */
    private inner class PlayerNotificationListener : PlayerNotificationManager.NotificationListener {
        override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
            if (ongoing && !isForegroundService) {
                val serviceIntent = Intent(applicationContext, this@MediaService.javaClass)
                ContextCompat.startForegroundService(applicationContext, serviceIntent)

                startForeground(notificationId, notification)
                isForegroundService = true
            }
        }

        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
            stopForeground(true)
            isForegroundService = false
            stopSelf()
        }
    }

    /**
     * Listen for events from ExoPlayer.
     */
    private inner class PlayerEventListener : Player.EventListener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING,
                Player.STATE_READY -> {
                    notificationManager.showNotificationForPlayer(currentPlayer)
                    if (playbackState == Player.STATE_READY) {
                        // When playing/paused save the current media item in persistent
                        // storage so that playback can be resumed between device reboots.
                        // Search for "media resumption" for more information.
                        // TODO saveRecentSongToStorage()

                        if (!playWhenReady) {
                            // If playback is paused we remove the foreground state which allows the
                            // notification to be dismissed. An alternative would be to provide a
                            // "close" button in the notification which stops playback and clears
                            // the notification.
                            stopForeground(false)
                        }
                    }
                }
                else -> notificationManager.hideNotification()
            }
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            var message = R.string.media_service_generic_error
            when (error.type) {
                ExoPlaybackException.TYPE_SOURCE -> {
                    message = R.string.media_service_item_not_found
                    Timber.e("TYPE_SOURCE: %s", error.sourceException.message)
                }
                ExoPlaybackException.TYPE_RENDERER -> Timber.e("TYPE_RENDERER: %s", error.rendererException.message)
                ExoPlaybackException.TYPE_UNEXPECTED -> Timber.e("TYPE_UNEXPECTED: %s", error.unexpectedException.message)
                ExoPlaybackException.TYPE_REMOTE -> Timber.e("TYPE_REMOTE: %s", error.message)
                ExoPlaybackException.TYPE_OUT_OF_MEMORY -> Timber.e("TYPE_OUT_OF_MEMORY: %s", error.outOfMemoryError.message)
                ExoPlaybackException.TYPE_TIMEOUT -> Timber.e("TYPE_TIMEOUT: %s", error.timeoutException.message)
            }
            applicationContext.toast(message, Toast.LENGTH_LONG)
        }
    }

    /**
     * Listen for MediaRoute changes
     */
    private inner class MediaRouterCallback : MediaRouter.Callback() {
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            if (reason == MediaRouter.UNSELECT_REASON_ROUTE_CHANGED) {
                Timber.d("Unselected because route changed, continue playback")
            } else if (reason == MediaRouter.UNSELECT_REASON_STOPPED) {
                Timber.d("Unselected because route was stopped, stop playback")
                currentPlayer.stop()
            }
        }
    }

    companion object {
        private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1
    }
}
