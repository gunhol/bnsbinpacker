package one.hollander.bnsbinpacker.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BinHeader {
    private String signature;
    private int version;
    private byte[] unknown001;
    private long fileDataSizePacked;
    private long fileCount;
    private boolean compressed;
    private boolean encrypted;
    private byte[] rsaSignature;
    private byte[] unknown002;
    private long fileTableSizePacked;
    private long fileTableSizeUnpacked;
    private long offsetGlobal;
}