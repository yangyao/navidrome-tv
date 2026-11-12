package com.navidrome.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/** Only attached screens register requesters; stale events cannot steal focus. */
object FocusBus {
    var sidebar: FocusRequester? = null
    var content: FocusRequester? = null
    var miniBar: FocusRequester? = null
    private fun request(requester: FocusRequester?) =
        requester != null && runCatching { requester.requestFocus(); true }.getOrDefault(false)
    fun requestSidebar() = request(sidebar)
    fun requestContent() = request(content)
    fun requestMiniBar() = request(miniBar)
}

@Composable
fun ContentFocus(requester: FocusRequester, ready: Boolean = true) {
    DisposableEffect(requester, ready) {
        if (ready) FocusBus.content = requester
        onDispose { if (FocusBus.content === requester) FocusBus.content = null }
    }
    LaunchedEffect(requester, ready) {
        if (ready) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
}
