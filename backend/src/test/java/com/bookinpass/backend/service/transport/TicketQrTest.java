package com.bookinpass.backend.service.transport;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.InflaterOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Le QR code se relit : le PNG de l'e-mail est un vrai PNG, et un lecteur en tire le code du billet. */
class TicketQrTest {

    @Test
    void pngDecodesBackToTheTicketCode() throws Exception {
        byte[] png = TicketQr.png("ABCD234EFG", 6);

        assertThat(Arrays.copyOf(png, 8)).containsExactly(0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n');
        ByteBuffer buffer = ByteBuffer.wrap(png, 8, png.length - 8);
        int side = 0;
        ByteArrayOutputStream pixels = new ByteArrayOutputStream();
        while (buffer.hasRemaining()) {
            int length = buffer.getInt();
            String type = new String(png, buffer.position(), 4, java.nio.charset.StandardCharsets.US_ASCII);
            buffer.position(buffer.position() + 4);
            if (type.equals("IHDR")) {
                side = buffer.getInt(buffer.position());
            } else if (type.equals("IDAT")) {
                try (InflaterOutputStream inflater = new InflaterOutputStream(pixels)) {
                    inflater.write(png, buffer.position(), length);
                }
            }
            buffer.position(buffer.position() + length + 4);
        }
        byte[] raw = pixels.toByteArray();
        assertThat(raw).hasSize(side * (side + 1));
        int[] rgb = new int[side * side];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int grey = raw[y * (side + 1) + 1 + x] & 0xFF;
                rgb[y * side + x] = grey << 16 | grey << 8 | grey;
            }
        }

        String decoded = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(side, side, rgb)))).getText();
        assertThat(decoded).isEqualTo("ABCD234EFG");
    }

    @Test
    void svgPathCoversTheSameModules() {
        TicketQr.Svg svg = TicketQr.svg("ABCD234EFG");

        // Version 1 (21 modules) et 2 modules de marge de chaque côté.
        assertThat(svg.size()).isEqualTo(25);
        assertThat(svg.path()).startsWith("M2 2h7v1h-7z");
    }
}
