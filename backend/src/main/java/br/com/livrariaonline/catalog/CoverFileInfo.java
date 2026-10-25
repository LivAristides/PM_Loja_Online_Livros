package br.com.livrariaonline.catalog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

record CoverFileInfo(String contentType, String extension, int width, int height) {
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    static CoverFileInfo validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new InvalidCoverException();
        if (isPng(bytes)) return png(bytes);
        if (isJpeg(bytes)) return jpeg(bytes);
        if (isWebp(bytes)) return webp(bytes);
        throw new InvalidCoverException();
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length >= 24 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
    }

    private static CoverFileInfo png(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int width = buffer.getInt(16); int height = buffer.getInt(20);
        return dimensions("image/png", ".png", width, height);
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
    }

    private static CoverFileInfo jpeg(byte[] bytes) {
        int index = 2;
        while (index + 9 < bytes.length) {
            if ((bytes[index] & 0xff) != 0xff) throw new InvalidCoverException();
            int marker = bytes[index + 1] & 0xff;
            int length = ((bytes[index + 2] & 0xff) << 8) + (bytes[index + 3] & 0xff);
            if (length < 2 || index + 2 + length > bytes.length) throw new InvalidCoverException();
            if ((marker >= 0xc0 && marker <= 0xc3) || (marker >= 0xc5 && marker <= 0xc7)
                    || (marker >= 0xc9 && marker <= 0xcb) || (marker >= 0xcd && marker <= 0xcf)) {
                int height = ((bytes[index + 5] & 0xff) << 8) + (bytes[index + 6] & 0xff);
                int width = ((bytes[index + 7] & 0xff) << 8) + (bytes[index + 8] & 0xff);
                return dimensions("image/jpeg", ".jpg", width, height);
            }
            index += 2 + length;
        }
        throw new InvalidCoverException();
    }

    private static boolean isWebp(byte[] bytes) {
        return bytes.length >= 16 && new String(bytes, 0, 4).equals("RIFF") && new String(bytes, 8, 4).equals("WEBP");
    }

    private static CoverFileInfo webp(byte[] bytes) {
        int index = 12;
        while (index + 10 <= bytes.length) {
            String type = new String(bytes, index, 4);
            int size = littleEndian(bytes, index + 4, 4);
            int data = index + 8;
            if (size < 0 || data + size > bytes.length) throw new InvalidCoverException();
            if (type.equals("VP8 ") && size >= 10) {
                return dimensions("image/webp", ".webp", littleEndian(bytes, data + 6, 2) & 0x3fff,
                        littleEndian(bytes, data + 8, 2) & 0x3fff);
            }
            if (type.equals("VP8L") && size >= 5) {
                int bits = littleEndian(bytes, data + 1, 4);
                return dimensions("image/webp", ".webp", (bits & 0x3fff) + 1, ((bits >> 14) & 0x3fff) + 1);
            }
            if (type.equals("VP8X") && size >= 10) {
                return dimensions("image/webp", ".webp", littleEndian(bytes, data + 4, 3) + 1,
                        littleEndian(bytes, data + 7, 3) + 1);
            }
            index = data + size + size % 2;
        }
        throw new InvalidCoverException();
    }

    private static int littleEndian(byte[] bytes, int index, int length) {
        int value = 0;
        for (int offset = 0; offset < length; offset++) value |= (bytes[index + offset] & 0xff) << (8 * offset);
        return value;
    }

    private static CoverFileInfo dimensions(String type, String extension, int width, int height) {
        if (width < 1 || height < 1) throw new InvalidCoverException();
        return new CoverFileInfo(type, extension, width, height);
    }

    static final class InvalidCoverException extends IllegalArgumentException {
        InvalidCoverException() { super("A capa deve ser uma imagem JPG, PNG ou WebP de até 2 MB."); }
    }
}
