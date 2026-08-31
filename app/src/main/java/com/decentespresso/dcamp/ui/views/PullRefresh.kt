package com.decentespresso.dcamp.ui.views

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/** Wraps a scrollable in swipe-to-refresh (Material3 PullToRefreshBox). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DcampPull(onRefresh: suspend () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true; scope.launch { onRefresh(); refreshing = false } },
        modifier = Modifier.fillMaxSize(),
    ) { content() }
}
