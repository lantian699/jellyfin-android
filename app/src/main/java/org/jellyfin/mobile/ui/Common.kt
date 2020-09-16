package org.jellyfin.mobile.ui

import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

val DefaultCornerRounding = RoundedCornerShape(8.dp)

@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    title: String,
    titleFont: FontFamily? = null,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontFamily = titleFont,
                    )
                },
                actions = actions,
                backgroundColor = MaterialTheme.colors.primary,
            )
        },
        bodyContent = content,
    )
}

@Composable
inline fun CenterRow(
    children: @Composable RowScope.() -> Unit
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
    children = children,
)

@Composable
fun ChipletButton(
    text: String,
    onClick: () -> Unit
) = OutlinedButton(
    onClick = onClick,
    modifier = Modifier.padding(8.dp),
    shape = CircleShape,
) {
    Text(text = text, color = MaterialTheme.colors.onSurface)
}
