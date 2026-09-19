package com.nothatcher.acasbridge;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.text.DateFormat;
import java.util.Date;

public final class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 7001;
    private TextView diagnostics;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            renderState();
            ui.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("ACAS Android — Detection M1");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("Detection-only build for Lichess in Chrome and the Lichess Android app. No chess engine is included in this milestone.");
        desc.setTextSize(15);
        desc.setPadding(0, dp(8), 0, dp(16));
        root.addView(desc);

        Button accessibility = new Button(this);
        accessibility.setText("Open Accessibility settings");
        accessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);

        Button capture = new Button(this);
        capture.setText("Start screen capture fallback");
        capture.setOnClickListener(v -> requestCapture());
        root.addView(capture);

        Button stop = new Button(this);
        stop.setText("Stop screen capture");
        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, ProjectionService.class).setAction(ProjectionService.ACTION_STOP);
            startService(i);
        });
        root.addView(stop);

        TextView how = new TextView(this);
        how.setText("\nTest steps:\n1. Enable ACAS Lichess Detector in Accessibility.\n2. Return here and optionally start screen capture.\n3. Open Lichess in Chrome or the Lichess app.\n4. Return here to inspect the detected move history/FEN.\n");
        root.addView(how);

        diagnostics = new TextView(this);
        diagnostics.setTextSize(14);
        diagnostics.setTypeface(Typeface.MONOSPACE);
        diagnostics.setTextIsSelectable(true);
        diagnostics.setPadding(0, dp(8), 0, dp(24));
        root.addView(diagnostics);

        TextView attribution = new TextView(this);
        attribution.setText("Architecture/reference: Psyyke/A.C.A.S. This milestone is GPL-3.0 compatible and performs all detection on-device.");
        attribution.setTextSize(12);
        root.addView(attribution);

        setContentView(scroll);
    }

    private void requestCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE || resultCode != RESULT_OK || data == null) return;
        Intent i = new Intent(this, ProjectionService.class)
                .setAction(ProjectionService.ACTION_START)
                .putExtra(ProjectionService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(ProjectionService.EXTRA_DATA, data);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private void renderState() {
        DetectorState.Snapshot s = DetectorState.get();
        String updated = DateFormat.getTimeInstance().format(new Date(s.updatedAtMs()));
        diagnostics.setText(
                "Target: " + s.target() + "\n" +
                "Source: " + s.source() + "\n" +
                "Board confirmed: " + s.boardDetected() + "\n" +
                "FEN: " + (s.fen() == null ? "—" : s.fen()) + "\n" +
                "Side to move: " + s.sideToMove() + "\n" +
                "Orientation: " + s.orientation() + "\n" +
                "Board rect: " + (s.boardRect() == null ? "—" : s.boardRect()) + "\n" +
                "Accessibility nodes: " + s.accessibilityNodes() + "\n" +
                "SAN moves observed: " + s.movesObserved() + "\n" +
                "Status: " + s.message() + "\n" +
                "Updated: " + updated);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onResume() {
        super.onResume();
        ui.post(refresh);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(refresh);
        super.onPause();
    }
}
