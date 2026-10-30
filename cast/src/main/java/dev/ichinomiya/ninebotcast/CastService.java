package dev.ichinomiya.ninebotcast;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.view.Surface;
import java.nio.ByteBuffer;
import dev.ichinomiya.ninebotcast.core.DisplaySettings;

/** Capture, rendering, encoding and cleanup all belong to one worker. */
public final class CastService extends Service {
    private MediaProjection projection;
    private VirtualDisplay display;
    private MediaCodec codec;
    private Surface encoderSurface;
    private FrameRenderer renderer;
    private RtpH264 rtp;
    private HandlerThread thread;
    private Handler worker;
    private volatile boolean running, stopping;
    private boolean starting;
    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override public void onStop() {
            if (!stopping) { LogStore.add("CAST 系统已结束录屏"); finishCapture(); }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        LogStore.init(this);
        thread = new HandlerThread("cast-encoder"); thread.start();
        worker = new Handler(thread.getLooper());
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (starting || running) { LogStore.add("CAST 已在运行，忽略重复启动"); return START_NOT_STICKY; }
        if (intent == null) { stopSelf(id); return START_NOT_STICKY; }
        try {
            createNotification();
            starting = true;
            worker.post(() -> startCapture(intent));
        } catch (RuntimeException e) { LogStore.add("CAST 启动失败：" + e); stopSelf(id); }
        return START_NOT_STICKY;
    }

    private void startCapture(Intent intent) {
        if (stopping) return;
        try {
            int[] size = fitSize(num(intent, "width", 1280), num(intent, "height", 720));
            DisplaySettings settings = DisplaySettings.defaults().withFrame(size[0], size[1]);
            if (settings.width != size[0] || settings.height != size[1]) throw new IllegalArgumentException("无法适配投屏尺寸");
            int fps = num(intent, "fps", 20), rate = num(intent, "bitrate", 2500000);
            if (fps < 1 || fps > 60 || rate < 100000 || rate > 20000000) throw new IllegalArgumentException("编码参数无效");
            String host = intent.getStringExtra("host");
            if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("未填写 UDP 目标地址");
            int port = num(intent, "port", 5004);
            rtp = new RtpH264(host.trim(), port, 1200);
            projection = getSystemService(MediaProjectionManager.class).getMediaProjection(
                    intent.getIntExtra("result", 0), (Intent) intent.getParcelableExtra("data"));
            if (projection == null) throw new IllegalStateException("录屏授权无效");
            // Required for targetSdk >= 34, and handles revocation from the system capture controls.
            projection.registerCallback(projectionCallback, worker);
            codec = MediaCodec.createEncoderByType("video/avc");
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", settings.width, settings.height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, rate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            format.setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, fps);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoderSurface = codec.createInputSurface(); codec.start();
            renderer = new FrameRenderer(encoderSurface, settings, worker, drain, this::finishCapture);
            display = projection.createVirtualDisplay("Ninebot Independent Cast", settings.virtualWidth,
                    settings.virtualHeight, settings.dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    renderer.inputSurface(), null, worker);
            if (display == null) throw new IllegalStateException("无法创建采集屏");
            running = true;
            LogStore.add("CAST running " + settings.label() + " top=" + settings.contentTop()
                    + " fps=" + fps + " bitrate=" + rate + " target=" + host + ":" + port);
            worker.post(drain);
        } catch (Exception e) { LogStore.add("CAST 启动失败：" + e); finishCapture(); }
    }

    private final Runnable drain = new Runnable() {
        @Override public void run() {
            worker.removeCallbacks(this);
            if (!running || stopping) return;
            try {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                for (int count = 0; count < 8; count++) {
                    int index = codec.dequeueOutputBuffer(info, 0);
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat format = codec.getOutputFormat();
                        rtp.codecConfig(bytes(format.getByteBuffer("csd-0")), bytes(format.getByteBuffer("csd-1")));
                    } else if (index >= 0) {
                        try {
                            ByteBuffer buffer = codec.getOutputBuffer(index);
                            if (buffer != null && info.size > 0) {
                                ByteBuffer range = buffer.duplicate(); range.position(info.offset); range.limit(info.offset + info.size);
                                byte[] data = bytes(range);
                                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) rtp.codecConfig(data);
                                else rtp.sendAccessUnit(data, info.presentationTimeUs);
                            }
                        } finally { codec.releaseOutputBuffer(index, false); }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) { finishCapture(); return; }
                    } else break;
                }
                worker.postDelayed(this, 10);
            } catch (Exception e) { LogStore.add("CAST 编码/发送异常：" + e); finishCapture(); }
        }
    };

    private static byte[] bytes(ByteBuffer buffer) {
        if (buffer == null) return null;
        ByteBuffer copy = buffer.duplicate(); byte[] data = new byte[copy.remaining()]; copy.get(data); return data;
    }

    private void finishCapture() {
        running = false;
        worker.removeCallbacks(drain);
        cleanup();
        stopSelf();
    }

    private void cleanup() {
        if (projection != null) release(() -> projection.unregisterCallback(projectionCallback));
        if (display != null) { release(display::release); display = null; }
        if (renderer != null) { release(renderer::close); renderer = null; }
        if (codec != null) { release(codec::stop); release(codec::release); codec = null; }
        if (encoderSurface != null) { release(encoderSurface::release); encoderSurface = null; }
        if (projection != null) { release(projection::stop); projection = null; }
        if (rtp != null) { rtp.close(); rtp = null; }
    }

    private static void release(Runnable action) {
        try { action.run(); } catch (RuntimeException e) { LogStore.add("CAST 释放资源：" + e); }
    }

    private void createNotification() {
        String channel = "cast";
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(channel, "九号独立投屏", NotificationManager.IMPORTANCE_LOW));
        startForeground(7, new Notification.Builder(this, channel).setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("九号独立投屏").setContentText("正在投屏").setOngoing(true).build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }

    private static int num(Intent intent, String key, int fallback) {
        try { return Integer.parseInt(intent.getStringExtra(key)); } catch (Exception e) { return fallback; }
    }

    private static int[] fitSize(int width, int height) {
        int w = Math.max(160, width), h = Math.max(160, height);
        float scale = Math.min(1280f / w, 720f / h);
        if (scale < 1f) { w = Math.round(w * scale); h = Math.round(h * scale); }
        return new int[]{Math.max(2, w & ~1), Math.max(2, h & ~1)};
    }

    @Override public void onDestroy() {
        stopping = true; running = false;
        worker.removeCallbacksAndMessages(null);
        worker.post(() -> { cleanup(); thread.quitSafely(); LogStore.add("CAST 已停止"); });
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
