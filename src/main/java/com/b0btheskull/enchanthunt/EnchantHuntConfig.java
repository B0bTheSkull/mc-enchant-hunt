package com.b0btheskull.enchanthunt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persisted config for EnchantHunt, stored at &lt;configDir&gt;/enchant-hunt.json via Gson.
 */
public class EnchantHuntConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Enchantment registry id, e.g. "minecraft:efficiency".
    public String targetEnchantId = "";
    public int level = 1;
    public int maxPrice = 64;          // emeralds
    public int cycleDelayTicks = 4;
    public int maxCycles = 1000;

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("enchant-hunt.json");
    }

    public static EnchantHuntConfig load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                EnchantHuntConfig cfg = GSON.fromJson(reader, EnchantHuntConfig.class);
                if (cfg != null) {
                    cfg.sanitize();
                    return cfg;
                }
            } catch (IOException | RuntimeException e) {
                EnchantHunt.LOGGER.warn("Failed to read enchant-hunt.json, using defaults", e);
            }
        }
        EnchantHuntConfig cfg = new EnchantHuntConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        sanitize();
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            EnchantHunt.LOGGER.warn("Failed to write enchant-hunt.json", e);
        }
    }

    private void sanitize() {
        if (targetEnchantId == null) targetEnchantId = "";
        targetEnchantId = targetEnchantId.trim();
        if (level < 1) level = 1;
        if (maxPrice < 1) maxPrice = 1;
        if (maxPrice > 64) maxPrice = 64;
        if (cycleDelayTicks < 1) cycleDelayTicks = 1;
        if (maxCycles < 1) maxCycles = 1;
    }

    public boolean hasValidTarget() {
        return targetEnchantId != null && !targetEnchantId.trim().isEmpty();
    }
}
