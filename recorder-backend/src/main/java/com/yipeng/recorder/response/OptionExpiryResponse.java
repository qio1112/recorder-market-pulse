package com.yipeng.recorder.response;

public class OptionExpiryResponse {

    private String expiry;
    private Boolean expired;

    public OptionExpiryResponse() {
    }

    public OptionExpiryResponse(String expiry, Boolean expired) {
        this.expiry = expiry;
        this.expired = expired;
    }

    public String getExpiry() {
        return expiry;
    }

    public void setExpiry(String expiry) {
        this.expiry = expiry;
    }

    public Boolean getExpired() {
        return expired;
    }

    public void setExpired(Boolean expired) {
        this.expired = expired;
    }
}
