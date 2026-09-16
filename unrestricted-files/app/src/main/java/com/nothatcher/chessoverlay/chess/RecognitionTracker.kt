package com.nothatcher.chessoverlay.chess

import com.nothatcher.chessoverlay.vision.BoardRecognition
import com.nothatcher.chessoverlay.vision.PieceClass
import kotlin.math.ln

sealed interface TrackingResult {
    data class Confirmed(
        val position: Position,
        val confidence: Float,
        val inferredMove: Move?,
    ) : TrackingResult

    data class Uncertain(
        val lastConfirmedFen: String?,
        val reason: String,
    ) : TrackingResult
}

class RecognitionTracker(
    private val confirmFrames: Int = 2,
    private val secondChoiceMargin: Float = 0.10f,
    private val maxCandidates: Int = 64,
    private val instantLegalMoveConfidence: Float = 0.95f,
    private val initialSideToMove: Color = Color.WHITE,
) {
    init {
        require(confirmFrames >= 1)
        require(maxCandidates >= 1)
    }

    private var confirmed: Position? = null
    private var pendingBoard: List<Piece?>? = null
    private var pendingCount = 0

    fun accept(observation: BoardRecognition): TrackingResult {
        val previous = confirmed
        if (previous == null) {
            standardStartBootstrap(observation)?.let { (position, confidence) ->
                return confirmPending(position, confidence, null)
            }
        }

        val candidates = generateCandidates(observation)
        if (candidates.isEmpty()) return uncertain("No plausible board candidate")

        if (previous != null) {
            val legal = candidates.asSequence()
                .mapNotNull { candidate -> TransitionResolver.resolve(previous, candidate.board)?.let { candidate to it } }
                .firstOrNull()
                ?: return uncertain("No candidate matches the previous position or one legal move")

            val (candidate, transition) = legal
            if (transition.move == null) {
                clearPending()
                return TrackingResult.Confirmed(previous, candidate.confidence, null)
            }

            if (candidate.confidence >= instantLegalMoveConfidence) {
                confirmed = transition.position
                clearPending()
                return TrackingResult.Confirmed(transition.position, candidate.confidence, transition.move)
            }

            return confirmPending(transition.position, candidate.confidence, transition.move)
        }

        val firstValid = candidates.firstNotNullOfOrNull { candidate ->
            initialPosition(candidate.board).takeIf { PositionValidator.validate(it).isValid }?.let { candidate to it }
        } ?: return uncertain("No candidate forms a valid chess position")

        return confirmPending(firstValid.second, firstValid.first.confidence, null)
    }

    fun lastConfirmed(): Position? = confirmed

    private fun standardStartBootstrap(observation: BoardRecognition): Pair<Position, Float>? {
        var supportSum = 0f
        var homeContradictions = 0
        var middleStrongPieces = 0
        val emptyIndex = PieceClass.EMPTY.ordinal

        observation.squares.forEachIndexed { index, square ->
            val rank = index / 8
            val emptyProbability = square.classProbabilities[emptyIndex].coerceIn(0f, 1f)
            val expectedOccupied = rank == 0 || rank == 1 || rank == 6 || rank == 7
            if (expectedOccupied) {
                supportSum += 1f - emptyProbability
                if (emptyProbability >= 0.70f) homeContradictions++
            } else {
                supportSum += emptyProbability
                if (emptyProbability <= 0.10f) middleStrongPieces++
            }
        }

        val meanSupport = supportSum / 64f
        if (homeContradictions != 0 || middleStrongPieces != 0 || meanSupport < 0.82f) return null
        val confidence = (observation.boardConfidence * meanSupport).coerceIn(0f, 1f)
        return STANDARD_START_POSITION to confidence
    }

    private fun confirmPending(position: Position, confidence: Float, move: Move?): TrackingResult {
        if (pendingBoard == position.board) pendingCount++ else {
            pendingBoard = position.board
            pendingCount = 1
        }
        if (pendingCount >= confirmFrames) {
            confirmed = position
            clearPending()
            return TrackingResult.Confirmed(position, confidence, move)
        }
        return uncertain("Waiting for a second compatible frame")
    }

    private fun uncertain(reason: String): TrackingResult.Uncertain = TrackingResult.Uncertain(
        lastConfirmedFen = confirmed?.let(FenCodec::format),
        reason = reason,
    )

    private fun clearPending() {
        pendingBoard = null
        pendingCount = 0
    }

    private fun generateCandidates(observation: BoardRecognition): List<PositionCandidate> {
        data class Beam(val board: MutableList<Piece?>, val logP: Double, val selectedProbSum: Float)
        var beam = listOf(Beam(MutableList(64) { null }, 0.0, 0f))

        observation.squares.forEachIndexed { squareIndex, square ->
            val ranked = square.classProbabilities.indices
                .sortedByDescending { square.classProbabilities[it] }
            val bestIndex = ranked.first()
            val bestP = square.classProbabilities[bestIndex]
            val options = mutableListOf(bestIndex)
            if (ranked.size > 1) {
                val secondIndex = ranked[1]
                val secondP = square.classProbabilities[secondIndex]
                if (secondP > 0f && bestP - secondP <= secondChoiceMargin) options += secondIndex
            }

            beam = beam.flatMap { partial ->
                options.map { classIndex ->
                    val probability = square.classProbabilities[classIndex].coerceAtLeast(1e-6f)
                    val nextBoard = partial.board.toMutableList()
                    nextBoard[squareIndex] = PieceClass.entries[classIndex].toPieceOrNull()
                    Beam(nextBoard, partial.logP + ln(probability.toDouble()), partial.selectedProbSum + probability)
                }
            }.sortedByDescending { it.logP }.take(maxCandidates)
        }

        val primary = beam.map { b ->
            val selectedMean = b.selectedProbSum / 64f
            PositionCandidate(
                board = b.board,
                logProbability = b.logP,
                confidence = (observation.boardConfidence * selectedMean).coerceIn(0f, 1f),
            )
        }.filter { basicBoardPlausible(it.board) }

        val fallback = kingAwareFallback(observation)
        return if (fallback != null && primary.none { it.board == fallback.board }) primary + fallback else primary
    }

    private fun kingAwareFallback(observation: BoardRecognition): PositionCandidate? {
        val whiteKing = PieceClass.WK.ordinal
        val blackKing = PieceClass.BK.ordinal
        val whiteCandidates = observation.squares.indices
            .sortedByDescending { observation.squares[it].classProbabilities[whiteKing] }
            .take(4)
        val blackCandidates = observation.squares.indices
            .sortedByDescending { observation.squares[it].classProbabilities[blackKing] }
            .take(4)

        var best: PositionCandidate? = null
        for (wkSquare in whiteCandidates) for (bkSquare in blackCandidates) {
            if (wkSquare == bkSquare) continue
            val wkProbability = observation.squares[wkSquare].classProbabilities[whiteKing]
            val bkProbability = observation.squares[bkSquare].classProbabilities[blackKing]
            if (wkProbability < 0.08f || bkProbability < 0.08f) continue

            val board = MutableList<Piece?>(64) { null }
            var logP = 0.0
            var probabilitySum = 0f
            for (squareIndex in 0 until 64) {
                val probabilities = observation.squares[squareIndex].classProbabilities
                val classIndex = when (squareIndex) {
                    wkSquare -> whiteKing
                    bkSquare -> blackKing
                    else -> bestNonKingClass(probabilities, squareIndex / 8)
                }
                val probability = probabilities[classIndex].coerceAtLeast(1e-6f)
                board[squareIndex] = PieceClass.entries[classIndex].toPieceOrNull()
                logP += ln(probability.toDouble())
                probabilitySum += probability
            }
            if (!basicBoardPlausible(board)) continue
            val candidate = PositionCandidate(
                board = board,
                logProbability = logP,
                confidence = (observation.boardConfidence * (probabilitySum / 64f)).coerceIn(0f, 1f),
            )
            if (best == null || candidate.logProbability > best.logProbability) best = candidate
        }
        return best
    }

    private fun bestNonKingClass(probabilities: FloatArray, rank: Int): Int {
        return probabilities.indices
            .asSequence()
            .filter { it != PieceClass.WK.ordinal && it != PieceClass.BK.ordinal }
            .filter { index ->
                if (rank != 0 && rank != 7) true
                else index != PieceClass.WP.ordinal && index != PieceClass.BP.ordinal
            }
            .maxByOrNull { probabilities[it] }
            ?: PieceClass.EMPTY.ordinal
    }

    private fun initialPosition(board: List<Piece?>): Position {
        val isStandardStart = board == STANDARD_START_BOARD
        return Position(
            board = board,
            sideToMove = initialSideToMove,
            whiteKingSide = isStandardStart,
            whiteQueenSide = isStandardStart,
            blackKingSide = isStandardStart,
            blackQueenSide = isStandardStart,
            enPassant = null,
            halfmoveClock = 0,
            fullmoveNumber = 1,
        )
    }

    private fun basicBoardPlausible(board: List<Piece?>): Boolean {
        if (board.size != 64) return false
        if (board.count { it == Piece(Color.WHITE, PieceType.KING) } != 1) return false
        if (board.count { it == Piece(Color.BLACK, PieceType.KING) } != 1) return false
        for (file in 0..7) {
            if (board[Square(file, 0).index]?.type == PieceType.PAWN) return false
            if (board[Square(file, 7).index]?.type == PieceType.PAWN) return false
        }
        return true
    }

    private fun PieceClass.toPieceOrNull(): Piece? = when (this) {
        PieceClass.EMPTY -> null
        PieceClass.WP -> Piece(Color.WHITE, PieceType.PAWN)
        PieceClass.WN -> Piece(Color.WHITE, PieceType.KNIGHT)
        PieceClass.WB -> Piece(Color.WHITE, PieceType.BISHOP)
        PieceClass.WR -> Piece(Color.WHITE, PieceType.ROOK)
        PieceClass.WQ -> Piece(Color.WHITE, PieceType.QUEEN)
        PieceClass.WK -> Piece(Color.WHITE, PieceType.KING)
        PieceClass.BP -> Piece(Color.BLACK, PieceType.PAWN)
        PieceClass.BN -> Piece(Color.BLACK, PieceType.KNIGHT)
        PieceClass.BB -> Piece(Color.BLACK, PieceType.BISHOP)
        PieceClass.BR -> Piece(Color.BLACK, PieceType.ROOK)
        PieceClass.BQ -> Piece(Color.BLACK, PieceType.QUEEN)
        PieceClass.BK -> Piece(Color.BLACK, PieceType.KING)
    }

    companion object {
        private val STANDARD_START_POSITION = FenCodec.parse(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        )
        private val STANDARD_START_BOARD = STANDARD_START_POSITION.board
    }
}
