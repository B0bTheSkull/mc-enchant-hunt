package com.b0btheskull.enchanthunt;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public class EnchantHuntModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            EnchantHuntConfig cfg = EnchantHunt.config;

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Component.literal("EnchantHunt"));

            ConfigEntryBuilder entry = builder.entryBuilder();
            ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

            general.addEntry(entry.startTextDescription(Component.literal(
                            "Enter an enchantment registry id, e.g. minecraft:efficiency"))
                    .build());

            // Enchant id: validated text field (fallback to text per spec; cloth-config
            // has no clean enchantment-id dropdown selector here).
            general.addEntry(entry.startStrField(Component.literal("Target Enchant Id"), cfg.targetEnchantId)
                    .setDefaultValue("minecraft:efficiency")
                    .setTooltip(Component.literal("An enchantment registry id like minecraft:efficiency"))
                    .setErrorSupplier(EnchantHuntModMenu::validateEnchantId)
                    .setSaveConsumer(v -> cfg.targetEnchantId = v == null ? "" : v.trim())
                    .build());

            general.addEntry(entry.startIntField(Component.literal("Level"), cfg.level)
                    .setDefaultValue(1)
                    .setMin(1)
                    .setMax(10)
                    .setTooltip(Component.literal("Exact enchantment level to match"))
                    .setSaveConsumer(v -> cfg.level = v)
                    .build());

            general.addEntry(entry.startIntSlider(Component.literal("Max Price (emeralds)"), cfg.maxPrice, 1, 64)
                    .setDefaultValue(64)
                    .setTooltip(Component.literal("Stop when the book costs at most this many emeralds"))
                    .setSaveConsumer(v -> cfg.maxPrice = v)
                    .build());

            general.addEntry(entry.startIntField(Component.literal("Cycle Delay (ticks)"), cfg.cycleDelayTicks)
                    .setDefaultValue(4)
                    .setMin(1)
                    .setMax(200)
                    .setTooltip(Component.literal("Ticks between trade-cycle attempts (20 ticks = 1s)"))
                    .setSaveConsumer(v -> cfg.cycleDelayTicks = v)
                    .build());

            general.addEntry(entry.startIntField(Component.literal("Max Cycles"), cfg.maxCycles)
                    .setDefaultValue(1000)
                    .setMin(1)
                    .setMax(100000)
                    .setTooltip(Component.literal("Give up after this many cycles"))
                    .setSaveConsumer(v -> cfg.maxCycles = v)
                    .build());

            builder.setSavingRunnable(cfg::save);
            return builder.build();
        };
    }

    private static Optional<Component> validateEnchantId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.of(Component.literal("Enter an enchantment id"));
        }
        Identifier id;
        try {
            id = Identifier.parse(value.trim());
        } catch (RuntimeException e) {
            return Optional.of(Component.literal("Not a valid id (use namespace:path)"));
        }
        // If we're in a world, verify the enchantment actually exists.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            boolean exists = mc.player.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .get(id)
                    .isPresent();
            if (!exists) {
                return Optional.of(Component.literal("No such enchantment in this world"));
            }
        }
        return Optional.empty();
    }
}
