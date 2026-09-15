package com.nothatcher.chessoverlay.browser

import org.junit.Test

class BrowserFallbackPolicyTest {
    @Test
    fun domBoardAlwaysWins() {
        check(BrowserFallbackPolicy.action(true, 10_000, true, false) == BrowserFallbackAction.USE_DOM)
    }

    @Test
    fun waitsBrieflyForDomBeforeFallingBack() {
        check(BrowserFallbackPolicy.action(false, 1_500, false, false) == BrowserFallbackAction.WAIT)
    }

    @Test
    fun usesAlreadyRunningScreenCaptureWithoutAnotherPrompt() {
        check(BrowserFallbackPolicy.action(false, 3_000, true, false) == BrowserFallbackAction.USE_SCREEN_CAPTURE)
    }

    @Test
    fun requestsScreenCaptureOnceWhenDomTimesOut() {
        check(BrowserFallbackPolicy.action(false, 3_000, false, false) == BrowserFallbackAction.REQUEST_SCREEN_CAPTURE)
        check(BrowserFallbackPolicy.action(false, 3_000, false, true) == BrowserFallbackAction.WAIT)
    }
}
