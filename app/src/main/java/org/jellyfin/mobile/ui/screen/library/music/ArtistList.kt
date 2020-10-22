package org.jellyfin.mobile.ui.screen.library.music

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.ui.screen.library.BaseMediaItem
import org.jellyfin.mobile.ui.utils.GridListFor
import timber.log.Timber

@Composable
fun ArtistList(viewModel: MusicViewModel) {
    GridListFor(
        items = viewModel.artists,
        numberOfColumns = 3,
        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
    ) { artist ->
        BaseMediaItem(info = artist, modifier = Modifier.fillItemMaxWidth(), onClick = {
            Timber.d("Clicked ${artist.name}")
        })
    }
}
