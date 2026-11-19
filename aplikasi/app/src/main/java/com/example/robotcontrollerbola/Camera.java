package com.example.robotcontrollerbola;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

final class Camera {
    static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;
    private static final int HEADER_BYTES = 8;
    private static final int MAX_FRAGMENTS = 4096;

    private int frameId = -1;
    private byte[][] fragments;
    private int fragmentCount;
    private int receivedFragments;
    private int frameSize;

    byte[] accept(byte[] packet, int packetLength) {
        if (packetLength < 0 || packetLength > packet.length) {
            return null;
        }
        if (packetLength >= 2
                && (packet[0] & 0xFF) == 0xFF
                && (packet[1] & 0xFF) == 0xD8) {
            return packetLength <= MAX_FRAME_BYTES
                    ? Arrays.copyOf(packet, packetLength)
                    : null;
        }
        if (packetLength < HEADER_BYTES) {
            return null;
        }

        int packetFrameId = readUnsignedShort(packet, 0);
        int packetCount = readUnsignedShort(packet, 2);
        int packetIndex = readUnsignedShort(packet, 4);
        int payloadLength = readUnsignedShort(packet, 6);
        if (packetCount < 1
                || packetCount > MAX_FRAGMENTS
                || packetIndex >= packetCount
                || payloadLength != packetLength - HEADER_BYTES
                || payloadLength == 0) {
            return null;
        }

        if (packetFrameId != frameId || packetCount != fragmentCount) {
            beginFrame(packetFrameId, packetCount);
        }
        if (fragments[packetIndex] != null) {
            return null;
        }
        if (frameSize + payloadLength > MAX_FRAME_BYTES) {
            reset();
            return null;
        }

        fragments[packetIndex] = Arrays.copyOfRange(
                packet,
                HEADER_BYTES,
                packetLength
        );
        frameSize += payloadLength;
        receivedFragments++;
        if (receivedFragments != fragmentCount) {
            return null;
        }

        ByteArrayOutputStream frame = new ByteArrayOutputStream(frameSize);
        for (byte[] fragment : fragments) {
            frame.write(fragment, 0, fragment.length);
        }
        byte[] completeFrame = frame.toByteArray();
        reset();
        if (completeFrame.length < 2
                || (completeFrame[0] & 0xFF) != 0xFF
                || (completeFrame[1] & 0xFF) != 0xD8) {
            return null;
        }
        return completeFrame;
    }

    private void beginFrame(int nextFrameId, int nextFragmentCount) {
        frameId = nextFrameId;
        fragmentCount = nextFragmentCount;
        fragments = new byte[nextFragmentCount][];
        receivedFragments = 0;
        frameSize = 0;
    }

    private void reset() {
        frameId = -1;
        fragmentCount = 0;
        fragments = null;
        receivedFragments = 0;
        frameSize = 0;
    }

    private static int readUnsignedShort(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }
}
