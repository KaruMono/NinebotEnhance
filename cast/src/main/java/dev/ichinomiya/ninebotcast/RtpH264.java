package dev.ichinomiya.ninebotcast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** RFC 6184 single-NAL/FU-A sender. One call represents one complete access unit. */
final class RtpH264 implements AutoCloseable {
    private final DatagramSocket socket;
    private final InetAddress address;
    private final int port, mtu, ssrc = new Random().nextInt();
    private int sequence;
    private byte[] sps, pps;
    long frames, packets, bytes, fragments;

    RtpH264(String host, int port, int mtu) throws IOException {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("UDP 端口须为 1–65535");
        address = InetAddress.getByName(host);
        this.port = port;
        this.mtu = Math.max(256, Math.min(65507, mtu));
        socket = new DatagramSocket();
        LogStore.add("RTP 初始化 host=" + host + " port=" + port + " mtu=" + this.mtu + " ssrc=" + ssrc);
    }

    /** MediaCodec may provide parameter sets in csd buffers or a separate codec-config buffer. */
    void codecConfig(byte[]... buffers) {
        for (byte[] buffer : buffers) if (buffer != null && buffer.length > 0) remember(nals(buffer));
    }

    void sendAccessUnit(byte[] data, long ptsUs) throws IOException {
        List<byte[]> units = nals(data);
        if (units.isEmpty()) return;
        remember(units);
        boolean idr = contains(units, 5);
        List<byte[]> output = new ArrayList<>();
        // Repeat headers before every IDR so a receiver can join or recover without an SDP side channel.
        if (idr) {
            for (byte[] nal : units) if ((nal[0] & 31) == 9) output.add(nal);
            if (sps != null) output.add(sps);
            if (pps != null) output.add(pps);
            for (byte[] nal : units) if ((nal[0] & 31) != 7 && (nal[0] & 31) != 8 && (nal[0] & 31) != 9) output.add(nal);
        } else output.addAll(units);
        long timestamp = ptsUs * 90L / 1000L;
        for (int i = 0; i < output.size(); i++) sendNal(output.get(i), timestamp, i == output.size() - 1);
        frames++;
    }

    private void remember(List<byte[]> units) {
        for (byte[] nal : units) {
            if ((nal[0] & 31) == 7) sps = nal;
            if ((nal[0] & 31) == 8) pps = nal;
        }
    }

    private static boolean contains(List<byte[]> units, int type) {
        for (byte[] nal : units) if ((nal[0] & 31) == type) return true;
        return false;
    }

    /** Accept Annex B, four-byte length-prefixed AVC, and an individual raw parameter-set NAL. */
    private static List<byte[]> nals(byte[] data) {
        List<byte[]> units = new ArrayList<>();
        if (data == null || data.length == 0) return units;
        int offset = 0;
        while (offset + 4 < data.length) {
            long length = 0;
            for (int i = 0; i < 4; i++) length = (length << 8) | (data[offset + i] & 255);
            if (length < 1 || length > data.length - offset - 4) break;
            int end = offset + 4 + (int) length;
            units.add(Arrays.copyOfRange(data, offset + 4, end));
            offset = end;
        }
        if (offset == data.length) return units;
        units.clear();
        int prefix = findStartCode(data, 0);
        if (prefix < 0) { units.add(data.clone()); return units; }
        while (prefix >= 0) {
            int start = prefix + (data[prefix + 2] == 1 ? 3 : 4);
            int next = findStartCode(data, start);
            int end = next < 0 ? data.length : next;
            if (end > start) units.add(Arrays.copyOfRange(data, start, end));
            prefix = next;
        }
        return units;
    }

    private static int findStartCode(byte[] data, int from) {
        for (int i = from; i + 2 < data.length; i++) {
            if (data[i] == 0 && data[i + 1] == 0
                    && (data[i + 2] == 1 || (data[i + 2] == 0 && i + 3 < data.length && data[i + 3] == 1))) return i;
        }
        return -1;
    }

    private void sendNal(byte[] nal, long timestamp, boolean lastNal) throws IOException {
        if (nal.length <= mtu - 12) {
            byte[] packet = new byte[12 + nal.length];
            header(packet, lastNal, timestamp);
            System.arraycopy(nal, 0, packet, 12, nal.length);
            send(packet, false);
            return;
        }
        for (int offset = 1; offset < nal.length;) {
            int length = Math.min(mtu - 14, nal.length - offset);
            boolean end = offset + length == nal.length;
            byte[] packet = new byte[14 + length];
            header(packet, lastNal && end, timestamp);
            packet[12] = (byte) ((nal[0] & 0xe0) | 28);
            packet[13] = (byte) ((nal[0] & 31) | (offset == 1 ? 0x80 : 0) | (end ? 0x40 : 0));
            System.arraycopy(nal, offset, packet, 14, length);
            send(packet, true);
            offset += length;
        }
    }

    private void send(byte[] packet, boolean fragmented) throws IOException {
        socket.send(new DatagramPacket(packet, packet.length, address, port));
        packets++; bytes += packet.length;
        if (fragmented) fragments++;
    }

    private void header(byte[] packet, boolean marker, long timestamp) {
        packet[0] = (byte) 0x80;
        packet[1] = (byte) ((marker ? 0x80 : 0) | 96);
        packet[2] = (byte) (sequence >>> 8);
        packet[3] = (byte) sequence++;
        put(packet, 4, timestamp);
        put(packet, 8, ssrc);
    }

    private static void put(byte[] packet, int offset, long value) {
        for (int i = 3; i >= 0; i--) packet[offset + 3 - i] = (byte) (value >>> (i * 8));
    }

    @Override public void close() {
        socket.close();
        LogStore.add("RTP 关闭 frames=" + frames + " packets=" + packets + " fragments=" + fragments + " bytes=" + bytes);
    }
}
