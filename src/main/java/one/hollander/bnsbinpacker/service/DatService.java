package one.hollander.bnsbinpacker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

@Slf4j
@Service
public class DatService {

    // C# hardcoded keys
    private static final byte[] AES_KEY = new byte[]{
            23, 81, (byte) 170, (byte) 213, 30, 54, 74, 27, (byte) 254, 96, 116, (byte) 231, (byte) 208, (byte) 133, 7, 104
    };
    private static final byte[] XOR_KEY = new byte[]{
            (byte) 164, (byte) 159, (byte) 216, (byte) 179, (byte) 246, (byte) 142, 57, (byte) 194,
            45, (byte) 224, 97, 117, 92, 75, 26, 7
    };

    public void extractDatafile64Bin(File datFile, File outputFile) throws IOException, GeneralSecurityException {
        try (RandomAccessFile raf = new RandomAccessFile(datFile, "r")) {
            raf.seek(0);

            byte[] signature = new byte[8];
            raf.readFully(signature);
            String signatureStr = new String(signature, StandardCharsets.US_ASCII);

            int version = Integer.reverseBytes(raf.readInt());

            byte[] unknown = new byte[5];
            raf.readFully(unknown);

            long fileDataSizePacked = Long.reverseBytes(raf.readLong());
            long fileCount = Long.reverseBytes(raf.readLong());

            boolean isCompressed = raf.readByte() == 1;
            boolean isEncrypted = raf.readByte() == 1;

            byte[] rsaSignature = new byte[128];
            if (version == 3) {
                raf.readFully(rsaSignature);
            }
            byte[] unknown2 = new byte[62];
            raf.readFully(unknown2);

            long fileTableSizePacked = Long.reverseBytes(raf.readLong());
            long fileTableSizeUnpacked = Long.reverseBytes(raf.readLong());

            byte[] fileTablePacked = new byte[(int) fileTableSizePacked];
            raf.readFully(fileTablePacked);

            long offsetGlobal = Long.reverseBytes(raf.readLong());
            // Do not trust value, get current position:
            offsetGlobal = raf.getFilePointer();

            byte[] fileTableUnpacked = unpack(fileTablePacked, (int) fileTableSizePacked, (int) fileTableSizePacked, (int) fileTableSizeUnpacked, isEncrypted, isCompressed);

            // Read file table entries
            try (DataInputStream fileTableStream = new DataInputStream(new ByteArrayInputStream(fileTableUnpacked))) {
                for (int i = 0; i < fileCount; i++) {
                    int filePathLength = (int) Long.reverseBytes(fileTableStream.readLong());
                    byte[] filePathBytes = new byte[filePathLength * 2];
                    fileTableStream.readFully(filePathBytes);
                    String filePath = new String(filePathBytes, StandardCharsets.UTF_16LE);

                    fileTableStream.readByte(); // unknown_001
                    boolean entryIsCompressed = fileTableStream.readByte() == 1;
                    boolean entryIsEncrypted = fileTableStream.readByte() == 1;
                    fileTableStream.readByte(); // unknown_002

                    int fileDataSizeUnpacked = (int) Long.reverseBytes(fileTableStream.readLong());
                    int fileDataSizeSheared = (int) Long.reverseBytes(fileTableStream.readLong());
                    int fileDataSizeStored = (int) Long.reverseBytes(fileTableStream.readLong());
                    int fileDataOffset = (int) (Long.reverseBytes(fileTableStream.readLong()) + offsetGlobal);

                    byte[] padding = new byte[60];
                    fileTableStream.readFully(padding);

                    if ("datafile64.bin".equals(filePath)) {
                        raf.seek(fileDataOffset);
                        byte[] packed = new byte[fileDataSizeStored];
                        raf.readFully(packed);
                        byte[] unpacked = unpack(packed, fileDataSizeStored, fileDataSizeSheared, fileDataSizeUnpacked, entryIsEncrypted, entryIsCompressed);
                        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                            fos.write(unpacked);
                        }
                        log.info("Extracted and decrypted datafile64.bin to {}", outputFile.getAbsolutePath());
                        return;
                    }
                }
            }
            throw new FileNotFoundException("datafile64.bin not found in dat file!");
        }
    }

    /**
     * Mimics the Unpack method from ashllay/BnsDatTool.
     */
    private byte[] unpack(byte[] buffer, int sizeStored, int sizeSheared, int sizeUnpacked, boolean isEncrypted, boolean isCompressed) throws GeneralSecurityException, IOException {
        byte[] output = buffer;

        if (isEncrypted) {
            output = decrypt(output, sizeStored);
        }
        if (isCompressed) {
            output = deflate(output, sizeSheared, sizeUnpacked);
        }
        if (output == buffer) {
            output = new byte[sizeUnpacked];
            if (sizeSheared < sizeUnpacked) {
                System.arraycopy(buffer, 0, output, 0, sizeSheared);
            } else {
                System.arraycopy(buffer, 0, output, 0, sizeUnpacked);
            }
        }
        return output;
    }

    /**
     * AES decryption in ECB mode, as in the C# tool.
     */
    private byte[] decrypt(byte[] buffer, int size) throws GeneralSecurityException {
        int AES_BLOCK_SIZE = AES_KEY.length;
        int sizePadded = size + (AES_BLOCK_SIZE - (size % AES_BLOCK_SIZE)) % AES_BLOCK_SIZE;
        byte[] tmp = new byte[sizePadded];
        System.arraycopy(buffer, 0, tmp, 0, size);

        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decrypted = cipher.doFinal(tmp);

        return Arrays.copyOf(decrypted, size);
    }

    /**
     * Decompress using zlib (as in Ionic.Zlib.ZlibStream.UncompressBuffer).
     */
    private byte[] deflate(byte[] buffer, int sizeCompressed, int sizeDecompressed) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(buffer, 0, sizeCompressed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(sizeDecompressed);
        try (java.util.zip.InflaterInputStream inflater = new java.util.zip.InflaterInputStream(bais)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = inflater.read(buf)) > 0) {
                baos.write(buf, 0, n);
            }
        }
        byte[] tmp = baos.toByteArray();
        if (tmp.length != sizeDecompressed) {
            byte[] resized = new byte[sizeDecompressed];
            System.arraycopy(tmp, 0, resized, 0, Math.min(tmp.length, sizeDecompressed));
            return resized;
        }
        return tmp;
    }
}