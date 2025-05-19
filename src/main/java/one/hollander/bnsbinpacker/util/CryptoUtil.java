package one.hollander.bnsbinpacker.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class CryptoUtil {
    public static final byte[] AES_KEY = "BNSR#XOR@Encrypt$Key".getBytes(StandardCharsets.US_ASCII);

    public static byte[] aesDecrypt(byte[] input, int size) throws Exception {
        int blockSize = AES_KEY.length;
        int paddedSize = ((size + blockSize - 1) / blockSize) * blockSize;
        byte[] inputPadded = Arrays.copyOf(input, paddedSize);

        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        SecretKeySpec secretKey = new SecretKeySpec(AES_KEY, "AES");
        byte[] decrypted = cipher.doFinal(inputPadded);

        return Arrays.copyOf(decrypted, size);
    }
}