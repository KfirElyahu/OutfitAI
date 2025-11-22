package com.kfir.outfitai;

public class HistoryItem {
    private int id;
    private String personUri;
    private String clothUri;
    private String resultUris; // Comma separated
    private long timestamp;

    public HistoryItem() {}

    public HistoryItem(String personUri, String clothUri, String resultUris) {
        this.personUri = personUri;
        this.clothUri = clothUri;
        this.resultUris = resultUris;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getPersonUri() { return personUri; }
    public void setPersonUri(String personUri) { this.personUri = personUri; }

    public String getClothUri() { return clothUri; }
    public void setClothUri(String clothUri) { this.clothUri = clothUri; }

    public String getResultUris() { return resultUris; }
    public void setResultUris(String resultUris) { this.resultUris = resultUris; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}