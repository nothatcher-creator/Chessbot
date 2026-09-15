package com.nothatcher.chessoverlay.browser

import org.junit.Test

class BrowserDiagnosticsFormatterTest {
    @Test
    fun includesDetectionSourceFenTurnOrientationAndEngineState() {
        val text = BrowserDiagnosticsFormatter.format(
            source = "DOM",
            pageKind = "Live game",
            status = "Board synced",
            fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
            turn = "Black",
            orientation = "Black bottom",
            bestMove = null,
            engineAllowed = false,
        )
        check("Source: DOM" in text)
        check("Turn: Black" in text)
        check("Orientation: Black bottom" in text)
        check("FEN:" in text)
        check("Engine: disabled for active human game" in text)
    }
}
