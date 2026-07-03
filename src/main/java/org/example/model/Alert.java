package org.example.model;

import java.time.LocalDateTime;

/**
 * A notification-only price watch. Unlike Target, an Alert never places a buy
 * order on a platform -- it just fires a desktop notification when the lowest
 * matching offer price crosses the threshold.
 */
public class Alert {

    public enum Direction { AT_OR_BELOW, AT_OR_ABOVE }

    private Long id;
    private Long skinId;
    private String skinMarketHashName;
    private Platform platform; // null = check all platforms
    private int thresholdUsdCents;
    private Direction direction = Direction.AT_OR_BELOW;
    private Double floatRangeMin;
    private Double floatRangeMax;
    private String wearCondition;
    private int cooldownMinutes = 60;
    private LocalDateTime triggeredAt;
    private Integer lastSeenPriceCents;
    private boolean active = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Alert() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSkinId() { return skinId; }
    public void setSkinId(Long skinId) { this.skinId = skinId; }

    public String getSkinMarketHashName() { return skinMarketHashName; }
    public void setSkinMarketHashName(String skinMarketHashName) { this.skinMarketHashName = skinMarketHashName; }

    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }

    public int getThresholdUsdCents() { return thresholdUsdCents; }
    public void setThresholdUsdCents(int thresholdUsdCents) { this.thresholdUsdCents = thresholdUsdCents; }

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }

    public Double getFloatRangeMin() { return floatRangeMin; }
    public void setFloatRangeMin(Double floatRangeMin) { this.floatRangeMin = floatRangeMin; }

    public Double getFloatRangeMax() { return floatRangeMax; }
    public void setFloatRangeMax(Double floatRangeMax) { this.floatRangeMax = floatRangeMax; }

    public String getWearCondition() { return wearCondition; }
    public void setWearCondition(String wearCondition) { this.wearCondition = wearCondition; }

    public int getCooldownMinutes() { return cooldownMinutes; }
    public void setCooldownMinutes(int cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; }

    public LocalDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(LocalDateTime triggeredAt) { this.triggeredAt = triggeredAt; }

    public Integer getLastSeenPriceCents() { return lastSeenPriceCents; }
    public void setLastSeenPriceCents(Integer lastSeenPriceCents) { this.lastSeenPriceCents = lastSeenPriceCents; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
