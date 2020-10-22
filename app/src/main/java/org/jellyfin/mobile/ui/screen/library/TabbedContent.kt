package org.jellyfin.mobile.ui.screen.library

import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.drawLayer
import androidx.compose.ui.platform.AnimationClockAmbient
import androidx.compose.ui.platform.DensityAmbient
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import org.jellyfin.mobile.ui.TopBarElevation
import org.jellyfin.mobile.ui.utils.Pager
import org.jellyfin.mobile.ui.utils.PagerState
import kotlin.math.max

@Composable
fun TabbedContent(
    tabTitles: List<String>,
    currentTabState: MutableState<Int>,
    pageContent: @Composable (Int) -> Unit
) {
    Column {
        val clock = AnimationClockAmbient.current
        val pagerState = remember(clock) { PagerState(clock, currentTabState.value, 0, max(0, tabTitles.size - 1)) }

        val elevationPx = with(DensityAmbient.current) { TopBarElevation.toPx() }
        ScrollableTabRow(
            modifier = Modifier.fillMaxWidth().drawLayer(shadowElevation = elevationPx).zIndex(TopBarElevation.value),
            selectedTabIndex = pagerState.currentPage,
            backgroundColor = MaterialTheme.colors.primary,
            divider = {},
        ) {
            tabTitles.fastForEachIndexed { index, title ->
                Tab(selected = index == pagerState.currentPage, onClick = {
                    currentTabState.value = index
                    pagerState.currentPage = index
                }, text = {
                    Text(title)
                })
            }
        }
        Pager(state = pagerState) {
            Column(modifier = Modifier.fillMaxSize()) {
                pageContent(page)
            }
        }
    }
}
