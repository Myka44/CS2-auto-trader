package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.SkinCatalogEntry;
import org.example.repository.SkinRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Loads the bundled CS2 skin catalog (sourced from the ByMykel/CSGO-API
 * community dataset) into the local database on first launch. The seed file
 * ships inside the app jar as a gzip-compressed JSON resource so the
 * download/repo size stays small while still covering ~8,800 skin+wear
 * combinations out of the box.
 */
public class SkinDataService {

    private static final Logger log = LoggerFactory.getLogger(SkinDataService.class);
    private static final String SEED_RESOURCE = "/seed/skin_catalog_seed.json.gz";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SkinRepository skinRepository;

    public SkinDataService(SkinRepository skinRepository) {
        this.skinRepository = skinRepository;
    }

    /** Loads the seed catalog into the database if it's currently empty. Safe to call on every startup. */
    public void loadSeedIfEmpty() {
        long existing = skinRepository.count();
        if (existing > 0) {
            log.info("Skin catalog already populated ({} entries), skipping seed load.", existing);
            return;
        }
        log.info("Skin catalog is empty -- loading bundled seed data...");
        List<SkinCatalogEntry> entries = readSeedEntries();
        skinRepository.insertBatch(entries);
        log.info("Loaded {} skin catalog entries from bundled seed.", entries.size());
    }

    @SuppressWarnings("unchecked")
    private List<SkinCatalogEntry> readSeedEntries() {
        try (InputStream raw = SkinDataService.class.getResourceAsStream(SEED_RESOURCE);
             GZIPInputStream gzip = new GZIPInputStream(raw)) {
            List<Map<String, Object>> rows = MAPPER.readValue(gzip, new TypeReference<List<Map<String, Object>>>() {});
            return rows.stream().map(this::toEntry).toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read bundled skin seed data from " + SEED_RESOURCE, e);
        }
    }

    private SkinCatalogEntry toEntry(Map<String, Object> row) {
        SkinCatalogEntry e = new SkinCatalogEntry();
        e.setMarketHashName((String) row.get("market_hash_name"));
        e.setWeapon((String) row.get("weapon"));
        e.setSkinName((String) row.get("skin_name"));
        e.setWear((String) row.get("wear"));
        e.setFloatMin(asDouble(row.get("float_min")));
        e.setFloatMax(asDouble(row.get("float_max")));
        e.setDefIndex(asInt(row.get("def_index")));
        e.setPaintIndex(asInt(row.get("paint_index")));
        e.setImageUrl((String) row.get("image_url"));
        e.setRarity((String) row.get("rarity"));
        e.setCollection((String) row.get("collection"));
        return e;
    }

    private Double asDouble(Object o) {
        return o == null ? null : ((Number) o).doubleValue();
    }

    private Integer asInt(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }
}
