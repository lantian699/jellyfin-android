@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.ui.utils

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.WithConstraints
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import dev.chrisbanes.accompanist.coil.CoilImage
import dev.chrisbanes.accompanist.imageloading.ImageLoadState
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.apiclient.model.dto.ImageOptions
import org.jellyfin.apiclient.model.entities.ImageType
import org.jellyfin.mobile.R
import org.jellyfin.mobile.model.dto.UserInfo
import org.jellyfin.mobile.ui.inject

@Stable
@Composable
fun ApiImage(
    id: String,
    modifier: Modifier = Modifier,
    imageType: ImageType = ImageType.Primary,
    imageTag: String? = null,
    fallback: @Composable ((ImageLoadState.Error) -> Unit)? = null
) {
    val apiClient: ApiClient by inject()
    WithConstraints(modifier = modifier) {
        val imageUrl = remember(id, constraints, imageType, imageTag) {
            apiClient.GetImageUrl(id, ImageOptions().apply {
                setImageType(imageType)
                maxWidth = constraints.maxWidth
                maxHeight = constraints.maxHeight
                quality = 90
                tag = imageTag
            })
        }
        CoilImage(
            data = imageUrl,
            modifier = Modifier.size(maxWidth, maxHeight),
            contentScale = ContentScale.Crop,
            error = fallback,
            loading = { LoadingSurface(Modifier.fillMaxSize()) },
        )
    }
}

@Stable
@Composable
fun ApiUserImage(
    id: String,
    modifier: Modifier = Modifier,
    imageTag: String? = null
) {
    val apiClient: ApiClient by inject()
    WithConstraints(modifier = modifier) {
        val imageUrl = remember(id, constraints, imageTag) {
            apiClient.GetUserImageUrl(id, ImageOptions().apply {
                imageType = ImageType.Primary
                maxWidth = constraints.maxWidth
                maxHeight = constraints.maxHeight
                quality = 90
                tag = imageTag
            })
        }
        CoilImage(
            data = imageUrl,
            modifier = Modifier.size(maxWidth, maxHeight),
            contentScale = ContentScale.Crop,
            error = { Image(asset = vectorResource(R.drawable.fallback_image_person)) },
            loading = { LoadingSurface(Modifier.fillMaxSize()) },
        )
    }
}

@Composable
inline fun ApiUserImage(
    userInfo: UserInfo,
    modifier: Modifier = Modifier,
) {
    ApiUserImage(
        id = userInfo.id,
        modifier = modifier,
        imageTag = userInfo.primaryImageTag,
    )
}
