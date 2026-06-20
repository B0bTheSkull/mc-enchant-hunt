# EnchantHunt

Client-side Fabric mod (Minecraft 26.2) that auto-cycles a villager's trades — using
[Trade Cycling](https://modrinth.com/mod/trade-cycling) — until an enchanted book matching
your configured enchantment, exact level, and max emerald price appears, then stops and alerts you.

## Usage
1. **ModMenu → EnchantHunt → Config:** set the target enchant id (e.g. `minecraft:efficiency`),
   exact level, and max price (emeralds). Optional: cycle delay (ticks) and max-cycles safety cap.
2. Bind the **"Toggle Enchant Hunt"** keybind (Controls → Gameplay; unbound by default).
3. Open a **fresh** librarian (never traded with), press the key. It cycles until a match is
   found at/under your price, dings, prints the result, and leaves the trade on screen (no auto-buy).
   Stops on match, screen close, non-cyclable villager, or the cycle cap.

## Requires
Trade Cycling, Cloth Config, Mod Menu, Fabric API (all client + Trade Cycling also server-side).

## Building
Mojang official mappings, JDK 25, `./gradlew build`. Compile-time jars (Trade Cycling, Cloth Config,
Mod Menu, and three Fabric API submodules) are expected in `libs/` (gitignored) and are provided at
runtime by the installed mods.
