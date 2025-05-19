package one.hollander.bnsbinpacker.service;

import lombok.extern.slf4j.Slf4j;
import one.hollander.bnsbinpacker.domain.BinFileEntry;
import one.hollander.bnsbinpacker.domain.BinHeader;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

@Slf4j
@Service
public class BinService {

    public void unpackBin(File binFile, File outputDir, boolean is64) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(binFile, "r")) {
            BinHeader header = readHeader(raf, is64);
            System.out.println("Signature: " + header.getSignature());
            System.out.println("File count: " + header.getFileCount());
            System.out.println("fileTableSizePacked: " + header.getFileTableSizePacked());
            System.out.println("fileTableSizeUnpacked: " + header.getFileTableSizeUnpacked());
            System.out.println("isCompressed: " + header.isCompressed());

            byte[] packedTable = new byte[(int) header.getFileTableSizePacked()];
            raf.readFully(packedTable);

            byte[] fileTable;
            if (header.isCompressed()) {
                fileTable = decompressZlib(packedTable, (int) header.getFileTableSizeUnpacked());
            } else {
                fileTable = packedTable;
            }

            List<BinFileEntry> entries = parseFileTable(fileTable, header.getFileCount(), is64, header.getOffsetGlobal());

            for (BinFileEntry entry : entries) {
                System.out.printf("Writing file %s%n", outputDir + entry.getFilePath());
                File entryOutFile = new File(outputDir, entry.getFilePath());
                File parent = entryOutFile.getParentFile();
                if (!parent.exists()) parent.mkdirs();

                raf.seek(entry.getFileDataOffset());
                byte[] packedData = new byte[(int) entry.getFileDataSizeStored()];
                raf.readFully(packedData);

                byte[] unpackedData;
                if (entry.isCompressed()) {
                    unpackedData = decompressZlib(packedData, (int) entry.getFileDataSizeUnpacked());
                } else {
                    unpackedData = packedData;
                }

                try (FileOutputStream fos = new FileOutputStream(entryOutFile)) {
                    fos.write(unpackedData);
                }
            }
        }
    }

    public void repackBin(File inputDir, File binFile, boolean is64) throws IOException {
        List<File> files = new ArrayList<>();
        collectFiles(inputDir, files, inputDir);

        List<BinFileEntry> entries = new ArrayList<>();
        ByteArrayOutputStream tableStream = new ByteArrayOutputStream();
        List<byte[]> compressedFileData = new ArrayList<>();
        long fileDataOffset = 0;

        // Prepare file table and gather compressed file data
        for (File file : files) {
            String relativePath = inputDir.toPath().relativize(file.toPath()).toString().replace("\\", "/");
            byte[] filePathBytes = relativePath.getBytes(StandardCharsets.UTF_16LE);
            int filePathLength = filePathBytes.length / 2;

            BinFileEntry entry = new BinFileEntry();
            entry.setFilePath(relativePath);
            entry.setFilePathLength(filePathLength);
            entry.setUnknown001((byte) 2);         // always 2 as in C#
            entry.setCompressed(true);              // always true as in C#
            entry.setEncrypted(true);               // always true as in C#
            entry.setUnknown002((byte) 0);         // always 0 as in C#

            byte[] rawData = readFile(file);
            byte[] storedData = compressZlib(rawData); // always compress with zlib/deflate

            entry.setFileDataSizeUnpacked(rawData.length);
            entry.setFileDataSizeSheared(rawData.length);
            entry.setFileDataSizeStored(storedData.length);
            entry.setPadding(new byte[60]);
            entry.setFileDataOffset(fileDataOffset);

            DataOutputStream entryOut = new DataOutputStream(tableStream);
            if (is64) {
                entryOut.writeLong(Long.reverseBytes(filePathLength));
            } else {
                entryOut.writeInt(Integer.reverseBytes(filePathLength));
            }
            entryOut.write(filePathBytes);
            entryOut.writeByte(entry.getUnknown001());
            entryOut.writeByte(1); // isCompressed always 1
            entryOut.writeByte(1); // isEncrypted always 1
            entryOut.writeByte(entry.getUnknown002());
            if (is64) {
                entryOut.writeLong(Long.reverseBytes(entry.getFileDataSizeUnpacked()));
                entryOut.writeLong(Long.reverseBytes(entry.getFileDataSizeSheared()));
                entryOut.writeLong(Long.reverseBytes(entry.getFileDataSizeStored()));
                entryOut.writeLong(Long.reverseBytes(entry.getFileDataOffset()));
            } else {
                entryOut.writeInt(Integer.reverseBytes((int) entry.getFileDataSizeUnpacked()));
                entryOut.writeInt(Integer.reverseBytes((int) entry.getFileDataSizeSheared()));
                entryOut.writeInt(Integer.reverseBytes((int) entry.getFileDataSizeStored()));
                entryOut.writeInt(Integer.reverseBytes((int) entry.getFileDataOffset()));
            }
            entryOut.write(entry.getPadding());
            entries.add(entry);

            compressedFileData.add(storedData);
            fileDataOffset += storedData.length;
        }

        byte[] fileTableBytesRaw = tableStream.toByteArray();
        byte[] fileTableBytes = compressZlib(fileTableBytesRaw); // always compress the file table
        long fileTableSizePacked = fileTableBytes.length;
        long fileTableSizeUnpacked = fileTableBytesRaw.length;

        try (RandomAccessFile raf = new RandomAccessFile(binFile, "rw")) {
            raf.write("TADBOSLB".getBytes(StandardCharsets.US_ASCII));
            raf.writeInt(Integer.reverseBytes(2)); // version 2
            raf.write(new byte[5]); // unknown001
            if (is64) {
                raf.writeLong(Long.reverseBytes(entries.stream().mapToLong(BinFileEntry::getFileDataSizeStored).sum()));
                raf.writeLong(Long.reverseBytes(entries.size()));
            } else {
                raf.writeInt(Integer.reverseBytes((int) entries.stream().mapToLong(BinFileEntry::getFileDataSizeStored).sum()));
                raf.writeInt(Integer.reverseBytes(entries.size()));
            }
            raf.writeByte(1); // isCompressed always 1
            raf.writeByte(1); // isEncrypted always 1
            // (No RSA for version 2; add 128 bytes if you use version 3)
            raf.write(new byte[62]); // unknown002
            if (is64) {
                raf.writeLong(Long.reverseBytes(fileTableSizePacked));
                raf.writeLong(Long.reverseBytes(fileTableSizeUnpacked));
            } else {
                raf.writeInt(Integer.reverseBytes((int) fileTableSizePacked));
                raf.writeInt(Integer.reverseBytes((int) fileTableSizeUnpacked));
            }
            long offsetGlobalValue = raf.getFilePointer() + (is64 ? 8 : 4); // after file table size fields
            if (is64) raf.writeLong(Long.reverseBytes(offsetGlobalValue));
            else raf.writeInt(Integer.reverseBytes((int) offsetGlobalValue));

            // Write file table (already compressed)
            raf.write(fileTableBytes);

            // Write file data
            for (byte[] storedData : compressedFileData) {
                raf.write(storedData);
            }
        }
    }

    private BinHeader readHeader(RandomAccessFile raf, boolean is64) throws IOException {
        BinHeader header = new BinHeader();
        byte[] sig = new byte[8];
        raf.readFully(sig);
        header.setSignature(new String(sig, StandardCharsets.US_ASCII));
        header.setVersion(Integer.reverseBytes(raf.readInt()));
        byte[] unknown001 = new byte[5];
        raf.readFully(unknown001);
        header.setUnknown001(unknown001);

        if (is64) {
            header.setFileDataSizePacked(Long.reverseBytes(raf.readLong()));
            header.setFileCount(Long.reverseBytes(raf.readLong()));
        } else {
            header.setFileDataSizePacked(Integer.reverseBytes(raf.readInt()) & 0xFFFFFFFFL);
            header.setFileCount(Integer.reverseBytes(raf.readInt()) & 0xFFFFFFFFL);
        }
        header.setCompressed(raf.readByte() == 1);
        header.setEncrypted(raf.readByte() == 1);

        if (header.getVersion() == 3) {
            byte[] rsa = new byte[128];
            raf.readFully(rsa);
            header.setRsaSignature(rsa);
        }

        byte[] unknown002 = new byte[62];
        raf.readFully(unknown002);
        header.setUnknown002(unknown002);

        if (is64) {
            header.setFileTableSizePacked(Long.reverseBytes(raf.readLong()));
            header.setFileTableSizeUnpacked(Long.reverseBytes(raf.readLong()));
        } else {
            header.setFileTableSizePacked(Integer.reverseBytes(raf.readInt()) & 0xFFFFFFFFL);
            header.setFileTableSizeUnpacked(Integer.reverseBytes(raf.readInt()) & 0xFFFFFFFFL);
        }

        if (is64) {
            /* This value is ignored, use current file pointer instead */
            raf.readLong();
        } else {
            raf.readInt();
        }
        header.setOffsetGlobal(raf.getFilePointer());
        return header;
    }

    private List<BinFileEntry> parseFileTable(byte[] fileTable, long fileCount, boolean is64, long offsetGlobal) throws IOException {
        List<BinFileEntry> entries = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(fileTable))) {
            for (int i = 0; i < fileCount; i++) {
                BinFileEntry entry = new BinFileEntry();
                int filePathLength = is64 ? (int) Long.reverseBytes(in.readLong()) : Integer.reverseBytes(in.readInt());
                byte[] filePathBytes = new byte[filePathLength * 2];
                in.readFully(filePathBytes);
                entry.setFilePath(new String(filePathBytes, StandardCharsets.UTF_16LE));
                entry.setFilePathLength(filePathLength);
                entry.setUnknown001(in.readByte());
                entry.setCompressed(in.readByte() == 1);
                entry.setEncrypted(in.readByte() == 1);
                entry.setUnknown002(in.readByte());
                entry.setFileDataSizeUnpacked(is64 ? Long.reverseBytes(in.readLong()) : Integer.reverseBytes(in.readInt()) & 0xFFFFFFFFL);
                entry.setFileDataSizeSheared(is64 ? Long.reverseBytes(in.readLong()) : Integer.reverseBytes(in.readInt()) & 0xFFFFFFFFL);
                entry.setFileDataSizeStored(is64 ? Long.reverseBytes(in.readLong()) : Integer.reverseBytes(in.readInt()) & 0xFFFFFFFFL);
                entry.setFileDataOffset((is64 ? Long.reverseBytes(in.readLong()) : Integer.reverseBytes(in.readInt()) & 0xFFFFFFFFL) + offsetGlobal);
                byte[] padding = new byte[60];
                in.readFully(padding);
                entry.setPadding(padding);
                entries.add(entry);
            }
        }
        return entries;
    }

    private void collectFiles(File dir, List<File> files, File root) {
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.isDirectory()) {
                collectFiles(file, files, root);
            } else {
                files.add(file);
            }
        }
    }

    private byte[] readFile(File file) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        }
    }

    private byte[] compressZlib(byte[] src) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DeflaterOutputStream dos = new DeflaterOutputStream(bos, new Deflater(Deflater.BEST_COMPRESSION))) {
            dos.write(src);
        }
        return bos.toByteArray();
    }

    private byte[] decompressZlib(byte[] compressed, int uncompressedLen) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (java.util.zip.InflaterInputStream iis = new java.util.zip.InflaterInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buf = new byte[8192];
            int read, total = 0;
            while ((read = iis.read(buf)) != -1 && total < uncompressedLen) {
                bos.write(buf, 0, read);
                total += read;
            }
        }
        return bos.toByteArray();
    }
}