package org.jellyfin.mobile.model.dto

import androidx.compose.runtime.Immutable

@Immutable
data class UserInfo(
    val id: String,
    val name: String,
    val primaryImageTag: String?,
)
