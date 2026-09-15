package com.nothatcher.chessoverlay.browser

import com.nothatcher.chessoverlay.chess.BoardOrientation
import com.nothatcher.chessoverlay.session.AnalysisContext
import com.nothatcher.chessoverlay.session.SessionMode
import org.junit.Test

class LichessPayloadValidatorTest {
    private val afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"

    @Test
    fun acceptsObservedStockfishPosition() {
        val result = LichessPayloadValidator.validate(payload(fen = afterE4, bot = true, orientation = "black"))
        check(result is LichessPayloadResult.Accepted)
        result as LichessPayloadResult.Accepted
        check(result.decision.mode == SessionMode.PRACTICE)
        check(result.decision.context == AnalysisContext.BOT)
        check(result.orientation == BoardOrientation.BLACK_BOTTOM)
        check(result.position.sideToMove.name == "BLACK")
        check(result.quad.topLeft.x == 0f && result.quad.topLeft.y == 346f)
    }

    @Test
    fun liveHumanPayloadIsStillDetectedWithoutEngineHints() {
        val result = LichessPayloadValidator.validate(payload(fen = afterE4, bot = false)) as LichessPayloadResult.Accepted
        check(result.decision.mode == SessionMode.PRACTICE)
        check(result.decision.context == AnalysisContext.LIVE_HUMAN)
        check(!result.decision.hintsAllowed)
        check(result.position.sideToMove.name == "BLACK")
    }

    @Test
    fun rejectsExternalUrlMalformedFenAndInvalidPosition() {
        check(LichessPayloadValidator.validate(payload(url = "https://example.com/x", fen = afterE4)) is LichessPayloadResult.Rejected)
        check(LichessPayloadValidator.validate(payload(fen = "not fen")) is LichessPayloadResult.Rejected)
        check(LichessPayloadValidator.validate(payload(fen = "8/8/8/8/8/8/8/8 w - - 0 1")) is LichessPayloadResult.Rejected)
    }

    private fun payload(
        url: String = "https://lichess.org/AbCd1234",
        fen: String,
        orientation: String = "white",
        bot: Boolean = false,
    ) = LichessDomPayload(
        url = url,
        fen = fen,
        orientation = orientation,
        leftPx = 0f,
        topPx = 346f,
        sizePx = 704f,
        botEvidence = bot,
        gameOverEvidence = false,
        pieceCount = 32,
    )
}
