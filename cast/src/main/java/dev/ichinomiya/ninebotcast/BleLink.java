package dev.ichinomiya.ninebotcast;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

public final class BleLink {
    public interface Listener { void changed(String state); }
    private final Context context;
    private final BluetoothAdapter adapter;
    private final Listener listener;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private boolean scanning;

    public BleLink(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        this.adapter = manager == null ? null : manager.getAdapter();
    }

    public boolean available() { return adapter != null; }

    private boolean allowed() {
        return Build.VERSION.SDK_INT < 31
                || context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    public void scan() {
        if (!allowed() || adapter == null || !adapter.isEnabled()) {
            say("BLE 扫描未开始：请打开蓝牙并授予附近设备权限"); return;
        }
        try {
            scanner = adapter.getBluetoothLeScanner();
            scanner.startScan(callback);
            scanning = true;
            say("BLE 扫描开始（仅记录设备，不假定任何设备就是九号车机）");
        } catch (SecurityException e) { say("BLE 扫描权限失败：" + e.getMessage()); }
    }

    public void stopScan() {
        if (scanner != null && scanning) try { scanner.stopScan(callback); } catch (SecurityException ignored) {}
        scanning = false;
    }

    public void connect(String address) {
        stopScan();
        if (!allowed() || adapter == null) { say("BLE 连接失败：缺少 BLUETOOTH_CONNECT 或蓝牙不可用"); return; }
        try {
            BluetoothDevice device = adapter.getRemoteDevice(address);
            say("BLE 连接请求 address=" + address);
            gatt = device.connectGatt(context, false, gattCallback);
        } catch (Exception e) { say("BLE 连接异常：" + e); }
    }

    public void close() {
        stopScan();
        if (gatt != null) try { gatt.disconnect(); gatt.close(); } catch (SecurityException ignored) {}
        gatt = null;
    }

    private final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) {
            BluetoothDevice device = result.getDevice(); String name = "";
            try { name = device.getName(); } catch (SecurityException ignored) {}
            String line = "BLE 发现 name=" + (name == null ? "" : name) + " address=" + device.getAddress() + " rssi=" + result.getRssi();
            LogStore.add(line); listener.changed(line);
        }
        @Override public void onScanFailed(int errorCode) { say("BLE 扫描失败 code=" + errorCode); }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int state) {
            String label = state == BluetoothProfile.STATE_CONNECTED ? "CONNECTED" : state == BluetoothProfile.STATE_DISCONNECTED ? "DISCONNECTED" : String.valueOf(state);
            say("BLE 状态 status=" + status + " state=" + label);
            if (state == BluetoothProfile.STATE_CONNECTED) try { g.discoverServices(); } catch (SecurityException ignored) {}
        }
        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) { say("BLE 服务发现失败 status=" + status); return; }
            say("BLE 服务发现完成 count=" + g.getServices().size() + "；九号专用特征尚未自动匹配");
            for (BluetoothGattService service : g.getServices()) say("BLE service uuid=" + service.getUuid() + " chars=" + service.getCharacteristics().size());
        }
    };

    private void say(String text) { LogStore.add(text); listener.changed(text); }
}
