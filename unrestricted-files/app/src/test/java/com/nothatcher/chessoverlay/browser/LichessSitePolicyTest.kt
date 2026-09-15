package com.nothatcher.chessoverlay.browser

import com.nothatcher.chessoverlay.session.AnalysisContext
import com.nothatcher.chessoverlay.session.SessionMode
import org.junit.Test

class LichessSitePolicyTest {
    @Test
    fun analysisPageIsAllowed() {
        val d = LichessSitePolicy.decide("https://lichess.org/analysis", false, false)
        check(d.kind == LichessPageKind.ANALYSIS)
        check(d.mode == SessionMode.PRACTICE)
        check(d.context == AnalysisContext.ANALYSIS_BOARD)
        check(d.hintsAllowed)
    }

    @Test
    fun puzzlePageIsAllowed() {
        val d = LichessSitePolicy.decide("https://lichess.org/training/abc", false, false)
        check(d.kind == LichessPageKind.PUZZLE)
        check(d.context == AnalysisContext.PUZZLE)
        check(d.hintsAllowed)
    }

    @Test
    fun stockfishBotEvidenceAllowsGenericGame() {
        val d = LichessSitePolicy.decide("https://lichess.org/AbCd1234", true, false)
        check(d.kind == LichessPageKind.BOT_GAME)
        check(d.mode == SessionMode.PRACTICE)
        check(d.context == AnalysisContext.BOT)
        check(d.hintsAllowed)
    }

    @Test
    fun finishedGameIsReview() {
        val d = LichessSitePolicy.decide("https://lichess.org/AbCd1234", false, true)
        check(d.kind == LichessPageKind.FINISHED_GAME)
        check(d.mode == SessionMode.POST_GAME_REVIEW)
        check(d.context == AnalysisContext.POST_GAME)
        check(d.hintsAllowed)
    }

    @Test
    fun genericActiveGameKeepsDetectionActiveButHintsOff() {
        val d = LichessSitePolicy.decide("https://lichess.org/AbCd1234", false, false)
        check(d.kind == LichessPageKind.LIVE_GAME)
        check(d.mode == SessionMode.PRACTICE)
        check(d.context == AnalysisContext.LIVE_HUMAN)
        check(!d.hintsAllowed)
    }

    @Test
    fun unknownLichessPageKeepsDetectionActiveButHintsOff() {
        val d = LichessSitePolicy.decide("https://lichess.org/@/somebody", false, false)
        check(d.kind == LichessPageKind.UNKNOWN)
        check(d.mode == SessionMode.PRACTICE)
        check(d.context == AnalysisContext.LIVE_HUMAN)
        check(!d.hintsAllowed)
    }

    @Test
    fun autoFallbackOnlyRunsOnBoardRoutes() {
        check(LichessSitePolicy.shouldAutoFallback("https://lichess.org/analysis"))
        check(LichessSitePolicy.shouldAutoFallback("https://lichess.org/training/abc"))
        check(LichessSitePolicy.shouldAutoFallback("https://lichess.org/AbCd1234"))
        check(!LichessSitePolicy.shouldAutoFallback("https://lichess.org/@/somebody"))
        check(!LichessSitePolicy.shouldAutoFallback("https://example.com/analysis"))
    }

    @Test
    fun nonLichessAndHttpAreRejected() {
        val external = LichessSitePolicy.decide("https://example.com/analysis", true, true)
        check(external.kind == LichessPageKind.UNSUPPORTED)
        check(!external.hintsAllowed)
        val insecure = LichessSitePolicy.decide("http://lichess.org/analysis", false, false)
        check(insecure.kind == LichessPageKind.UNSUPPORTED)
        check(!insecure.hintsAllowed)
    }
}
