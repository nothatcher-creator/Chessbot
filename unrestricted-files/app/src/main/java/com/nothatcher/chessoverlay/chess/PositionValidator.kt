package com.nothatcher.chessoverlay.chess

object PositionValidator {
    fun validate(position: Position): ValidationResult {
        val reasons = mutableListOf<String>()
        val whiteKings = position.board.count { it == Piece(Color.WHITE, PieceType.KING) }
        val blackKings = position.board.count { it == Piece(Color.BLACK, PieceType.KING) }
        val whitePieces = position.board.count { it?.color == Color.WHITE }
        val blackPieces = position.board.count { it?.color == Color.BLACK }
        val whitePawns = position.board.count { it == Piece(Color.WHITE, PieceType.PAWN) }
        val blackPawns = position.board.count { it == Piece(Color.BLACK, PieceType.PAWN) }
        if (whitePieces > 16) reasons += "White cannot have more than 16 pieces"
        if (blackPieces > 16) reasons += "Black cannot have more than 16 pieces"
        if (whitePawns > 8) reasons += "White cannot have more than 8 pawns"
        if (blackPawns > 8) reasons += "Black cannot have more than 8 pawns"
        if (whiteKings != 1) reasons += "Expected exactly one white king"
        if (blackKings != 1) reasons += "Expected exactly one black king"

        position.board.forEachIndexed { index, piece ->
            if (piece?.type == PieceType.PAWN) {
                val rank = index / 8
                if (rank == 0 || rank == 7) reasons += "Pawn on back rank"
            }
        }

        if (whiteKings == 1 && blackKings == 1) {
            val wk = Square.fromIndex(position.board.indexOf(Piece(Color.WHITE, PieceType.KING)))
            val bk = Square.fromIndex(position.board.indexOf(Piece(Color.BLACK, PieceType.KING)))
            if (kotlin.math.abs(wk.file - bk.file) <= 1 && kotlin.math.abs(wk.rank - bk.rank) <= 1) {
                reasons += "Kings may not be adjacent"
            }
            if (MoveGenerator.isKingInCheck(position, Color.WHITE) && MoveGenerator.isKingInCheck(position, Color.BLACK)) {
                reasons += "Both kings cannot be in check"
            }
        }

        return ValidationResult(reasons.isEmpty(), reasons)
    }
}
