package one.hollander.bnsbinpacker.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BinFileEntry {
    private String filePath;
    private int filePathLength;
    private byte unknown001;
    private boolean compressed;
    private boolean encrypted;
    private byte unknown002;
    private long fileDataSizeUnpacked;
    private long fileDataSizeSheared;
    private long fileDataSizeStored;
    private long fileDataOffset;
    private byte[] padding;
}