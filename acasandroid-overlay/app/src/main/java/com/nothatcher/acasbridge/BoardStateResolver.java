package com.nothatcher.acasbridge;

import com.github.bhlangonijr.chesslib.Board;
import java.util.List;

public final class BoardStateResolver {
    public record Result(String fen, String sideToMove, int appliedMoves, String error) {
        public boolean ok() { return error == null; }
    }

    private BoardStateResolver() {}

    public static Result standardStart() {
        Board board = new Board();
        return new Result(board.getFen(), "White", 0, null);
    }

    public static Result fromSan(List<String> moves) {
        Board board = new Board();
        int applied = 0;
        try {
            for (String san : moves) {
                if (san == null || san.isBlank()) continue;
                if (!board.doMove(san)) {
                    return new Result(board.getFen(), side(board), applied, "Rejected move: " + san);
                }
                applied++;
            }
            return new Result(board.getFen(), side(board), applied, null);
        } catch (RuntimeException e) {
            return new Result(board.getFen(), side(board), applied,
                    "Move parse failure: " + e.getClass().getSimpleName());
        }
    }

    private static String side(Board board) {
        return board.getSideToMove().name().equals("WHITE") ? "White" : "Black";
    }
}
