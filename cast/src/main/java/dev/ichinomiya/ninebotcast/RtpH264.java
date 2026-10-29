package dev.ichinomiya.ninebotcast;

import java.io.*; import java.net.*; import java.util.*;

final class RtpH264 {
    private final DatagramSocket socket; private final InetAddress address; private final int port, mtu; private int sequence; private final int ssrc = new Random().nextInt();
    long frames, packets, bytes, fragments;
    RtpH264(String host, int port, int mtu) throws Exception { address=InetAddress.getByName(host); this.port=port; this.mtu=Math.max(256,mtu); socket=new DatagramSocket(); LogStore.add("RTP 初始化 host="+host+" port="+port+" mtu="+this.mtu+" ssrc="+ssrc); }
    void sendAccessUnit(byte[] data, long ptsUs) throws IOException {
        int p=0; boolean sent=false; while(p+3<data.length){
            while(p+3<data.length && !startCode(data,p))p++;
            if(p+3>=data.length)break; int start=p+(data[p+2]==1?3:4), next=start;
            while(next+3<data.length&&!startCode(data,next))next++;
            sendNal(data,start,next,ptsUs/1000000L*90); p=next; sent=true;
        }
        if(sent) frames++;
    }
    private boolean startCode(byte[] d,int p){return d[p]==0&&d[p+1]==0&&(d[p+2]==1||(d[p+2]==0&&p+3<d.length&&d[p+3]==1));}
    private void sendNal(byte[] d,int s,int e,long ts) throws IOException { if(e<=s)return; int n=e-s; if(n<=mtu-12){packet(d,s,n,true,ts);return;} int type=d[s]&31,max=mtu-14,off=s+1; while(off<e){int len=Math.min(max,e-off);boolean end=off+len>=e;byte[] p=new byte[14+len];header(p,false,ts);p[12]=(byte)((d[s]&0xe0)|28);p[13]=(byte)((type&31)|(off==s+1?0x80:0)|(end?0x40:0));System.arraycopy(d,off,p,14,len);socket.send(new DatagramPacket(p,p.length,address,port));packets++;bytes+=p.length;fragments++;off+=len;}}
    private void packet(byte[] d,int s,int n,boolean marker,long ts)throws IOException{byte[] p=new byte[12+n];header(p,marker,ts);System.arraycopy(d,s,p,12,n);socket.send(new DatagramPacket(p,p.length,address,port));packets++;bytes+=p.length;}
    private void header(byte[] p,boolean marker,long ts){p[0]=(byte)0x80;p[1]=(byte)((marker?0x80:0)|96);p[2]=(byte)(sequence>>>8);p[3]=(byte)sequence++;put(p,4,ts);put(p,8,ssrc);}
    private static void put(byte[] p,int o,long v){for(int i=3;i>=0;i--)p[o+3-i]=(byte)(v>>>(i*8));}
    void close(){socket.close();LogStore.add("RTP 关闭 frames="+frames+" packets="+packets+" fragments="+fragments+" bytes="+bytes);}
}
