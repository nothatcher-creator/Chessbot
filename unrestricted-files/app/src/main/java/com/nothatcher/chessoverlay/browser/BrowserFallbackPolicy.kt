package com.nothatcher.chessoverlay.browser

enum class BrowserFallbackAction {
    USE_DOM,
    USE_SCREEN_CAPTURE,
    REQUEST_SCREEN_CAPTURE,
    WAIT,
}

object BrowserFallbackPolicy {
    const val DOM_GRACE_PERIOD_MS: Long = 2_500

    fun action(
        domBoardAvailable: Boolean,
        elapsedSincePageLoadMs: Long,
        screenCaptureRunning: Boolean,
        captureRequestAlreadyLaunched: Boolean,
    ): BrowserFallbackAction = when {
        domBoardAvailable -> BrowserFallbackAction.USE_DOM
        elapsedSincePageLoadMs < DOM_GRACE_PERIOD_MS -> BrowserFallbackAction.WAIT
        screenCaptureRunning -> BrowserFallbackAction.USE_SCREEN_CAPTURE
        captureRequestAlreadyLaunched -> BrowserFallbackAction.WAIT
        else -> BrowserFallbackAction.REQUEST_SCREEN_CAPTURE
    }
}
