package one.hollander.bnsbinpacker.util;

public class XorUtil {
    public static final byte[] XOR_KEY = new byte[]{
            (byte) 164, (byte) 159, (byte) 216, (byte) 179,
            (byte) 246, (byte) 142, (byte) 57, (byte) 194,
            (byte) 45, (byte) 224, (byte) 97, (byte) 117,
            (byte) 92, (byte) 75, (byte) 26, (byte) 7
    };

    public static void xor(byte[] buffer, int size) {
        for (int i = 0; i < size; i++) {
            buffer[i] = (byte) (buffer[i] ^ XOR_KEY[i % XOR_KEY.length]);
        }
    }
}