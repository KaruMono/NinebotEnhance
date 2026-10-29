package dev.ichinomiya.ninebotcast;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.view.*;
import java.nio.*;

public final class CastService extends Service {
    private MediaProjection projection;
    private VirtualDisplay display;
    private MediaCodec codec;
    private RtpH264 rtp;
    private Thread worker;
    private volatile boolean running;

    @Override public int onStartCommand(Intent in, int flags, int id) {
        if (running) { LogStore.add("CAST 已在运行，忽略重复启动"); return START_NOT_STICKY; }
        try {
            int w=num(in,"width",1280), h=num(in,"height",720), fps=num(in,"fps",20), rate=num(in,"bitrate",2500000);
            String host=in.getStringExtra("host"); int port=num(in,"port",5004);
            if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("未填写 UDP 目标地址");
            LogStore.add("CAST 初始化 size="+w+"x"+h+" fps="+fps+" bitrate="+rate+" target="+host+":"+port);
            createNotification();
            MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
            projection=manager.getMediaProjection(in.getIntExtra("result",0),(Intent)in.getParcelableExtra("data"));
            if (projection == null) throw new IllegalStateException("录屏授权无效");
            rtp=new RtpH264(host,port,1200);
            codec=MediaCodec.createEncoderByType("video/avc");
            MediaFormat f=MediaFormat.createVideoFormat("video/avc",w,h);
            f.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            f.setInteger(MediaFormat.KEY_BIT_RATE,rate); f.setInteger(MediaFormat.KEY_FRAME_RATE,fps); f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            codec.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            LogStore.add("CODEC configure video/avc color=surface");
            Surface surface=codec.createInputSurface(); codec.start();
            display=projection.createVirtualDisplay("Ninebot Independent Cast",w,h,getResources().getDisplayMetrics().densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,surface,null,null);
            running=true; LogStore.add("CAST running：等待编码输出");
            worker=new Thread(this::drain,"cast-encoder"); worker.start();
        } catch(Exception e) { LogStore.add("CAST 启动失败："+e); stopSelf(); }
        return START_NOT_STICKY;
    }

    private void drain() {
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        try {
            while(running) {
                int ix=codec.dequeueOutputBuffer(info,1000);
                if(ix>=0) {
                    ByteBuffer b=codec.getOutputBuffer(ix);
                    if(b != null && info.size>0 && (info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0) {
                        byte[] x=new byte[info.size]; b.position(info.offset); b.limit(info.offset+info.size); b.get(x); rtp.sendAccessUnit(x,info.presentationTimeUs);
                    }
                    codec.releaseOutputBuffer(ix,false);
                }
            }
        } catch(Exception e) { if(running) LogStore.add("CAST 编码/发送异常："+e); }
        finally { if(running){running=false;LogStore.add("CAST 工作线程结束");} stopSelf(); }
    }

    private void createNotification() {
        String ch="cast"; getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(ch,"九号独立投屏",NotificationManager.IMPORTANCE_LOW));
        startForeground(7,new Notification.Builder(this,ch).setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("九号独立投屏").setContentText("正在采集并发送 H.264/RTP UDP").setOngoing(true).build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }
    private static int num(Intent i,String k,int d){try{return Integer.parseInt(i.getStringExtra(k));}catch(Exception e){return d;}}
    @Override public void onDestroy(){running=false;if(display != null)display.release();if(codec != null){try{codec.stop();}catch(Exception ignored){}codec.release();}if(projection != null)projection.stop();if(rtp != null)rtp.close();LogStore.add("CAST 已停止");super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
