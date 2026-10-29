package dev.ichinomiya.ninebotcast;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Display;
import android.view.WindowManager;
import android.widget.*;
import java.util.Locale;

/** Standalone virtual-display sender. It deliberately has no Bluetooth/GATT dependency. */
public final class MainActivity extends Activity {
    private static final int CAPTURE = 42;
    private TextView status, sizeLabel, networkLabel, logView;
    private EditText host, port;
    private int plannedWidth, plannedHeight;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LogStore.init(this);
        buildUi();
        LogStore.add("APP 启动：独立虚拟屏模式，不使用 BLE/GATT");
        refreshLog();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 20, 28, 24);
        root.addView(text("独立虚拟屏投屏", 24, Color.BLACK));
        root.addView(text("手机画面 → 自适应虚拟屏 → H.264/RTP", 15, Color.DKGRAY));
        root.addView(text("程序不连接蓝牙，也不读写车辆 GATT。只负责创建虚拟屏和发送视频。", 13, Color.DKGRAY));

        root.addView(section("1. 视频接收端"));
        root.addView(text("填写车机或视频接收器的网络地址。车辆专用地址和协议需要由接收端提供。", 13, Color.DKGRAY));
        host = field("接收端 IP 或主机名", ""); root.addView(host);
        port = field("UDP 端口", "5004"); root.addView(port);
        networkLabel = text("当前网络：检测中", 13, Color.DKGRAY); root.addView(networkLabel);
        updateNetwork();

        root.addView(section("2. 自动虚拟屏"));
        sizeLabel = text("正在计算手机屏幕尺寸…", 14, Color.DKGRAY); root.addView(sizeLabel);
        Button recalc = button("重新检测屏幕大小"); root.addView(recalc);
        recalc.setOnClickListener(v -> planSize());

        root.addView(section("3. 开始投屏"));
        root.addView(text("点击后允许系统录屏。授权一次后，程序会创建独立虚拟屏并自动编码发送。", 13, Color.DKGRAY));
        Button start = button("开始虚拟屏投屏"); root.addView(start);
        Button stop = button("停止投屏"); root.addView(stop);
        status = text("等待开始", 14, Color.DKGRAY); root.addView(status);
        start.setOnClickListener(v -> requestCapture());
        stop.setOnClickListener(v -> { stopService(new Intent(this, CastService.class)); status.setText("投屏已停止"); LogStore.add("UI 请求停止投屏"); refreshLog(); });

        Button advanced = button("日志和系统设置"); root.addView(advanced);
        LinearLayout details = new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); details.setVisibility(android.view.View.GONE);
        logView = text("", 12, Color.DKGRAY); logView.setTypeface(android.graphics.Typeface.MONOSPACE); logView.setTextIsSelectable(true);
        ScrollView logs = new ScrollView(this); logs.addView(logView); details.addView(logs, new LinearLayout.LayoutParams(-1, 260));
        Button refresh = button("刷新日志"); details.addView(refresh);
        Button settings = button("打开应用权限设置"); details.addView(settings);
        refresh.setOnClickListener(v -> refreshLog());
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))));
        advanced.setOnClickListener(v -> { boolean show = details.getVisibility() != android.view.View.VISIBLE; details.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE); advanced.setText(show ? "收起日志和系统设置" : "日志和系统设置"); refreshLog(); });
        root.addView(details);
        ScrollView all = new ScrollView(this); all.addView(root); setContentView(all);
        planSize();
    }

    private void planSize() {
        try {
            Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
            android.graphics.Point p = new android.graphics.Point(); display.getRealSize(p);
            int sourceW = Math.max(p.x, p.y), sourceH = Math.min(p.x, p.y);
            float ratio = sourceW / (float) Math.max(1, sourceH);
            int maxW = 1280, maxH = 720;
            if (ratio >= maxW / (float) maxH) { plannedWidth = maxW; plannedHeight = even(Math.round(maxW / ratio)); }
            else { plannedHeight = maxH; plannedWidth = even(Math.round(maxH * ratio)); }
            plannedWidth = Math.max(160, plannedWidth); plannedHeight = Math.max(160, plannedHeight);
            String value = String.format(Locale.ROOT, "手机屏幕 %d×%d；自动虚拟屏 %d×%d（横屏，保持比例）", p.x, p.y, plannedWidth, plannedHeight);
            if (sizeLabel != null) sizeLabel.setText(value);
            LogStore.add("DISPLAY auto source=" + p.x + "x" + p.y + " virtual=" + plannedWidth + "x" + plannedHeight);
        } catch (Exception e) { plannedWidth = 1280; plannedHeight = 720; if (sizeLabel != null) sizeLabel.setText("无法读取屏幕，使用默认虚拟屏 1280×720"); LogStore.add("DISPLAY auto failed=" + e); }
    }

    private static int even(int value) { return Math.max(2, value & ~1); }

    private void requestCapture() {
        if (host.getText().toString().trim().isEmpty()) { status.setText("请先填写接收端地址"); return; }
        planSize();
        startActivityForResult(((MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent(), CAPTURE);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CAPTURE) return;
        if (result != RESULT_OK || data == null) { status.setText("录屏授权已取消"); LogStore.add("录屏授权取消"); return; }
        Intent i = new Intent(this, CastService.class).putExtra("result", result).putExtra("data", data)
                .putExtra("host", host.getText().toString().trim()).putExtra("port", port.getText().toString().trim())
                .putExtra("width", Integer.toString(plannedWidth)).putExtra("height", Integer.toString(plannedHeight));
        startForegroundService(i); status.setText("虚拟屏已启动，正在发送视频"); LogStore.add("UI 启动自动虚拟屏投屏"); refreshLog();
    }

    private void updateNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork(); NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
            String type = c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "移动数据" : c != null ? "其他网络" : "无网络";
            networkLabel.setText("当前网络：" + type + "；发送使用系统默认路由"); LogStore.add("NETWORK 当前=" + type);
        } catch (Exception e) { networkLabel.setText("当前网络：无法读取"); }
    }

    private void refreshLog() { if (logView != null) logView.setText(LogStore.text()); }
    private TextView section(String s) { return text(s, 18, Color.rgb(0, 80, 80)); }
    private Button button(String s) { Button b = new Button(this); b.setText(s); return b; }
    private EditText field(String hint, String value) { EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setSingleLine(true); return e; }
    private TextView text(String s, int size, int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setPadding(0, 7, 0, 7); return v; }
}
