package dev.ichinomiya.ninebotcast;

import dev.ichinomiya.ninebotcast.core.DisplaySettings;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.util.*;

/** Real loopback UDP round trips; no Android runtime or receiver-specific assumptions. */
public final class CastTests {
    private static int assertions;

    private static void check(boolean value, String detail) {
        assertions++;
        if (!value) throw new AssertionError(detail);
    }

    public static void main(String[] args) throws Exception {
        payloads(); parameters(); layout();
        System.out.println("PASS: " + assertions + " cast assertions (RTP payload/fragment reassembly, timestamps, markers, SPS/PPS and dashboard layout)");
    }

    private static void payloads() throws Exception {
        try (DatagramSocket receiver = receiver(); RtpH264 sender = sender(receiver)) {
            byte[] small = {0x61, 10, 11, 12, 13, 14, 15, 16};
            sender.sendAccessUnit(annex(small), 50000);
            Received first = receive(receiver);
            same(first, small);
            check(first.timestamp == 4500, "50ms uses the 90kHz RTP clock");
            sender.sendAccessUnit(annex(small), 100000);
            Received second = receive(receiver);
            same(second, small);
            check(second.timestamp - first.timestamp == 4500, "sub-second timestamps remain distinct");
            check(second.firstSequence == ((first.lastSequence + 1) & 65535), "RTP sequence advances across frames");
            check(first.ssrc == second.ssrc, "a session keeps one SSRC");

            for (int length : new int[]{1, 2, 3, 244, 245, 1600}) {
                byte[] nal = new byte[length]; Arrays.fill(nal, (byte) 0x44); nal[0] = 0x61;
                sender.sendAccessUnit(annex(nal), 150000 + length);
                Received frame = receive(receiver); same(frame, nal);
                check(frame.packetCount == (length <= 244 ? 1 : (length - 1 + 241) / 242), "MTU boundary packet count");
            }

            byte[] large = new byte[1600]; Arrays.fill(large, (byte) 0x42); large[0] = 0x61;
            sender.sendAccessUnit(annex(large, small), 200000);
            same(receive(receiver), large, small);
            sender.sendAccessUnit(annex(small, large), 250000);
            same(receive(receiver), small, large);
            // A three-byte start code immediately before a one-byte final NAL must also be found.
            sender.sendAccessUnit(concat(new byte[]{0, 0, 1}, small, new byte[]{0, 0, 1, 0x61}), 300000);
            same(receive(receiver), small, new byte[]{0x61});

            byte[] avcc = concat(new byte[]{0, 0, 0, 8}, small, new byte[]{0, 0, 0, 1, 0x61});
            sender.sendAccessUnit(avcc, 350000); same(receive(receiver), small, new byte[]{0x61});
            // Length 0x100 has the same first three bytes as an Annex B start code.
            byte[] ambiguous = new byte[256]; Arrays.fill(ambiguous, (byte) 0x41); ambiguous[0] = 0x61;
            sender.sendAccessUnit(concat(new byte[]{0, 0, 1, 0}, ambiguous), 400000);
            same(receive(receiver), ambiguous);

            java.lang.reflect.Field sequence = RtpH264.class.getDeclaredField("sequence");
            sequence.setAccessible(true); sequence.setInt(sender, 65535);
            long pts = (1L << 32) * 1000L / 90L + 1000000L;
            sender.sendAccessUnit(annex(large), pts);
            Received wrapped = receive(receiver); same(wrapped, large);
            check(wrapped.firstSequence == 65535 && wrapped.lastSequence < 65535, "sequence wraps at 16 bits");
            check(wrapped.timestamp == ((pts * 90L / 1000L) & 0xffffffffL), "timestamp wraps at 32 bits");
        }
    }

    private static void parameters() throws Exception {
        byte[] sps = {0x67, 100, 0, 31, 10}, pps = {0x68, 11, 12}, idr = {0x65, 20, 21, 22, 23};
        try (DatagramSocket receiver = receiver(); RtpH264 sender = sender(receiver)) {
            sender.codecConfig(annex(sps), annex(pps));
            sender.sendAccessUnit(annex(idr), 1000); same(receive(receiver), sps, pps, idr);
            sender.sendAccessUnit(annex(idr), 2000); same(receive(receiver), sps, pps, idr);
            sender.sendAccessUnit(annex(sps, idr), 3000); same(receive(receiver), sps, pps, idr);
            sender.sendAccessUnit(annex(sps, pps, idr), 4000); same(receive(receiver), sps, pps, idr);
            byte[] aud = {9, 0x10};
            sender.sendAccessUnit(annex(aud, sps, idr), 5000); same(receive(receiver), aud, sps, pps, idr);
            byte[] changed = {0x67, 77, 0, 31, 30};
            sender.codecConfig(annex(changed, pps));
            sender.sendAccessUnit(annex(idr), 6000); same(receive(receiver), changed, pps, idr);
            byte[] predicted = {0x61, 31};
            sender.sendAccessUnit(annex(predicted), 7000); same(receive(receiver), predicted);
        }
        try (DatagramSocket receiver = receiver(); RtpH264 sender = sender(receiver)) {
            sender.codecConfig(sps, pps);
            sender.sendAccessUnit(annex(idr), 8000); same(receive(receiver), sps, pps, idr);
        }
    }

    private static void layout() {
        DisplaySettings five = DisplaySettings.defaults().withFrame(848, 480);
        check(five.virtualWidth == 640 && five.virtualHeight == 440 && five.contentTop() == 40 && five.bottomInset == 0,
                "five-inch app area leaves forty rows above and the right instrument area free");
        for (int height : new int[]{600, 608}) {
            DisplaySettings seven = DisplaySettings.defaults().withFrame(1024, height);
            check(seven.virtualWidth == 760 && seven.virtualHeight == 496 && seven.dpi == 160,
                    "seven-inch capture dimensions and DPI");
            check(seven.contentTop() == 48 && seven.contentBottom() == 544 && seven.bottomInset == height - 544,
                    "seven-inch composition preserves top and bottom reserved rows");
        }
        DisplaySettings half = DisplaySettings.defaults().withFrame(240, 320);
        check(half.virtualWidth == 240 && half.virtualHeight == 300 && half.contentTop() == 20, "half-screen app area");
    }

    private static DatagramSocket receiver() throws Exception {
        DatagramSocket socket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1")); socket.setSoTimeout(2000); return socket;
    }
    private static RtpH264 sender(DatagramSocket receiver) throws Exception { return new RtpH264("127.0.0.1", receiver.getLocalPort(), 256); }

    private record Received(List<byte[]> nals, long timestamp, long ssrc, int firstSequence, int lastSequence, int packetCount) {}

    private static Received receive(DatagramSocket receiver) throws Exception {
        List<byte[]> nals = new ArrayList<>();
        ByteArrayOutputStream fragment = null;
        long timestamp = -1, ssrc = -1;
        int first = -1, previous = -1, count = 0;
        while (true) {
            byte[] buffer = new byte[2048]; DatagramPacket packet = new DatagramPacket(buffer, buffer.length); receiver.receive(packet);
            int length = packet.getLength(), sequence = (buffer[2] & 255) << 8 | (buffer[3] & 255);
            check(length <= 256 && length > 12 && (buffer[0] & 255) == 128 && (buffer[1] & 127) == 96, "RTP header and MTU");
            if (count++ == 0) { first = sequence; timestamp = u32(buffer, 4); ssrc = u32(buffer, 8); }
            else check(sequence == ((previous + 1) & 65535) && timestamp == u32(buffer, 4) && ssrc == u32(buffer, 8), "one AU has sequential packets and one timestamp/SSRC");
            previous = sequence;
            int type = buffer[12] & 31;
            if (type == 28) {
                boolean start = (buffer[13] & 128) != 0, end = (buffer[13] & 64) != 0;
                if (start) { check(fragment == null, "FU-A starts once"); fragment = new ByteArrayOutputStream(); fragment.write((buffer[12] & 0xe0) | (buffer[13] & 31)); }
                check(fragment != null, "FU-A follows its start fragment");
                fragment.write(buffer, 14, length - 14);
                if (end) { nals.add(fragment.toByteArray()); fragment = null; }
            } else {
                check(fragment == null, "single NAL cannot interrupt FU-A"); nals.add(Arrays.copyOfRange(buffer, 12, length));
            }
            if ((buffer[1] & 128) != 0) {
                check(fragment == null, "marker belongs to a completed access unit");
                return new Received(nals, timestamp, ssrc, first, previous, count);
            }
        }
    }

    private static void same(Received frame, byte[]... expected) {
        check(frame.nals.size() == expected.length, "only the final NAL ends an access unit; expected " + expected.length + " NALs, got " + frame.nals.size());
        for (int i = 0; i < expected.length; i++) check(Arrays.equals(frame.nals.get(i), expected[i]), "NAL " + i + " survives a UDP round trip byte for byte");
    }
    private static long u32(byte[] data, int offset) { long n = 0; for (int i = 0; i < 4; i++) n = (n << 8) | (data[offset + i] & 255); return n; }
    private static byte[] annex(byte[]... units) { ByteArrayOutputStream out = new ByteArrayOutputStream(); for (byte[] nal : units) { out.writeBytes(new byte[]{0, 0, 0, 1}); out.writeBytes(nal); } return out.toByteArray(); }
    private static byte[] concat(byte[]... parts) { ByteArrayOutputStream out = new ByteArrayOutputStream(); for (byte[] part : parts) out.writeBytes(part); return out.toByteArray(); }
}
