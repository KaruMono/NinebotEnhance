package dev.ichinomiya.ninebotcast;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.*;

public final class BleLink {
    public interface Listener { void changed(String state); }
    private final Context context;
    private final BluetoothAdapter adapter;
    private final Listener listener;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private boolean scanning;
    private final ArrayList<String> writable = new ArrayList<>();
    private String lastAddress;

    public BleLink(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        this.adapter = manager == null ? null : manager.getAdapter();
    }

    public boolean available() { return adapter != null; }
    public List<String> writableCharacteristics() { return new ArrayList<>(writable); }

    private boolean allowed() {
        return Build.VERSION.SDK_INT < 31 ||
                (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                 context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
    }

    public void scan() {
        if (!allowed() || adapter == null || !adapter.isEnabled()) {
            say("BLE 扫描未开始：请打开蓝牙并授予附近设备权限"); return;
        }
        try {
            scanner = adapter.getBluetoothLeScanner();
            scanner.startScan(callback); scanning = true;
            say("BLE 扫描开始：等待目标设备");
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
            lastAddress = address; writable.clear();
            BluetoothDevice device = adapter.getRemoteDevice(address);
            say("BLE 连接请求 address=" + address);
            gatt = device.connectGatt(context, false, gattCallback);
        } catch (Exception e) { say("BLE 连接异常：" + e); }
    }

    public void writeHex(String characteristicUuid, String hex) {
        if (gatt == null) { say("BLE 写入失败：尚未连接"); return; }
        try {
            byte[] data = parseHex(hex);
            BluetoothGattCharacteristic target = null;
            UUID wanted = UUID.fromString(characteristicUuid.trim());
            for (BluetoothGattService service : gatt.getServices()) {
                BluetoothGattCharacteristic c = service.getCharacteristic(wanted);
                if (c != null) target = c;
            }
            if (target == null) { say("BLE 写入失败：找不到特征 " + characteristicUuid); return; }
            if ((target.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) {
                say("BLE 写入拒绝：特征不可写 " + characteristicUuid); return;
            }
            target.setWriteType((target.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                    ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            target.setValue(data);
            boolean queued = gatt.writeCharacteristic(target);
            say("BLE 写入 " + (queued ? "已排队" : "失败") + " uuid=" + characteristicUuid + " bytes=" + data.length + " hex=" + hex.replaceAll("\\s+", "").toUpperCase(Locale.ROOT));
        } catch (Exception e) { say("BLE 写入异常：" + e); }
    }

    private static byte[] parseHex(String value) {
        String s = value == null ? "" : value.replaceAll("[^0-9A-Fa-f]", "");
        if (s.length() == 0 || (s.length() & 1) != 0) throw new IllegalArgumentException("十六进制数据长度必须为偶数");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    public void close() {
        stopScan();
        if (gatt != null) try { gatt.disconnect(); gatt.close(); } catch (SecurityException ignored) {}
        gatt = null; lastAddress = null; writable.clear();
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
            writable.clear();
            say("BLE 服务发现完成 count=" + g.getServices().size());
            for (BluetoothGattService service : g.getServices()) {
                say("BLE service uuid=" + service.getUuid() + " chars=" + service.getCharacteristics().size());
                for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                    int p = c.getProperties();
                    String line = "BLE char uuid=" + c.getUuid() + " properties=0x" + Integer.toHexString(p);
                    if ((p & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) { writable.add(c.getUuid().toString()); line += " WRITABLE"; }
                    say(line);
                }
            }
            say("BLE 可写特征数量=" + writable.size() + "；未自动假定九号协议");
        }
        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) { say("BLE 写入回调 uuid=" + c.getUuid() + " status=" + status); }
    };

    private void say(String text) { LogStore.add(text); listener.changed(text); }
}
