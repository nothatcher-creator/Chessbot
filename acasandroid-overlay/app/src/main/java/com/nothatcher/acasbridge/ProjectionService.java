package com.nothatcher.acasbridge;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.util.DisplayMetrics;

public final class ProjectionService extends Service {
    public static final String ACTION_START = "com.nothatcher.acasbridge.START_CAPTURE";
    public static final String ACTION_STOP = "com.nothatcher.acasbridge.STOP_CAPTURE";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_DATA = "data";

    private static final String CHANNEL_ID = "acas_capture";
    private static final int NOTIFICATION_ID = 4101;

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread worker;
    private Handler handler;
    private long lastFrameMs;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopCapture();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction()) || projection != null) return START_NOT_STICKY;

        startForeground(NOTIFICATION_ID, buildNotification());
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = manager.getMediaProjection(resultCode, data);
        if (projection == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() {
                stopCapture();
                stopSelf();
            }
        }, new Handler(Looper.getMainLooper()));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = Math.max(1, dm.widthPixels);
        int height = Math.max(1, dm.heightPixels);
        int density = Math.max(1, dm.densityDpi);

        worker = new HandlerThread("acas-capture");
        worker.start();
        handler = new Handler(worker.getLooper());
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(r -> {
            Image image = r.acquireLatestImage();
            if (image == null) return;
            try {
                long now = System.currentTimeMillis();
                if (now - lastFrameMs >= 500) {
                    lastFrameMs = now;
                    DetectorState.mergeScreen(
                            false,
                            "Unknown",
                            null,
                            false,
                            "Screen capture active. M1 is collecting clean frames; SAN Accessibility remains the authoritative FEN source.");
                }
            } finally {
                image.close();
            }
        }, handler);

        display = projection.createVirtualDisplay(
                "ACAS-M1",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(),
                null,
                handler);
        return START_NOT_STICKY;
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, ProjectionService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("ACAS Android M1")
                .setContentText("Screen capture diagnostics active")
                .addAction(new Notification.Action.Builder(null, "Stop", stopPi).build())
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "ACAS screen capture", NotificationManager.IMPORTANCE_LOW));
    }

    private void stopCapture() {
        if (reader != null) {
            reader.setOnImageAvailableListener(null, null);
            reader.close();
            reader = null;
        }
        if (display != null) {
            display.release();
            display = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
        if (worker != null) {
            worker.quitSafely();
            worker = null;
            handler = null;
        }
    }

    @Override public void onDestroy() {
        stopCapture();
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
