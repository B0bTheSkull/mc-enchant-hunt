package com.b0btheskull.enchanthunt;

import com.mojang.blaze3d.platform.InputConstants;
import de.maxhenkel.tradecycling.gui.CycleTradesButton;
import de.maxhenkel.tradecycling.net.CycleTradesPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class EnchantHunt implements ClientModInitializer {
    public static final String MOD_ID = "enchant-hunt";
    public static final Logger LOGGER = LoggerFactory.getLogger("EnchantHunt");

    public static EnchantHuntConfig config;

    private static KeyMapping toggleKey;
    private static boolean active = false;
    private static int tickCounter = 0;
    private static int cycleCount = 0;

    @Override
    public void onInitializeClient() {
        config = EnchantHuntConfig.load();

        // Default UNBOUND (InputConstants.UNKNOWN). Category: GAMEPLAY.
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.enchant-hunt.toggle",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.GAMEPLAY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        // Vanilla keybinds (consumeClick) only fire when NO screen is open — but this tool is
        // used INSIDE the villager trade screen. So also listen for the key within the merchant
        // screen via Fabric's screen keyboard events.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof MerchantScreen) {
                ScreenKeyboardEvents.afterKeyPress(screen).register((scr, keyEvent) -> {
                    if (toggleKey.matches(keyEvent)) {
                        toggleActive(client);
                    }
                });
            }
        });
    }

    private void onClientTick(Minecraft mc) {
        // Handle the keybind toggle (queued presses).
        while (toggleKey.consumeClick()) {
            toggleActive(mc);
        }

        if (!active) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null) {
            stop(null, "");
            return;
        }

        Screen screen = mc.gui.screen();
        if (!(screen instanceof MerchantScreen merchantScreen)) {
            stop(player, "EnchantHunt: stopped (trade screen closed).");
            return;
        }

        MerchantMenu menu = merchantScreen.getMenu();
        if (!CycleTradesButton.canCycle(menu)) {
            // Not cyclable right now: villager has a job-level/xp or a trade is in progress.
            stop(player, "EnchantHunt: stopped (this villager can't be cycled).");
            return;
        }

        // Resolve the target enchantment Holder from the configured id.
        Optional<Holder.Reference<Enchantment>> target = resolveTarget(player);
        if (target.isEmpty()) {
            stop(player, "EnchantHunt: invalid target enchant id '" + config.targetEnchantId + "'. Fix it in the config.");
            return;
        }

        // Scan current offers for a match.
        MerchantOffers offers = menu.getOffers();
        for (MerchantOffer offer : offers) {
            if (matches(offer, target.get())) {
                String enchName = config.targetEnchantId + " " + config.level;
                int price = offer.getCostA().getCount();
                stop(player, "EnchantHunt: found " + enchName + " for " + price + " emeralds!");
                player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                return; // Leave the offer on screen; do NOT auto-buy.
            }
        }

        // No match this tick: cycle on the configured cadence.
        if (tickCounter % Math.max(1, config.cycleDelayTicks) == 0) {
            ClientPlayNetworking.send(new CycleTradesPacket());
            cycleCount++;
            if (cycleCount >= config.maxCycles) {
                stop(player, "EnchantHunt: gave up after " + cycleCount + " cycles.");
                return;
            }
        }
        tickCounter++;
    }

    private void toggleActive(Minecraft mc) {
        if (active) {
            stop(mc.player, "EnchantHunt: stopped.");
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (!config.hasValidTarget()) {
            player.sendSystemMessage(Component.literal("EnchantHunt: Set a target enchant in EnchantHunt config first"));
            return;
        }
        if (resolveTarget(player).isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "EnchantHunt: invalid target enchant id '" + config.targetEnchantId + "'. Fix it in the config."));
            return;
        }
        active = true;
        tickCounter = 0;
        cycleCount = 0;
        player.sendSystemMessage(Component.literal(
                "EnchantHunt: hunting " + config.targetEnchantId + " " + config.level
                        + " at <= " + config.maxPrice + " emeralds. Open a (cyclable) villager."));
    }

    private void stop(LocalPlayer player, String message) {
        active = false;
        tickCounter = 0;
        cycleCount = 0;
        if (player != null && message != null && !message.isEmpty()) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    private Optional<Holder.Reference<Enchantment>> resolveTarget(LocalPlayer player) {
        if (!config.hasValidTarget()) {
            return Optional.empty();
        }
        RegistryAccess access = player.registryAccess();
        Identifier id;
        try {
            id = Identifier.parse(config.targetEnchantId.trim());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        return access.lookupOrThrow(Registries.ENCHANTMENT).get(id);
    }

    private boolean matches(MerchantOffer offer, Holder<Enchantment> target) {
        ItemStack result = offer.getResult();
        if (result.getItem() != Items.ENCHANTED_BOOK) {
            return false;
        }
        ItemEnchantments stored = result.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        // EXACTLY the target enchantment at EXACTLY the configured level, and nothing else.
        if (stored.size() != 1) {
            return false;
        }
        int level = stored.getLevel(target);
        if (level != config.level) {
            return false;
        }
        // Price check: emerald cost (cost A) count must be <= maxPrice.
        return offer.getCostA().getCount() <= config.maxPrice;
    }
}
