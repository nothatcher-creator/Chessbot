package com.nothatcher.chessoverlay.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nothatcher.chessoverlay.browser.LichessBrowserActivity
import com.nothatcher.chessoverlay.capture.CaptureState
import com.nothatcher.chessoverlay.session.AnalysisContext
import com.nothatcher.chessoverlay.session.SessionMode
import com.nothatcher.chessoverlay.session.SessionUiState
import com.nothatcher.chessoverlay.session.allowsEngineHints
import com.nothatcher.chessoverlay.settings.UserPreferences

@Composable
fun HomeScreen(
    state: SessionUiState,
    preferences: UserPreferences,
    overlayGranted: Boolean,
    engineError: String?,
    onMode: (SessionMode) -> Unit,
    onContext: (AnalysisContext) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onEngineSettings: () -> Unit,
    onRecognitionSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val analysisContext = state.analysisContext
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("ChessOverlay", style = MaterialTheme.typography.headlineMedium)
        Text("On-device chess analysis with DOM-first Lichess support and screen-scan fallback", style = MaterialTheme.typography.bodyMedium)

        SectionCard("Lichess DOM mode") {
            Text("Most reliable for Lichess. Reads the board directly from the page instead of guessing pieces from pixels.")
            Button(onClick = {
                context.startActivity(Intent(context, LichessBrowserActivity::class.java))
            }) { Text("Open Lichess browser") }
            Text("Board detection and diagnostics stay active on any supported Lichess board page. Stockfish analyzes automatically on analysis, puzzles, bot/self-play and finished games; active human games still hide engine output.", style = MaterialTheme.typography.bodySmall)
        }

        SectionCard("Mode") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Practice", state.mode == SessionMode.PRACTICE) { onMode(SessionMode.PRACTICE) }
                ModeChip("Scan Only", state.mode == SessionMode.SCAN_ONLY) { onMode(SessionMode.SCAN_ONLY) }
                ModeChip("Review", state.mode == SessionMode.POST_GAME_REVIEW) { onMode(SessionMode.POST_GAME_REVIEW) }
            }
            if (state.mode == SessionMode.SCAN_ONLY) {
                SafetyText("Engine move suggestions are disabled while scanning.")
            }
        }

        if (state.mode == SessionMode.PRACTICE) {
            SectionCard("Practice context") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContextChip("Puzzle", AnalysisContext.PUZZLE, analysisContext, onContext)
                    ContextChip("Bot", AnalysisContext.BOT, analysisContext, onContext)
                    ContextChip("Self-play", AnalysisContext.SELF_PLAY, analysisContext, onContext)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContextChip("Analysis", AnalysisContext.ANALYSIS_BOARD, analysisContext, onContext)
                    ContextChip("Live human", AnalysisContext.LIVE_HUMAN, analysisContext, onContext)
                }
                if (analysisContext == AnalysisContext.LIVE_HUMAN) {
                    SafetyText("Live human game selected: engine hints and move arrows are disabled. The board can still be recorded for post-game review.")
                }
            }
        }

        SectionCard("Screen-scan fallback") {
            Text("Use this for unsupported chess apps or sites.", style = MaterialTheme.typography.bodySmall)
            StatusRow("Capture", state.captureState.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase))
            StatusRow("Board", state.recognitionStatus.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase))
            state.confirmedPosition?.let { StatusRow("Confidence", "${(it.confidence * 100).toInt()}%") }
            state.lastConfirmedFen?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.statusMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (state.captureState == CaptureState.RUNNING) {
                Button(onClick = onStopScan, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Stop scanning") }
            } else {
                Button(onClick = onStartScan, enabled = overlayGranted) { Text("Start screen scan") }
            }
        }

        SectionCard("Overlay permission") {
            StatusRow("Draw over apps", if (overlayGranted) "Ready" else "Required for screen-scan mode")
            if (!overlayGranted) Button(onClick = onRequestOverlayPermission) { Text("Grant overlay permission") }
            else OutlinedButton(onClick = onStartOverlay) { Text("Show overlay") }
        }

        SectionCard("Best move") {
            val analysis = state.analysis
            if (!state.mode.allowsEngineHints(analysisContext)) {
                Text("Hidden in this mode")
            } else if (analysis?.bestMoveUci == null) {
                Text("Waiting for a stable board and Stockfish…")
            } else {
                Text(analysis.bestMoveUci, style = MaterialTheme.typography.headlineLarge)
                val eval = analysis.mateIn?.let { "Mate ${if (it >= 0) "+" else ""}$it" }
                    ?: analysis.evaluationCp?.let { cp -> "${if (cp >= 0) "+" else ""}${"%.2f".format(cp / 100.0)}" }
                Text(listOfNotNull(eval, "Depth ${analysis.depth}", "${analysis.nodes} nodes").joinToString(" • "))
            }
        }

        engineError?.let { SafetyText(it) }
        Text("Engine profile: ${preferences.engineProfile.name.lowercase().replaceFirstChar(Char::uppercase)}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEngineSettings) { Text("Engine") }
            OutlinedButton(onClick = onRecognitionSettings) { Text("Recognition") }
            OutlinedButton(onClick = onAbout) { Text("About") }
        }
    }
}

@Composable private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) = FilterChip(selected, onClick, { Text(text) })
@Composable private fun ContextChip(text: String, value: AnalysisContext, current: AnalysisContext, onClick: (AnalysisContext) -> Unit) = FilterChip(current == value, { onClick(value) }, { Text(text) })

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value) }
}
@Composable private fun SafetyText(text: String) { Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
