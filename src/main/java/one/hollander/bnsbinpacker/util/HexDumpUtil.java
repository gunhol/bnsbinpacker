package one.hollander.bnsbinpacker.util;

public class HexDumpUtil {
    /**
     * Prints a hex dump of the given byte array to System.out, grouped by 16 bytes per line.
     */
    public static void printHexDump(byte[] data, int maxBytes) {
        int length = Math.min(data.length, maxBytes);
        for (int i = 0; i < length; i += 16) {
            System.out.printf("%04X  ", i);
            // Print hex part
            for (int j = 0; j < 16; j++) {
                if (i + j < length) {
                    System.out.printf("%02X ", data[i + j]);
                } else {
                    System.out.print("   ");
                }
            }
            System.out.print(" ");
            // Print ASCII part
            for (int j = 0; j < 16; j++) {
                if (i + j < length) {
                    int b = data[i + j] & 0xFF;
                    System.out.print((b >= 32 && b <= 126) ? (char) b : '.');
                }
            }
            System.out.println();
        }
    }

    /**
     * Converts a range of a byte array to a space-separated hex string, for logging.
     *
     * @param data   The byte array
     * @param offset Start index (inclusive)
     * @param length Number of bytes to convert
     * @return Hex string (e.g. "4F 6B 20 01")
     */
    public static String toHex(byte[] data, int offset, int length) {
        if (data == null || data.length == 0 || length <= 0 || offset >= data.length) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int end = Math.min(data.length, offset + length);
        for (int i = offset; i < end; i++) {
            sb.append(String.format("%02X", data[i]));
            if (i < end - 1) sb.append(" ");
        }
        return sb.toString();
    }
}