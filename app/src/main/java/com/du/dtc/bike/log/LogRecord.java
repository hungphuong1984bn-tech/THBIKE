package com.du.dtc.bike.log;

public class LogRecord {
    public String md5;
    public String hexData;
    public String stringData;
    public long timestamp;

    public LogRecord(String md5, String hexData, String stringData) {
        this.md5 = md5;
        this.hexData = hexData;
        this.stringData = stringData;
        this.timestamp = System.currentTimeMillis();
    }
}