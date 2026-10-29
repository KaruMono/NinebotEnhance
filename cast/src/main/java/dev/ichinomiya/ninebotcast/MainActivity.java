package dev.ichinomiya.ninebotcast;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int CAPTURE=42, BT_PERMISSION=43;
    private TextView status, logView, selected;
    private EditText host, port, width, height, fps, bitrate, characteristic, payload;
    private Button startButton, connectButton;
    private BleLink ble;
    private ArrayAdapter<String> devices;
    private ArrayAdapter<String> writable;
    private String selectedAddress;
    private LinearLayout advanced;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        devices = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        writable = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        LogStore.init(this);
        ble = new BleLink(this, s -> runOnUiThread(() -> {
            if (status != null) status.setText(shortStatus(s));
            if (s.startsWith("BLE 发现 ")) addDevice(s);
            if (s.contains("CONNECTED")) {
                if (connectButton != null) connectButton.setText("已连接");
                if (startButton != null) startButton.setEnabled(true);
            }
            refreshLog();
            refreshWritable();
        }));
        buildUi();
        LogStore.add("APP 启动，独立模式不依赖九号官方 App 进程");
        refreshLog();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 18, 28, 24);

        root.addView(text("九号车机投屏", 24, Color.BLACK));
        root.addView(text("先连接车机，再开始投屏", 15, Color.DKGRAY));
        root.addView(section("第 1 步：寻找车机"));
        LinearLayout scanRow = new LinearLayout(this);
        Button scan = button("扫描车机");
        Button stop = button("停止扫描");
        scanRow.addView(scan, new LinearLayout.LayoutParams(0, -2, 1));
        scanRow.addView(stop, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(scanRow);
        root.addView(text("点下面的设备名称，再连接。", 13, Color.DKGRAY));
        ListView list = new ListView(this);
        list.setAdapter(devices);
        root.addView(list, new LinearLayout.LayoutParams(-1, 150));

        selected = text("还没有选择车机", 14, Color.DKGRAY);
        root.addView(selected);
        connectButton = button("连接车机");
        connectButton.setEnabled(false);
        root.addView(connectButton);
        status = text("请先扫描车机", 14, Color.DKGRAY);
        root.addView(status);

        root.addView(section("第 2 步：开始投屏"));
        root.addView(text("连接成功后，点击下面按钮，并允许录屏权限。", 13, Color.DKGRAY));
        startButton = button("开始投屏");
        startButton.setEnabled(false);
        root.addView(startButton);
        Button stopCast = button("停止投屏");
        root.addView(stopCast);

        Button advancedToggle = button("高级设置（诊断和参数）");
        root.addView(advancedToggle);
        advanced = new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        buildAdvancedUi(advanced);
        root.addView(advanced);

        scan.setOnClickListener(v -> requestBluetooth(true));
        stop.setOnClickListener(v -> ble.stopScan());
        list.setOnItemClickListener((p, v, pos, id) -> {
            String row = devices.getItem(pos);
            selectedAddress = extractAddress(row);
            if (selectedAddress != null) {
                selected.setText("已选择：" + displayName(row));
                connectButton.setEnabled(true);
                status.setText("可以连接");
            }
        });
        connectButton.setOnClickListener(v -> {
            if (selectedAddress == null) return;
            status.setText("正在连接车机…");
            ble.connect(selectedAddress);
        });
        startButton.setOnClickListener(v -> requestBluetooth(false));
        stopCast.setOnClickListener(v -> {
            stopService(new Intent(this, CastService.class));
            status.setText("投屏已停止");
            LogStore.add("UI 请求停止投屏");
            refreshLog();
        });
        advancedToggle.setOnClickListener(v -> {
            boolean show = advanced.getVisibility() != View.VISIBLE;
            advanced.setVisibility(show ? View.VISIBLE : View.GONE);
            advancedToggle.setText(show ? "收起高级设置" : "高级设置（诊断和参数）");
        });

        ScrollView all = new ScrollView(this);
        all.addView(root);
        setContentView(all);
    }

    private void buildAdvancedUi(LinearLayout box) {
        box.addView(section("GATT 诊断"));
        box.addView(text("连接后会列出车机可写特征。没有明确协议时，不要随意写入数据。", 13, Color.DKGRAY));
        ListView chars = new ListView(this);
        chars.setAdapter(writable);
        box.addView(chars, new LinearLayout.LayoutParams(-1, 130));
        box.addView(text("写入特征 UUID", 13, Color.DKGRAY));
        characteristic = field("UUID", "");
        box.addView(characteristic);
        box.addView(text("十六进制数据", 13, Color.DKGRAY));
        payload = field("例如：01020304", "");
        box.addView(payload);
        Button write = button("写入十六进制帧");
        box.addView(write);
        write.setOnClickListener(v -> ble.writeHex(characteristic.getText().toString(), payload.getText().toString()));
        chars.setOnItemClickListener((p, v, pos, id) -> {
            String uuid = writable.getItem(pos);
            if (uuid != null) characteristic.setText(uuid);
        });

        box.addView(section("RTP 投屏参数"));
        box.addView(text("这些参数一般不用修改。", 13, Color.DKGRAY));
        box.addView(text("接收端 IP 地址", 13, Color.DKGRAY));
        host = field("例如：192.168.1.1", "192.168.1.1"); box.addView(host);
        box.addView(text("UDP 端口", 13, Color.DKGRAY));
        port = field("例如：5004", "5004"); box.addView(port);
        box.addView(text("画面宽度", 13, Color.DKGRAY));
        width = field("像素", "1280"); box.addView(width);
        box.addView(text("画面高度", 13, Color.DKGRAY));
        height = field("像素", "720"); box.addView(height);
        box.addView(text("帧率", 13, Color.DKGRAY));
        fps = field("每秒帧数", "20"); box.addView(fps);
        box.addView(text("码率", 13, Color.DKGRAY));
        bitrate = field("比特/秒", "2500000"); box.addView(bitrate);
        TextView network = text("网络状态：检测中", 13, Color.DKGRAY);
        box.addView(network);
        updateNetwork(network);
        box.addView(text("RTP 使用系统默认网络。切换移动数据后，接收端地址和端口必须能从移动网络访问。", 12, Color.DKGRAY));

        box.addView(section("日志"));
        LinearLayout actions = new LinearLayout(this);
        Button refresh = button("刷新"); Button clear = button("清空"); Button share = button("分享日志");
        actions.addView(refresh, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(clear, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(share, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(actions);
        logView = text("", 12, Color.DKGRAY);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        ScrollView logs = new ScrollView(this);
        logs.addView(logView);
        box.addView(logs, new LinearLayout.LayoutParams(-1, 300));
        refresh.setOnClickListener(v -> refreshLog());
        clear.setOnClickListener(v -> { LogStore.clear(); refreshLog(); });
        share.setOnClickListener(v -> shareLog());
        Button settings = button("打开权限设置");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        box.addView(settings);
    }

    private String shortStatus(String line) {
        if (line.contains("CONNECTED")) return "车机已连接，可以开始投屏";
        if (line.contains("DISCONNECTED")) return "车机已断开";
        if (line.startsWith("BLE 发现 ")) return "发现车机：" + displayName(line);
        if (line.contains("扫描开始")) return "正在寻找车机…";
        if (line.contains("扫描失败")) return "扫描失败，请检查蓝牙权限";
        return line;
    }

    private void addDevice(String line) {
        String address = extractAddress(line);
        if (address == null) return;
        for (int i = 0; i < devices.getCount(); i++) if (address.equals(extractAddress(devices.getItem(i)))) return;
        devices.add(line.substring("BLE 发现 ".length()));
        devices.notifyDataSetChanged();
    }

    private String extractAddress(String line) {
        if (line == null) return null;
        int p = line.indexOf("address=");
        if (p < 0) return null;
        String tail = line.substring(p + 8);
        int end = tail.indexOf(' ');
        return (end < 0 ? tail : tail.substring(0, end)).trim();
    }

    private String displayName(String line) {
        if (line == null) return "未知车机";
        int p = line.indexOf("name=");
        int a = line.indexOf(" address=");
        if (p >= 0 && a > p) {
            String name = line.substring(p + 5, a).trim();
            if (!name.isEmpty()) return name;
        }
        String address = extractAddress(line);
        return address == null ? "未知车机" : address;
    }

    private void refreshWritable() {
        if (writable != null) { writable.clear(); writable.addAll(ble.writableCharacteristics()); writable.notifyDataSetChanged(); }
    }

    private void updateNetwork(TextView view) {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
            String type = c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "移动数据" : c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : c != null ? "其他网络" : "无网络";
            view.setText("当前网络：" + type + "；RTP 使用系统默认路由");
            LogStore.add("NETWORK 当前=" + type);
        } catch (Exception e) { view.setText("网络状态：无法读取"); }
    }

    private void requestBluetooth(boolean scan) {
        if (android.os.Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, BT_PERMISSION); return;
        }
        if (scan) ble.scan(); else requestCapture();
    }

    private void requestCapture() { startActivityForResult(((MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent(), CAPTURE); }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CAPTURE || result != RESULT_OK || data == null) { if (request == CAPTURE) LogStore.add("录屏授权取消"); return; }
        Intent i = new Intent(this, CastService.class).putExtra("result", result).putExtra("data", data)
                .putExtra("host", host.getText().toString().trim()).putExtra("port", port.getText().toString())
                .putExtra("width", width.getText().toString()).putExtra("height", height.getText().toString())
                .putExtra("bitrate", bitrate.getText().toString()).putExtra("fps", fps.getText().toString());
        startForegroundService(i); status.setText("正在投屏"); refreshLog();
    }

    @Override public void onRequestPermissionsResult(int request, String[] p, int[] r) {
        super.onRequestPermissionsResult(request, p, r);
        if (request == BT_PERMISSION) { LogStore.add("蓝牙权限结果 granted=" + (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED)); refreshLog(); }
    }

    private void refreshLog() { if (logView != null) logView.setText(LogStore.text()); }
    private void shareLog() { startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "九号独立投屏日志").putExtra(Intent.EXTRA_TEXT, LogStore.text()), "分享投屏日志")); }
    private TextView section(String s) { return text(s, 18, Color.rgb(0, 80, 80)); }
    private Button button(String s) { Button b = new Button(this); b.setText(s); return b; }
    private EditText field(String hint, String value) { EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setSingleLine(true); e.setInputType(android.text.InputType.TYPE_CLASS_TEXT); return e; }
    private TextView text(String s, int size, int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setPadding(0, 7, 0, 7); return v; }
}
