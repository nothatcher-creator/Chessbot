package com.nothatcher.chessoverlay.browser

import android.webkit.JavascriptInterface
import com.nothatcher.chessoverlay.AppRuntime
import com.nothatcher.chessoverlay.chess.BoardOrientation
import com.nothatcher.chessoverlay.chess.Color
import com.nothatcher.chessoverlay.session.ConfirmedPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class LichessBrowserState(
    val url: String? = null,
    val quad: com.nothatcher.chessoverlay.overlay.geometry.BoardQuad? = null,
    val orientation: BoardOrientation = BoardOrientation.WHITE_BOTTOM,
    val decision: LichessDecision? = null,
    val fen: String? = null,
    val sideToMove: Color? = null,
    val status: String = "Waiting for Lichess board…",
)

class LichessJavascriptBridge(
    private val runtime: AppRuntime,
    private val currentTopLevelUrl: () -> String?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(LichessBrowserState())
    val state: StateFlow<LichessBrowserState> = _state.asStateFlow()
    private var lastDecision: LichessDecision? = null

    @JavascriptInterface
    fun onPosition(json: String) {
        val topLevel = currentTopLevelUrl().orEmpty()
        if (!LichessSitePolicy.isAllowedWebViewUrl(topLevel)) {
            _state.value = LichessBrowserState(url = topLevel, status = "Blocked non-Lichess page")
            return
        }

        val payload = runCatching { parsePayload(json) }.getOrElse {
            _state.value = _state.value.copy(status = "Could not read Lichess board")
            return
        }
        when (val validated = LichessPayloadValidator.validate(payload)) {
            is LichessPayloadResult.Rejected -> {
                _state.value = _state.value.copy(status = "Board rejected: ${validated.reason}")
            }
            is LichessPayloadResult.Accepted -> {
                _state.value = LichessBrowserState(
                    url = payload.url,
                    quad = validated.quad,
                    orientation = validated.orientation,
                    decision = validated.decision,
                    fen = payload.fen,
                    sideToMove = validated.position.sideToMove,
                    status = if (validated.decision.hintsAllowed) "Board synced" else "Board synced • engine hidden",
                )
                scope.launch {
                    mutex.withLock {
                        val decisionChanged = lastDecision != validated.decision
                        if (decisionChanged) runtime.coordinator.acceptUncertain("Reading Lichess board…")
                        runtime.coordinator.setAnalysisContext(validated.decision.context)
                        runtime.coordinator.setMode(validated.decision.mode)
                        runtime.coordinator.acceptConfirmedPosition(
                            ConfirmedPosition(
                                position = validated.position,
                                confidence = 1f,
                                quad = validated.quad,
                                orientation = validated.orientation,
                                inferredMove = null,
                            )
                        )
                        lastDecision = validated.decision
                    }
                }
            }
        }
    }

    fun close() = scope.cancel()

    private fun parsePayload(json: String): LichessDomPayload {
        val obj = JSONObject(json)
        return LichessDomPayload(
            url = obj.getString("url"),
            fen = obj.getString("fen"),
            orientation = obj.getString("orientation"),
            leftPx = obj.getDouble("leftPx").toFloat(),
            topPx = obj.getDouble("topPx").toFloat(),
            sizePx = obj.getDouble("sizePx").toFloat(),
            botEvidence = obj.optBoolean("botEvidence", false),
            gameOverEvidence = obj.optBoolean("gameOverEvidence", false),
            pieceCount = obj.optInt("pieceCount", 0),
        )
    }
}
