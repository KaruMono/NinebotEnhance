package dev.ichinomiya.ninebotcast;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int CAPTURE=42, BT_PERMISSION=43;
    private TextView status, logView; private EditText host, port, width, height, fps, bitrate; private BleLink ble;
    private final ArrayAdapter<String> devices = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

    @Override public void onCreate(Bundle state) { super.onCreate(state); LogStore.init(this); ble=new BleLink(this, s -> runOnUiThread(() -> { status.setText(s); refreshLog(); })); buildUi(); LogStore.add("APP 启动，独立模式不依赖九号官方 App 进程"); }

    private void buildUi() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,20,28,24);
        root.addView(text("九号独立投屏器",23,Color.BLACK));
        root.addView(text("从模块中抽出的独立测试工具：录屏、H.264、RTP/UDP、BLE 连接诊断和完整日志均在本 APK 内运行。九号专用握手参数仍以实测日志为准。",14,Color.DKGRAY));
        root.addView(section("1. 蓝牙连接诊断"));
        LinearLayout bt=new LinearLayout(this); Button scan=button("扫描 BLE"); Button stop=button("停止扫描"); bt.addView(scan,new LinearLayout.LayoutParams(0,-2,1)); bt.addView(stop,new LinearLayout.LayoutParams(0,-2,1)); root.addView(bt);
        ListView list=new ListView(this); list.setAdapter(devices); list.setMinimumHeight(130); root.addView(list,new LinearLayout.LayoutParams(-1,170));
        status=text("尚未扫描",14,Color.DKGRAY); root.addView(status);
        scan.setOnClickListener(v -> requestBluetooth(true)); stop.setOnClickListener(v -> ble.stopScan());
        list.setOnItemClickListener((p,v,pos,id) -> {String row=devices.getItem(pos); if(row != null){String[] x=row.split(" "); ble.connect(x[x.length-1]);}});
        root.addView(section("2. UDP/RTP 投屏参数"));
        host=field("车机/接收端 IP（标准 RTP 测试）","192.168.1.1"); port=field("UDP 端口","5004"); width=field("宽度","1280"); height=field("高度","720"); fps=field("帧率","20"); bitrate=field("码率","2500000");
        root.addView(host); root.addView(port); root.addView(width); root.addView(height); root.addView(fps); root.addView(bitrate);
        root.addView(text("注意：连接 BLE 不等于已完成九号投屏握手。当前发送层是可验证的 H.264/RTP/UDP，BLE 层负责扫描、连接、服务枚举和日志记录；九号专用特征写入协议需要从目标车型实测补齐。",13,Color.rgb(110,55,20)));
        Button start=button("开始录屏并发送 RTP"); Button stopCast=button("停止投屏"); root.addView(start); root.addView(stopCast);
        start.setOnClickListener(v -> requestBluetooth(false)); stopCast.setOnClickListener(v -> {stopService(new Intent(this,CastService.class));LogStore.add("UI 请求停止投屏");refreshLog();});
        root.addView(section("3. 日志"));
        LinearLayout actions=new LinearLayout(this); Button refresh=button("刷新"); Button clear=button("清空"); Button share=button("分享日志"); actions.addView(refresh,new LinearLayout.LayoutParams(0,-2,1)); actions.addView(clear,new LinearLayout.LayoutParams(0,-2,1)); actions.addView(share,new LinearLayout.LayoutParams(0,-2,1)); root.addView(actions);
        logView=text("",12,Color.DKGRAY); logView.setTypeface(android.graphics.Typeface.MONOSPACE); logView.setTextIsSelectable(true); ScrollView logs=new ScrollView(this); logs.addView(logView); root.addView(logs,new LinearLayout.LayoutParams(-1,430)); refresh.setOnClickListener(v->refreshLog()); clear.setOnClickListener(v->{LogStore.clear();refreshLog();}); share.setOnClickListener(v->shareLog());
        Button settings=button("打开本应用权限设置"); settings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())))); root.addView(settings);
        ScrollView all=new ScrollView(this); all.addView(root); setContentView(all); refreshLog();
    }
    private void requestBluetooth(boolean scan) { if(android.os.Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},BT_PERMISSION);return;} if(scan)ble.scan();else requestCapture(); }
    private void requestCapture(){startActivityForResult(((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent(),CAPTURE);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=CAPTURE||result!=RESULT_OK||data == null){if(request==CAPTURE)LogStore.add("录屏授权取消");return;} Intent i=new Intent(this,CastService.class).putExtra("result",result).putExtra("data",data).putExtra("host",host.getText().toString().trim()).putExtra("port",port.getText().toString()).putExtra("width",width.getText().toString()).putExtra("height",height.getText().toString()).putExtra("bitrate",bitrate.getText().toString()).putExtra("fps",fps.getText().toString());startForegroundService(i);refreshLog();}
    @Override public void onRequestPermissionsResult(int request,String[] p,int[] r){super.onRequestPermissionsResult(request,p,r);if(request==BT_PERMISSION){LogStore.add("蓝牙权限结果 granted="+(r.length>0&&r[0]==PackageManager.PERMISSION_GRANTED));refreshLog();}}
    private void refreshLog(){if(logView != null)logView.setText(LogStore.text());}
    private void shareLog(){startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"九号独立投屏日志").putExtra(Intent.EXTRA_TEXT,LogStore.text()),"分享投屏日志"));}
    private TextView section(String s){return text(s,18,Color.rgb(0,80,80));}
    private Button button(String s){Button b=new Button(this);b.setText(s);return b;}
    private EditText field(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT);return e;}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,7,0,7);return v;}
}
