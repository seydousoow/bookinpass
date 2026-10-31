package com.bookinpass.backend.service.transport;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.jspecify.annotations.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * QR code d'un billet de car : il contient le code du billet, tel que le contrôleur le saisirait. Dessiné côté serveur,
 * une seule source pour la page des billets (tracé SVG) et l'e-mail (PNG intégré). Sans AWT ni {@code ImageIO} :
 * l'image native n'en a pas besoin.
 */
public final class TicketQr {

    /**
     * Correction M (15 %) : lisible sur un écran fissuré ou une impression froissée. Marge : 2 modules.
     */
    private static final Map<EncodeHintType, Object> HINTS = Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN, 2);

    private TicketQr() {
    }

    public static Svg svg(String code) {
        BitMatrix matrix = matrix(code);
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < matrix.getHeight(); y++) {
            int x = 0;
            while (x < matrix.getWidth()) {
                if (!matrix.get(x, y)) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < matrix.getWidth() && matrix.get(x, y)) {
                    x++;
                }
                path.append('M').append(start).append(' ').append(y).append('h').append(x - start).append("v1h-").append(x - start).append('z');
            }
        }
        return new Svg(matrix.getWidth(), path.toString());
    }

    /**
     * PNG en niveaux de gris, {@code scale} pixels par module (noir sur blanc).
     */
    public static byte[] png(String code, int scale) {
        BitMatrix matrix = matrix(code);
        int side = matrix.getWidth() * scale;
        // Une ligne = un octet de filtre (0 : aucun) puis un octet par pixel.
        ByteArrayOutputStream compressed = getCompressed(scale, side, matrix);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        // IHDR : largeur, hauteur, 8 bits par pixel, niveaux de gris, compression, filtre et entrelacement standard.
        chunk(png, "IHDR", ByteBuffer.allocate(13).putInt(side).putInt(side).put((byte) 8).put((byte) 0).put((byte) 0)
                .put((byte) 0).put((byte) 0).array());
        chunk(png, "IDAT", compressed.toByteArray());
        chunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static @NonNull ByteArrayOutputStream getCompressed(int scale, int side, BitMatrix matrix) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(side * (side + 1));
        for (int y = 0; y < side; y++) {
            raw.write(0);
            for (int x = 0; x < side; x++) {
                raw.write(matrix.get(x / scale, y / scale) ? 0x00 : 0xFF);
            }
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream dlt = new DeflaterOutputStream(compressed)) {
            raw.writeTo(dlt);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return compressed;
    }

    private static BitMatrix matrix(String code) {
        try {
            return new QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 0, 0, HINTS);
        } catch (WriterException e) {
            throw new IllegalArgumentException("Ticket code cannot be encoded as a QR code", e);
        }
    }

    private static void chunk(ByteArrayOutputStream png, String type, byte[] data) {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        png.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        png.writeBytes(name);
        png.writeBytes(data);
        png.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    /**
     * Tracé SVG du QR code, à poser dans {@code <svg viewBox="0 0 size size"><path d="…"/></svg>} : un rectangle par
     * suite de modules noirs d'une même ligne.
     *
     * @param size côté en modules, marges comprises
     */
    public record Svg(int size, String path) {
    }
}
