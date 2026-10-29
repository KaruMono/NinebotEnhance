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
    private TextView status, logView; private EditText host, port, width, height, fps, bitrate, characteristic, payload;
    private BleLink ble; private ArrayAdapter<String> devices; private ArrayAdapter<String> writable;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); devices = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1); writable = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        LogStore.init(this); ble = new BleLink(this, s -> runOnUiThread(() -> { if (status != null) status.setText(s); if (s.startsWith("BLE 发现 ")) addDevice(s); refreshLog(); refreshWritable(); }));
        try { buildUi(); LogStore.add("APP 启动，独立模式不依赖九号官方 App 进程"); refreshLog(); } catch (RuntimeException e) { LogStore.add("APP UI 初始化失败：" + e); throw e; }
    }

    private void buildUi() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,20,28,24);
        root.addView(text("九号独立投屏器",23,Color.BLACK));
        root.addView(text("独立 BLE 连接、录屏和 RTP 投屏工具。",14,Color.DKGRAY));
        root.addView(section("1. 蓝牙连接与 GATT 诊断"));
        LinearLayout bt=new LinearLayout(this); Button scan=button("扫描 BLE"); Button stop=button("停止扫描"); bt.addView(scan,new LinearLayout.LayoutParams(0,-2,1)); bt.addView(stop,new LinearLayout.LayoutParams(0,-2,1)); root.addView(bt);
        ListView list=new ListView(this); list.setAdapter(devices); root.addView(list,new LinearLayout.LayoutParams(-1,170));
        status=text("尚未扫描",14,Color.DKGRAY); root.addView(status); scan.setOnClickListener(v -> requestBluetooth(true)); stop.setOnClickListener(v -> ble.stopScan());
        list.setOnItemClickListener((p,v,pos,id) -> { String row=devices.getItem(pos); if(row != null){ String[] x=row.split(" "); if(x.length>0) ble.connect(x[x.length-1]); }});
        root.addView(text("可写特征列表",13,Color.DKGRAY)); ListView chars=new ListView(this); chars.setAdapter(writable); root.addView(chars,new LinearLayout.LayoutParams(-1,150));
        root.addView(text("写入特征 UUID",13,Color.DKGRAY)); characteristic=field("UUID",""); root.addView(characteristic);
        root.addView(text("十六进制数据",13,Color.DKGRAY)); payload=field("例如：01020304",""); root.addView(payload);
        root.addView(text("先点选上面的可写特征，再输入要发送的数据。协议未知时不要随意发送。",12,Color.DKGRAY));
        Button write=button("写入十六进制帧"); root.addView(write); write.setOnClickListener(v -> ble.writeHex(characteristic.getText().toString(),payload.getText().toString()));
        chars.setOnItemClickListener((p,v,pos,id) -> { String uuid=writable.getItem(pos); if(uuid != null) characteristic.setText(uuid); });
        root.addView(text("BLE 连接成功只代表 GATT 可用，不代表九号投屏握手成功。发送帧前请依据目标车型实测日志确认。",13,Color.rgb(110,55,20)));
        root.addView(section("2. UDP/RTP 投屏参数"));
        root.addView(text("接收端 IP 地址",13,Color.DKGRAY)); host=field("例如：192.168.1.1","192.168.1.1"); root.addView(host);
        root.addView(text("UDP 端口",13,Color.DKGRAY)); port=field("例如：5004","5004"); root.addView(port);
        root.addView(text("画面宽度",13,Color.DKGRAY)); width=field("像素","1280"); root.addView(width);
        root.addView(text("画面高度",13,Color.DKGRAY)); height=field("像素","720"); root.addView(height);
        root.addView(text("帧率",13,Color.DKGRAY)); fps=field("每秒帧数","20"); root.addView(fps);
        root.addView(text("码率",13,Color.DKGRAY)); bitrate=field("比特/秒","2500000"); root.addView(bitrate);
        TextView network=text("网络状态：检测中",13,Color.DKGRAY); root.addView(network); updateNetwork(network);
        root.addView(text("RTP 使用系统默认网络。手机切到移动数据后，只要运营商网络允许访问接收端 IP 和端口，就可以发送。",12,Color.rgb(70,70,70)));
        Button start=button("开始录屏并发送 RTP"); Button stopCast=button("停止投屏"); root.addView(start); root.addView(stopCast); start.setOnClickListener(v -> requestBluetooth(false)); stopCast.setOnClickListener(v -> {stopService(new Intent(this,CastService.class));LogStore.add("UI 请求停止投屏");refreshLog();});
        root.addView(section("3. 日志")); LinearLayout actions=new LinearLayout(this); Button refresh=button("刷新"); Button clear=button("清空"); Button share=button("分享日志"); actions.addView(refresh,new LinearLayout.LayoutParams(0,-2,1)); actions.addView(clear,new LinearLayout.LayoutParams(0,-2,1)); actions.addView(share,new LinearLayout.LayoutParams(0,-2,1)); root.addView(actions);
        logView=text("",12,Color.DKGRAY); logView.setTypeface(android.graphics.Typeface.MONOSPACE); logView.setTextIsSelectable(true); ScrollView logs=new ScrollView(this); logs.addView(logView); root.addView(logs,new LinearLayout.LayoutParams(-1,430)); refresh.setOnClickListener(v->refreshLog()); clear.setOnClickListener(v->{LogStore.clear();refreshLog();}); share.setOnClickListener(v->shareLog());
        Button settings=button("打开本应用权限设置"); settings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())))); root.addView(settings);
        ScrollView all=new ScrollView(this); all.addView(root); setContentView(all); refreshLog();
    }
    private void addDevice(String line){ String[] x=line.split(" address="); if(x.length<2)return; String address=x[1].split(" ")[0]; for(int i=0;i<devices.getCount();i++) if(devices.getItem(i).endsWith(address)) return; devices.add(line.substring("BLE 发现 ".length())); devices.notifyDataSetChanged(); }
    private void refreshWritable(){if(writable != null){writable.clear();writable.addAll(ble.writableCharacteristics());writable.notifyDataSetChanged();}}
    private void updateNetwork(TextView view){
        try {
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            Network n=cm.getActiveNetwork();
            NetworkCapabilities c=n == null ? null : cm.getNetworkCapabilities(n);
            String type=c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"移动数据":c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":c != null?"其他网络":"无网络";
            view.setText("当前网络："+type+"；RTP 将使用系统默认路由"); LogStore.add("NETWORK 当前="+type);
        } catch(Exception e){ view.setText("网络状态：无法读取"); LogStore.add("NETWORK 检测失败："+e.getClass().getSimpleName()); }
    }
    private void requestBluetooth(boolean scan) { if(android.os.Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},BT_PERMISSION);return;} if(scan)ble.scan();else requestCapture(); }
    private void requestCapture(){startActivityForResult(((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent(),CAPTURE);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=CAPTURE||result!=RESULT_OK||data == null){if(request==CAPTURE)LogStore.add("录屏授权取消");return;} Intent i=new Intent(this,CastService.class).putExtra("result",result).putExtra("data",data).putExtra("host",host.getText().toString().trim()).putExtra("port",port.getText().toString()).putExtra("width",width.getText().toString()).putExtra("height",height.getText().toString()).putExtra("bitrate",bitrate.getText().toString()).putExtra("fps",fps.getText().toString());startForegroundService(i);refreshLog();}
    @Override public void onRequestPermissionsResult(int request,String[] p,int[] r){super.onRequestPermissionsResult(request,p,r);if(request==BT_PERMISSION){LogStore.add("蓝牙权限结果 granted="+(r.length>0&&r[0]==PackageManager.PERMISSION_GRANTED));refreshLog();}}
    private void refreshLog(){if(logView != null)logView.setText(LogStore.text());}
    private void shareLog(){startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"九号独立投屏日志").putExtra(Intent.EXTRA_TEXT,LogStore.text()),"分享投屏日志"));}
    private TextView section(String s){return text(s,18,Color.rgb(0,80,80));} private Button button(String s){Button b=new Button(this);b.setText(s);return b;}
    private EditText field(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT);return e;}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,7,0,7);return v;}
}
