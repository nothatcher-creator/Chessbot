package com.nothatcher.chessoverlay.browser

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.nothatcher.chessoverlay.ChessOverlayApplication
import com.nothatcher.chessoverlay.capture.CaptureState
import com.nothatcher.chessoverlay.capture.ProjectionController
import com.nothatcher.chessoverlay.chess.BoardOrientation
import com.nothatcher.chessoverlay.chess.Color as ChessColor
import com.nothatcher.chessoverlay.overlay.android.BoardArrowView
import com.nothatcher.chessoverlay.overlay.android.OverlayController
import com.nothatcher.chessoverlay.overlay.android.OverlayState
import com.nothatcher.chessoverlay.session.RecognitionStatus
import com.nothatcher.chessoverlay.session.SessionMode
import com.nothatcher.chessoverlay.session.allowsEngineHints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LichessBrowserActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var arrowView: BoardArrowView
    private lateinit var statusView: TextView
    private lateinit var bridge: LichessJavascriptBridge
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var adapterScript: String
    private val runtime by lazy { (application as ChessOverlayApplication).runtime }

    private var pageProbeStartedMs: Long = 0L
    private var fallbackRequestLaunched = false
    private var startedFallbackCapture = false
    private var fallbackStatus: String? = null

    private val fallbackProjectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ProjectionController.start(this, result.resultCode, data)
            startedFallbackCapture = true
            fallbackStatus = "Screen fallback active"
        } else {
            fallbackStatus = "Screen fallback permission declined"
        }
        render()
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchProjectionConsent()
        } else {
            fallbackStatus = "Notification permission is required for screen fallback"
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep any already-running MediaProjection alive so it can become an immediate fallback.
        // The floating overlay is stopped because this Activity draws its own arrow above the WebView.
        OverlayController.stop(this)

        adapterScript = assets.open("lichess_adapter.js").bufferedReader().use { it.readText() }
        buildUi()
        configureWebView()

        bridge = LichessJavascriptBridge(runtime) { webView.url }
        webView.addJavascriptInterface(bridge, "ChessOverlayBridge")

        uiScope.launch { runtime.state.collectLatest { render() } }
        uiScope.launch { bridge.state.collectLatest { render() } }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        webView.loadUrl(intent.getStringExtra(EXTRA_URL) ?: DEFAULT_URL)
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        webView = WebView(this)
        arrowView = BoardArrowView(this).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xD0181818.toInt())
            textSize = 12f
            setPadding(dp(10), dp(7), dp(10), dp(7))
            text = "Opening Lichess…"
        }

        root.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(arrowView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xB0101010.toInt())
            addView(navButton("Analysis") { webView.loadUrl("https://lichess.org/analysis") })
            addView(navButton("Puzzles") { webView.loadUrl("https://lichess.org/training") })
            addView(navButton("Lichess") { webView.loadUrl("https://lichess.org/") })
            addView(navButton("Close") { finish() })
        }
        root.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP))
        root.addView(statusView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            topMargin = dp(48)
        })
        setContentView(root)
    }

    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(false)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val url = request.url.toString()
                if (LichessSitePolicy.isAllowedWebViewUrl(url)) return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                beginDomProbe(view, url)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                val current = url ?: return
                view.postDelayed({ beginDomProbe(view, current) }, 120)
            }
        }
    }

    private fun beginDomProbe(view: WebView, url: String) {
        if (!LichessSitePolicy.isAllowedWebViewUrl(url)) return
        pageProbeStartedMs = SystemClock.elapsedRealtime()
        fallbackRequestLaunched = false
        fallbackStatus = "DOM probe active"
        injectAdapter(view)
        render()
        view.postDelayed({ maybeStartScreenFallback() }, BrowserFallbackPolicy.DOM_GRACE_PERIOD_MS + 150)
    }

    private fun injectAdapter(view: WebView) {
        val url = view.url ?: return
        if (!LichessSitePolicy.isAllowedWebViewUrl(url)) return
        view.evaluateJavascript(adapterScript, null)
    }

    private fun maybeStartScreenFallback() {
        if (!::bridge.isInitialized || isFinishing || isDestroyed) return
        val url = webView.url.orEmpty()
        if (!LichessSitePolicy.shouldAutoFallback(url)) return

        val browser = bridge.state.value
        val domAvailable = browser.fen != null && sameDocument(browser.url, url)
        val elapsed = (SystemClock.elapsedRealtime() - pageProbeStartedMs).coerceAtLeast(0L)
        val captureRunning = runtime.state.value.captureState == CaptureState.RUNNING
        when (BrowserFallbackPolicy.action(domAvailable, elapsed, captureRunning, fallbackRequestLaunched)) {
            BrowserFallbackAction.USE_DOM -> Unit
            BrowserFallbackAction.USE_SCREEN_CAPTURE -> {
                fallbackStatus = "DOM unavailable • screen fallback scanning"
                prepareFallbackContext(url)
                render()
            }
            BrowserFallbackAction.REQUEST_SCREEN_CAPTURE -> {
                fallbackRequestLaunched = true
                fallbackStatus = "DOM unavailable • requesting screen fallback"
                prepareFallbackContext(url)
                requestScreenFallback()
                render()
            }
            BrowserFallbackAction.WAIT -> Unit
        }
    }

    private fun prepareFallbackContext(url: String) {
        val decision = LichessSitePolicy.decide(url, botEvidence = false, gameOverEvidence = false)
        uiScope.launch {
            runtime.coordinator.setAnalysisContext(decision.context)
            runtime.coordinator.setMode(decision.mode)
        }
    }

    private fun requestScreenFallback() {
        if (runtime.state.value.captureState == CaptureState.RUNNING) return
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchProjectionConsent()
        }
    }

    private fun launchProjectionConsent() {
        fallbackProjectionLauncher.launch(ProjectionController.createPermissionIntent(this))
    }

    private fun render() {
        if (!::bridge.isInitialized || !::webView.isInitialized) return
        val browser = bridge.state.value
        val session = runtime.state.value
        val currentUrl = webView.url.orEmpty()
        val domReady = browser.fen != null && browser.quad != null && sameDocument(browser.url, currentUrl)

        if (domReady && startedFallbackCapture) {
            ProjectionController.stop(this)
            startedFallbackCapture = false
            fallbackStatus = "DOM recovered • screen fallback stopped"
        }

        val screenReady = !domReady &&
            session.captureState == CaptureState.RUNNING &&
            session.recognitionStatus == RecognitionStatus.STABLE &&
            session.confirmedPosition != null

        val decision = if (domReady) {
            browser.decision ?: LichessSitePolicy.decide(currentUrl, false, false)
        } else {
            LichessSitePolicy.decide(currentUrl, false, false)
        }
        val hintsAllowed = decision.hintsAllowed &&
            session.mode != SessionMode.SCAN_ONLY &&
            runtime.currentAnalysisContext().allowsEngineHints()

        val confirmed = session.confirmedPosition
        val boardQuad = when {
            domReady -> browser.quad
            screenReady -> confirmed?.quad
            else -> null
        }
        val orientation = when {
            domReady -> browser.orientation
            screenReady -> confirmed?.orientation ?: BoardOrientation.WHITE_BOTTOM
            else -> BoardOrientation.WHITE_BOTTOM
        }

        arrowView.update(
            OverlayState(
                mode = session.mode,
                boardQuad = boardQuad,
                orientation = orientation,
                confidence = when {
                    domReady -> 1f
                    screenReady -> confirmed?.confidence ?: 0f
                    else -> 0f
                },
                confidenceThreshold = 0.5f,
                analysis = if (hintsAllowed && (domReady || screenReady)) session.analysis else null,
                hintsAllowed = hintsAllowed && (domReady || screenReady),
                statusMessage = session.statusMessage,
            )
        )

        val fen = when {
            domReady -> browser.fen
            screenReady -> session.lastConfirmedFen
            else -> null
        }
        val turn = when {
            domReady -> browser.sideToMove
            screenReady -> confirmed?.position?.sideToMove
            else -> null
        }
        val source = when {
            domReady -> "DOM"
            screenReady -> "Screen fallback"
            session.captureState == CaptureState.RUNNING -> "Screen fallback"
            else -> "Waiting"
        }
        val status = when {
            domReady -> browser.status
            screenReady -> session.statusMessage ?: "Board synced"
            fallbackStatus != null -> fallbackStatus!!
            else -> "Waiting for Lichess board…"
        }
        val kind = decision.kind.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
        val orientationLabel = when (orientation) {
            BoardOrientation.WHITE_BOTTOM -> "White bottom"
            BoardOrientation.BLACK_BOTTOM -> "Black bottom"
        }
        val turnLabel = when (turn) {
            ChessColor.WHITE -> "White"
            ChessColor.BLACK -> "Black"
            null -> null
        }
        val bestMove = if (hintsAllowed && (domReady || screenReady)) session.analysis?.bestMoveUci else null

        statusView.text = if (fen != null) {
            BrowserDiagnosticsFormatter.format(
                source = source,
                pageKind = kind,
                status = status,
                fen = fen,
                turn = turnLabel,
                orientation = orientationLabel,
                bestMove = bestMove,
                engineAllowed = hintsAllowed,
            )
        } else {
            "Source: $source • $kind\n$status"
        }
    }

    private fun sameDocument(payloadUrl: String?, currentUrl: String): Boolean {
        if (payloadUrl.isNullOrBlank() || currentUrl.isBlank()) return false
        val a = runCatching { android.net.Uri.parse(payloadUrl) }.getOrNull() ?: return false
        val b = runCatching { android.net.Uri.parse(currentUrl) }.getOrNull() ?: return false
        return a.scheme.equals(b.scheme, true) &&
            a.host.equals(b.host, true) &&
            a.path == b.path &&
            a.query == b.query
    }

    private fun navButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        if (startedFallbackCapture) {
            ProjectionController.stop(this)
            startedFallbackCapture = false
        }
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("ChessOverlayBridge")
            webView.stopLoading()
            webView.destroy()
        }
        if (::bridge.isInitialized) bridge.close()
        uiScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val DEFAULT_URL = "https://lichess.org/analysis"
    }
}
