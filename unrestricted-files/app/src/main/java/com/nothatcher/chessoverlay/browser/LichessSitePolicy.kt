package com.nothatcher.chessoverlay.browser

import com.nothatcher.chessoverlay.session.AnalysisContext
import com.nothatcher.chessoverlay.session.SessionMode
import java.net.URI

enum class LichessPageKind {
    ANALYSIS,
    PUZZLE,
    STUDY,
    EDITOR,
    BOT_GAME,
    FINISHED_GAME,
    LIVE_GAME,
    UNKNOWN,
    UNSUPPORTED,
}

data class LichessDecision(
    val mode: SessionMode,
    val context: AnalysisContext,
    val hintsAllowed: Boolean,
    val kind: LichessPageKind,
)

object LichessSitePolicy {
    fun decide(url: String, botEvidence: Boolean, gameOverEvidence: Boolean): LichessDecision {
        val uri = runCatching { URI(url) }.getOrNull() ?: return unsupported()
        val host = uri.host?.lowercase() ?: return unsupported()
        if (uri.scheme?.lowercase() != "https" || !isLichessHost(host)) return unsupported()

        val path = (uri.path ?: "/").lowercase()
        when {
            path == "/analysis" || path.startsWith("/analysis/") -> return practice(AnalysisContext.ANALYSIS_BOARD, LichessPageKind.ANALYSIS)
            path == "/editor" || path.startsWith("/editor/") -> return practice(AnalysisContext.ANALYSIS_BOARD, LichessPageKind.EDITOR)
            path == "/study" || path.startsWith("/study/") -> return practice(AnalysisContext.ANALYSIS_BOARD, LichessPageKind.STUDY)
            path == "/training" || path.startsWith("/training/") || path == "/practice" || path.startsWith("/practice/") -> {
                return practice(AnalysisContext.PUZZLE, LichessPageKind.PUZZLE)
            }
        }

        if (isLikelyGamePath(path)) {
            if (gameOverEvidence) {
                return LichessDecision(SessionMode.POST_GAME_REVIEW, AnalysisContext.POST_GAME, true, LichessPageKind.FINISHED_GAME)
            }
            if (botEvidence) return practice(AnalysisContext.BOT, LichessPageKind.BOT_GAME)
            return LichessDecision(SessionMode.PRACTICE, AnalysisContext.LIVE_HUMAN, false, LichessPageKind.LIVE_GAME)
        }

        return LichessDecision(SessionMode.PRACTICE, AnalysisContext.LIVE_HUMAN, false, LichessPageKind.UNKNOWN)
    }

    fun shouldAutoFallback(url: String): Boolean {
        val kind = decide(url, botEvidence = false, gameOverEvidence = false).kind
        return kind in setOf(
            LichessPageKind.ANALYSIS,
            LichessPageKind.PUZZLE,
            LichessPageKind.STUDY,
            LichessPageKind.EDITOR,
            LichessPageKind.BOT_GAME,
            LichessPageKind.FINISHED_GAME,
            LichessPageKind.LIVE_GAME,
        )
    }

    fun isAllowedWebViewUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme?.lowercase() == "https" && isLichessHost(host)
    }

    private fun isLichessHost(host: String): Boolean = host == "lichess.org" || host.endsWith(".lichess.org")

    private fun isLikelyGamePath(path: String): Boolean {
        val first = path.trim('/').substringBefore('/')
        return first.matches(Regex("[a-z0-9]{8,12}", RegexOption.IGNORE_CASE))
    }

    private fun practice(context: AnalysisContext, kind: LichessPageKind) =
        LichessDecision(SessionMode.PRACTICE, context, true, kind)

    private fun unsupported() =
        LichessDecision(SessionMode.SCAN_ONLY, AnalysisContext.LIVE_HUMAN, false, LichessPageKind.UNSUPPORTED)
}
