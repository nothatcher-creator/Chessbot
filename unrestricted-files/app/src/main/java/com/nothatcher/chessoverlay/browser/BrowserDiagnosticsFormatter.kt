package com.nothatcher.chessoverlay.browser

object BrowserDiagnosticsFormatter {
    fun format(
        source: String,
        pageKind: String,
        status: String,
        fen: String?,
        turn: String?,
        orientation: String?,
        bestMove: String?,
        engineAllowed: Boolean,
    ): String = buildString {
        append("Source: ").append(source)
        append(" • ").append(pageKind)
        append(" • ").append(status)
        if (turn != null) append("\nTurn: ").append(turn)
        if (orientation != null) append(" • Orientation: ").append(orientation)
        if (fen != null) append("\nFEN: ").append(fen)
        when {
            bestMove != null && engineAllowed -> append("\nBest: ").append(bestMove)
            engineAllowed -> append("\nStockfish: thinking…")
            else -> append("\nEngine: disabled for active human game")
        }
    }
}
