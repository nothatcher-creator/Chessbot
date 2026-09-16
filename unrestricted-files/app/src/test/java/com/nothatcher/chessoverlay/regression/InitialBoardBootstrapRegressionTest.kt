package com.nothatcher.chessoverlay.regression

import com.nothatcher.chessoverlay.chess.*
import com.nothatcher.chessoverlay.overlay.geometry.BoardQuad
import com.nothatcher.chessoverlay.overlay.geometry.PointFModel
import com.nothatcher.chessoverlay.vision.BoardRecognition
import com.nothatcher.chessoverlay.vision.PieceClass
import com.nothatcher.chessoverlay.vision.SquareProbabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialBoardBootstrapRegressionTest {
    @Test fun impossiblePieceCountsAreRejected() {
        val bad = FenCodec.parse(BAD_START_FEN)
        val validation = PositionValidator.validate(bad)
        assertFalse(validation.isValid)
        assertTrue(validation.reasons.any { "pawns" in it.lowercase() || "pieces" in it.lowercase() })
    }

    @Test fun screenshotBadFenBootstrapsToStandardStartFromOccupancy() {
        val tracker = RecognitionTracker(confirmFrames = 1)
        val result = tracker.accept(recognitionFromPosition(FenCodec.parse(BAD_START_FEN)))
        assertTrue(result is TrackingResult.Confirmed)
        result as TrackingResult.Confirmed
        assertEquals(STANDARD_START_FEN, FenCodec.format(result.position))
    }

    @Test fun oneMoveLayoutDoesNotResetToStartingPosition() {
        val tracker = RecognitionTracker(confirmFrames = 1)
        val afterE4 = FenCodec.parse("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")
        val result = tracker.accept(recognitionFromPosition(afterE4))
        assertTrue(result is TrackingResult.Confirmed)
        result as TrackingResult.Confirmed
        assertNotEquals(FenCodec.parse(STANDARD_START_FEN).board, result.position.board)
    }

    private fun recognitionFromPosition(position: Position): BoardRecognition {
        val squares = position.board.map { piece ->
            val probabilities = FloatArray(PieceClass.entries.size) { 0.0005f }
            probabilities[pieceClass(piece).ordinal] = 0.994f
            SquareProbabilities(probabilities)
        }
        return BoardRecognition(
            quad = BoardQuad(
                PointFModel(0f, 0f), PointFModel(800f, 0f),
                PointFModel(800f, 800f), PointFModel(0f, 800f),
            ),
            orientationHint = BoardOrientation.WHITE_BOTTOM,
            squares = squares,
            boardConfidence = .99f,
            capturedAtElapsedMs = 0L,
        )
    }

    private fun pieceClass(piece: Piece?): PieceClass = when (piece) {
        null -> PieceClass.EMPTY
        Piece(Color.WHITE, PieceType.PAWN) -> PieceClass.WP
        Piece(Color.WHITE, PieceType.KNIGHT) -> PieceClass.WN
        Piece(Color.WHITE, PieceType.BISHOP) -> PieceClass.WB
        Piece(Color.WHITE, PieceType.ROOK) -> PieceClass.WR
        Piece(Color.WHITE, PieceType.QUEEN) -> PieceClass.WQ
        Piece(Color.WHITE, PieceType.KING) -> PieceClass.WK
        Piece(Color.BLACK, PieceType.PAWN) -> PieceClass.BP
        Piece(Color.BLACK, PieceType.KNIGHT) -> PieceClass.BN
        Piece(Color.BLACK, PieceType.BISHOP) -> PieceClass.BB
        Piece(Color.BLACK, PieceType.ROOK) -> PieceClass.BR
        Piece(Color.BLACK, PieceType.QUEEN) -> PieceClass.BQ
        Piece(Color.BLACK, PieceType.KING) -> PieceClass.BK
        else -> error("Unexpected piece")
    }

    companion object {
        private const val STANDARD_START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        private const val BAD_START_FEN = "rbbqkbnR/PPPPPPPP/8/8/8/8/PPPPPPPP/BNBQKBNB w - - 0 1"
    }
}
