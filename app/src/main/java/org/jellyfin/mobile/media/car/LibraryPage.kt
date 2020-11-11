package org.jellyfin.mobile.media.car

object LibraryPage {
    /**
     * List of music libraries that the user can access (referred to as "user views" in Jellyfin)
     */
    const val LIBRARIES = "libraries"

    /**
     * Special flag for use with [EXTRA_RECENT][androidx.media.MediaBrowserServiceCompat.BrowserRoot.EXTRA_RECENT],
     * loads and plays the last played queue - basically resuming playback from last session
     */
    const val RECENT = "recent"

    /**
     * A single music library
     */
    const val LIBRARY = "library"

    /**
     * A list of recently added tracks
     */
    const val RECENTS = "recents"

    /**
     * A list of albums
     */
    const val ALBUMS = "albums"

    /**
     * A list of artists
     */
    const val ARTISTS = "artists"

    /**
     * A list of albums by a specific artist
     */
    const val ARTIST_ALBUMS = "artist_albums"

    /**
     * A list of genres
     */
    const val GENRES = "genres"

    /**
     * A list of albums with a specific genre
     */
    const val GENRE_ALBUMS = "genre_albums"

    /**
     * A list of playlists
     */
    const val PLAYLISTS = "playlists"

    // Content types for individual library items - albums, artists, playlists and a shuffle meta item

    const val ALBUM = "album"
    const val PLAYLIST = "playlist"
}
