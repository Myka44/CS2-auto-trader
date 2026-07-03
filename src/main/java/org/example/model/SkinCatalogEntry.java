package org.example.model;

/** A single (weapon + skin + wear) entry from the CS2 skin catalog. */
public class SkinCatalogEntry {

    private Long id;
    private String marketHashName;
    private String weapon;
    private String skinName;
    private String wear;
    private Double floatMin;
    private Double floatMax;
    private Integer defIndex;
    private Integer paintIndex;
    private String imageUrl;
    private String rarity;
    private String collection;

    public SkinCatalogEntry() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMarketHashName() { return marketHashName; }
    public void setMarketHashName(String marketHashName) { this.marketHashName = marketHashName; }

    public String getWeapon() { return weapon; }
    public void setWeapon(String weapon) { this.weapon = weapon; }

    public String getSkinName() { return skinName; }
    public void setSkinName(String skinName) { this.skinName = skinName; }

    public String getWear() { return wear; }
    public void setWear(String wear) { this.wear = wear; }

    public Double getFloatMin() { return floatMin; }
    public void setFloatMin(Double floatMin) { this.floatMin = floatMin; }

    public Double getFloatMax() { return floatMax; }
    public void setFloatMax(Double floatMax) { this.floatMax = floatMax; }

    public Integer getDefIndex() { return defIndex; }
    public void setDefIndex(Integer defIndex) { this.defIndex = defIndex; }

    public Integer getPaintIndex() { return paintIndex; }
    public void setPaintIndex(Integer paintIndex) { this.paintIndex = paintIndex; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }

    public String getCollection() { return collection; }
    public void setCollection(String collection) { this.collection = collection; }

    @Override
    public String toString() {
        return marketHashName;
    }
}
