package com.example.robotcontrollerbola;

import org.junit.Test;

import static org.junit.Assert.*;

public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void gerakanMendatarStikKananMengubahWz() {
        assertEquals(0.7f, MainActivity.sumbuPutaran(0.7f, 0.1f), 0.001f);
    }

    @Test
    public void gerakanTegakStikKananJugaMengubahWz() {
        assertEquals(0.8f, MainActivity.sumbuPutaran(0, -0.8f), 0.001f);
    }

    @Test
    public void sumbuGamepadAlternatifMengubahWzKeDuaArah() {
        assertEquals(-1, MainActivity.sumbuPutaranTambahan(1, 0), 0.001f);
        assertEquals(1, MainActivity.sumbuPutaranTambahan(0, 1), 0.001f);
    }

    @Test
    public void sumbuGamepadAlternatifNetralTidakMemutar() {
        assertEquals(0, MainActivity.sumbuPutaranTambahan(0.5f, 0.5f), 0.001f);
    }

    @Test
    public void frameJpegSatuDatagramDiteruskanUtuh() {
        CameraFrameAssembler assembler = new CameraFrameAssembler();
        byte[] frame = {(byte) 0xFF, (byte) 0xD8, 1, 2};

        assertArrayEquals(frame, assembler.accept(frame, frame.length));
    }

    @Test
    public void fragmenJpegDisusunMenurutNomorPaket() {
        CameraFrameAssembler assembler = new CameraFrameAssembler();
        byte[] fragmenKedua = paketFragmen(7, 2, 1, new byte[]{3, 4});
        byte[] fragmenPertama = paketFragmen(
                7,
                2,
                0,
                new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2}
        );

        assertNull(assembler.accept(fragmenKedua, fragmenKedua.length));
        assertArrayEquals(
                new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3, 4},
                assembler.accept(fragmenPertama, fragmenPertama.length)
        );
    }

    @Test
    public void paketFragmenDenganUkuranPayloadKeliruDitolak() {
        CameraFrameAssembler assembler = new CameraFrameAssembler();
        byte[] packet = paketFragmen(1, 1, 0, new byte[]{1, 2});
        packet[7] = 3;

        assertNull(assembler.accept(packet, packet.length));
    }

    private static byte[] paketFragmen(
            int frameId,
            int packetCount,
            int packetIndex,
            byte[] payload
    ) {
        byte[] packet = new byte[8 + payload.length];
        putUnsignedShort(packet, 0, frameId);
        putUnsignedShort(packet, 2, packetCount);
        putUnsignedShort(packet, 4, packetIndex);
        putUnsignedShort(packet, 6, payload.length);
        System.arraycopy(payload, 0, packet, 8, payload.length);
        return packet;
    }

    private static void putUnsignedShort(byte[] packet, int offset, int value) {
        packet[offset] = (byte) (value >>> 8);
        packet[offset + 1] = (byte) value;
    }
}