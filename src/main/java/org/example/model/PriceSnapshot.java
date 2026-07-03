package org.example.model;

import java.time.LocalDateTime;

/** A single historical price observation for a skin on a platform. */
public class PriceSnapshot {

    private Long id;
    private Long skinId;
    private Platform platform;
    private Integer priceUsdCents;
    private Double floatValue;
    private LocalDateTime recordedAt;

    public PriceSnapshot() {}

    public PriceSnapshot(Long skinId, Platform platform, Integer priceUsdCents, Double floatValue) {
        this.skinId = skinId;
        this.platform = platform;
        this.priceUsdCents = priceUsdCents;
        this.floatValue = floatValue;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSkinId() { return skinId; }
    public void setSkinId(Long skinId) { this.skinId = skinId; }

    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }

    public Integer getPriceUsdCents() { return priceUsdCents; }
    public void setPriceUsdCents(Integer priceUsdCents) { this.priceUsdCents = priceUsdCents; }

    public Double getFloatValue() { return floatValue; }
    public void setFloatValue(Double floatValue) { this.floatValue = floatValue; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
