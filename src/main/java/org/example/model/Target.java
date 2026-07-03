package org.example.model;

import java.time.LocalDateTime;

/** A user-defined buy target for a skin on a specific platform. */
public class Target {

    private Long id;
    private Long skinId;
    private String skinMarketHashName; // denormalized for display convenience after joins
    private Platform platform;
    private String platformTargetId;
    private int maxPriceUsdCents;
    private int priceModifierCents = 1;
    private Double floatRangeMin;
    private Double floatRangeMax;
    private String floatPartValue;
    private int quantity = 10;
    private boolean autoAdjust = true;
    private boolean active = true;
    private Integer lastPriceCents;
    private String lastCheckedAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Target() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSkinId() { return skinId; }
    public void setSkinId(Long skinId) { this.skinId = skinId; }

    public String getSkinMarketHashName() { return skinMarketHashName; }
    public void setSkinMarketHashName(String skinMarketHashName) { this.skinMarketHashName = skinMarketHashName; }

    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }

    public String getPlatformTargetId() { return platformTargetId; }
    public void setPlatformTargetId(String platformTargetId) { this.platformTargetId = platformTargetId; }

    public int getMaxPriceUsdCents() { return maxPriceUsdCents; }
    public void setMaxPriceUsdCents(int maxPriceUsdCents) { this.maxPriceUsdCents = maxPriceUsdCents; }

    public int getPriceModifierCents() { return priceModifierCents; }
    public void setPriceModifierCents(int priceModifierCents) { this.priceModifierCents = priceModifierCents; }

    public Double getFloatRangeMin() { return floatRangeMin; }
    public void setFloatRangeMin(Double floatRangeMin) { this.floatRangeMin = floatRangeMin; }

    public Double getFloatRangeMax() { return floatRangeMax; }
    public void setFloatRangeMax(Double floatRangeMax) { this.floatRangeMax = floatRangeMax; }

    public String getFloatPartValue() { return floatPartValue; }
    public void setFloatPartValue(String floatPartValue) { this.floatPartValue = floatPartValue; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public boolean isAutoAdjust() { return autoAdjust; }
    public void setAutoAdjust(boolean autoAdjust) { this.autoAdjust = autoAdjust; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getLastPriceCents() { return lastPriceCents; }
    public void setLastPriceCents(Integer lastPriceCents) { this.lastPriceCents = lastPriceCents; }

    public String getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(String lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
