package melonslise.locks.common.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.google.common.collect.Lists;
import melonslise.locks.Locks;
import melonslise.locks.common.util.LocksUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public final class LocksConfig {

    // ── ForgeConfigSpec (locks-common.toml) ──────────────────────────────────
    // GENERATED_LOCKS, GENERATED_LOCK_WEIGHTS, GENERATION_CHANCE, and
    // GENERATION_ENCHANT_CHANCE have been removed. All generation settings
    // are now configured exclusively in locks-generation.toml.

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GEN_LOCKABLE_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue RANDOMIZE_LOADED_LOCKS;

    static {
        ForgeConfigSpec.Builder cfg = new ForgeConfigSpec.Builder();

        GEN_LOCKABLE_BLOCKS = cfg
                .comment("Blocks that can be locked during world generation (supports regex).")
                .comment("当世界生成时锁定的方块")
                .defineList("Lockable Generated Blocks",
                        Lists.newArrayList("minecraft:chest", "minecraft:barrel", "lootr:.*", "quark:.*_chest"),
                        e -> e instanceof String);

        RANDOMIZE_LOADED_LOCKS = cfg
                .comment("Randomize lock IDs and combinations when loading from a structure file.")
                .comment("从结构文件加载锁ID和组合时进行随机化。随机化的方式与世界生成时相同。")
                .define("Randomize Loaded Locks", true);

        SPEC = cfg.build();
    }

    // ── Resolved at init() ────────────────────────────────────────────────────

    public static Pattern[] lockableGenBlocks;

    // Per-loot-table entries loaded from locks-generation.toml.
    // Items are resolved lazily on first roll() call, not at load time,
    // to avoid reading the item registry before it is fully populated.
    private static Map<String, LootTableEntry> lootTableEntries;

    // ── Inner class ───────────────────────────────────────────────────────────

    static final class LootTableEntry {
        // Raw lock type strings + weights stored at load time
        private final List<String[]> rawLocks; // each String[]: [itemId, weight]
        final double genChance;
        final double enchChance;

        // Resolved lazily on first roll()
        private NavigableMap<Integer, Item> weightedLocks = null;
        private int weightTotal = 0;

        LootTableEntry(List<?> lockList, double genChance, double enchChance) {
            this.genChance  = 1f; //genChance;
            this.enchChance = enchChance;
            this.rawLocks   = new ArrayList<>();

            for (Object raw : lockList) {
                if (!(raw instanceof Config entry)) continue;
                Object typeRaw   = entry.get("lockType");
                Object weightRaw = entry.get("weight");
                if (typeRaw == null || weightRaw == null) continue;
                int weight = ((Number) weightRaw).intValue();
                if (weight <= 0) continue;
                String lockType = typeRaw.toString();
                String itemId = lockType.contains(":") ? lockType : "locks:" + lockType + "_lock";
                rawLocks.add(new String[]{ itemId, String.valueOf(weight) });
            }
        }

        // Resolve item registry lookups deferred to first use,
        // by which point all items are guaranteed to be registered.
        private void resolveIfNeeded() {
            if (weightedLocks != null) return;
            weightedLocks = new TreeMap<>();
            int total = 0;
            for (String[] entry : rawLocks) {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(entry[0]));
                // Skip air/missing items
                if (item == net.minecraft.world.item.Items.AIR) {
                    Locks.LOGGER.warn("[LocksConfig] Item '{}' not found in registry, skipping.", entry[0]);
                    continue;
                }
                int weight = Integer.parseInt(entry[1]);
                total += weight;
                weightedLocks.put(total, item);
            }
            weightTotal = total;
            if (weightTotal == 0) {
                Locks.LOGGER.warn("[LocksConfig] LootTableEntry resolved with no valid items!");
            }
        }

        boolean isValid() {
            // Check raw locks before resolution so we can validate at load time
            return !rawLocks.isEmpty();
        }

        boolean isValidResolved() {
            resolveIfNeeded();
            return weightTotal > 0 && !weightedLocks.isEmpty();
        }

        ItemStack roll(RandomSource rng) {
            if (!LocksUtil.chance(rng, genChance)) {
                Locks.LOGGER.info("[LootTableEntry] genChance roll FAILED (chance={})", genChance);
                return null;
            }
            resolveIfNeeded();
            Locks.LOGGER.info(getInfo());//"[LootTableEntry] weightTotal={} locks={}", weightTotal, weightedLocks);

            resolveIfNeeded();
            if (weightTotal == 0 || weightedLocks.isEmpty()) return null;

            Map.Entry<Integer, Item> found = weightedLocks.ceilingEntry(rng.nextInt(weightTotal) + 1);
            if (found == null) return null; // safety guard
            ItemStack stack = new ItemStack(found.getValue());
            return LocksUtil.chance(rng, enchChance)
                    ? EnchantmentHelper.enchantItem(rng, stack, 5 + rng.nextInt(30), false)
                    : stack;
        }

        public String getInfo() {
            resolveIfNeeded();
            return "weightTotal=" + weightTotal
                    + " genChance=" + genChance
                    + " enchChance=" + enchChance
                    + " locks=" + weightedLocks;
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private LocksConfig() {}

    public static synchronized void init() {
        Locks.LOGGER.info("[LocksConfig] init() called");
        lockableGenBlocks = GEN_LOCKABLE_BLOCKS.get().stream()
                .map(Pattern::compile).toArray(Pattern[]::new);
        loadLootTableConfig();
    }

    // ── locks-generation.toml ─────────────────────────────────────────────────

    private static final String GEN_CONFIG_FILE = "locks-generation.toml";

    private static void loadLootTableConfig() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path file = configDir.resolve(GEN_CONFIG_FILE);

        Locks.LOGGER.info("[LocksConfig] Looking for: {}", file.toAbsolutePath());

        if (!Files.exists(file)) {
            Locks.LOGGER.info("[LocksConfig] Not found, writing defaults...");
            writeDefaultLootTableConfig(file);
        }

        lootTableEntries = new HashMap<>();
        try (FileConfig cfg = FileConfig.builder(file.toFile(), TomlFormat.instance()).sync().build()) {
            cfg.load();

            Config section = cfg.get("lootTable");
            if (section == null) {
                Locks.LOGGER.warn("[LocksConfig] No [lootTable] section in {}.", GEN_CONFIG_FILE);
                return;
            }

            Locks.LOGGER.info("[LocksConfig] Found [lootTable] keys: {}", section.valueMap().keySet());

            for (Map.Entry<String, Object> kv : section.valueMap().entrySet()) {
                String key = kv.getKey();
                if (!(kv.getValue() instanceof Config entry)) continue;

                List<?> lockList    = entry.get("locks");
                Number  genChanceN  = entry.get("genChance");
                Number  enchChanceN = entry.get("enchChance");
                if (lockList == null) {
                    Locks.LOGGER.warn("[LocksConfig] Entry '{}' missing 'locks', skipping.", key);
                    continue;
                }

                double genChance  = genChanceN  != null ? genChanceN.doubleValue()  : 0.85;
                double enchChance = enchChanceN != null ? enchChanceN.doubleValue() : 0.40;
                LootTableEntry resolved = new LootTableEntry(lockList, genChance, enchChance);
                if (resolved.isValid()) {
                    lootTableEntries.put(key, resolved);
                    Locks.LOGGER.info("[LocksConfig] Loaded entry '{}' ({} locks, genChance={}, enchChance={})",
                            key, resolved.rawLocks.size(), genChance, enchChance);
                } else {
                    Locks.LOGGER.warn("[LocksConfig] Entry '{}' has no valid locks, skipping.", key);
                }
            }
            Locks.LOGGER.info("[LocksConfig] Loaded {} loot-table entries total.", lootTableEntries.size());
        } catch (Exception e) {
            Locks.LOGGER.error("[LocksConfig] Failed to load {}: {}", GEN_CONFIG_FILE, e.getMessage(), e);
        }
    }

    private static void writeDefaultLootTableConfig(Path path) {
        try { Files.createDirectories(path.getParent()); }
        catch (IOException e) {
            Locks.LOGGER.error("[LocksConfig] Could not create config dir: {}", e.getMessage());
            return;
        }
        String content =
                """
                # Locks \u2013 Per Loot Table Lock Generation Config (locks-generation.toml)
                # =====================================================================
                #
                # HOW TO ADD AN ENTRY
                # -------------------
                # [lootTable."modId:path/to/loot_table"]
                # locks      = [{ lockType = "material", weight = N }, ...]
                # genChance  = 0.0 ~ 1.0   (chance a lock spawns on this chest type)
                # enchChance = 0.0 ~ 1.0   (chance the lock is enchanted)
                #
                # "default" matches any chest whose loot table is not explicitly listed.
                # If a chest has no loot table at all, it also falls back to "default".
                #
                # lockType short names: wood, copper, gold, iron, steel, diamond
                #   (expands to locks:wood_lock etc.)
                # Full item ids also work: e.g. "mymod:my_custom_lock"
                #
                # weight: positive integer - higher = more likely to be chosen.
                
                [lootTable."default"]
                locks = [
                    { lockType = "wood",    weight = 6 },
                    { lockType = "copper",  weight = 5 },
                    { lockType = "gold",    weight = 4 },
                    { lockType = "iron",    weight = 3 },
                    { lockType = "steel",   weight = 2 },
                    { lockType = "diamond", weight = 1 }
                ]
                genChance  = 0.85
                enchChance = 0.40
                
                [lootTable."minecraft:chests/simple_dungeon"]
                locks = [
                    { lockType = "wood",   weight = 6 },
                    { lockType = "copper", weight = 5 },
                    { lockType = "gold",   weight = 4 }
                ]
                genChance  = 0.40
                enchChance = 0.10
                
                [lootTable."minecraft:chests/abandoned_mineshaft"]
                locks = [
                    { lockType = "wood",   weight = 5 },
                    { lockType = "copper", weight = 4 },
                    { lockType = "iron",   weight = 2 }
                ]
                genChance  = 0.50
                enchChance = 0.15
                
                [lootTable."minecraft:chests/desert_pyramid"]
                locks = [
                    { lockType = "copper", weight = 5 },
                    { lockType = "gold",   weight = 6 },
                    { lockType = "iron",   weight = 3 }
                ]
                genChance  = 0.70
                enchChance = 0.25
                
                [lootTable."minecraft:chests/jungle_temple"]
                locks = [
                    { lockType = "wood",   weight = 4 },
                    { lockType = "copper", weight = 5 },
                    { lockType = "gold",   weight = 4 }
                ]
                genChance  = 0.65
                enchChance = 0.20
                
                [lootTable."minecraft:chests/pillager_outpost"]
                locks = [
                    { lockType = "copper", weight = 4 },
                    { lockType = "iron",   weight = 5 },
                    { lockType = "steel",  weight = 3 }
                ]
                genChance  = 0.75
                enchChance = 0.25
                
                [lootTable."minecraft:chests/woodland_mansion"]
                locks = [
                    { lockType = "iron",    weight = 5 },
                    { lockType = "steel",   weight = 4 },
                    { lockType = "diamond", weight = 2 }
                ]
                genChance  = 0.85
                enchChance = 0.40
                
                [lootTable."minecraft:chests/shipwreck_treasure"]
                locks = [
                    { lockType = "copper", weight = 4 },
                    { lockType = "gold",   weight = 5 },
                    { lockType = "iron",   weight = 3 }
                ]
                genChance  = 0.75
                enchChance = 0.30
                
                [lootTable."minecraft:chests/nether_bridge"]
                locks = [
                    { lockType = "gold",  weight = 5 },
                    { lockType = "iron",  weight = 4 },
                    { lockType = "steel", weight = 2 }
                ]
                genChance  = 0.75
                enchChance = 0.35
                
                [lootTable."minecraft:chests/bastion_treasure"]
                locks = [
                    { lockType = "gold",    weight = 4 },
                    { lockType = "steel",   weight = 4 },
                    { lockType = "diamond", weight = 3 }
                ]
                genChance  = 0.90
                enchChance = 0.55
                
                [lootTable."minecraft:chests/bastion_other"]
                locks = [
                    { lockType = "gold",  weight = 5 },
                    { lockType = "iron",  weight = 4 },
                    { lockType = "steel", weight = 2 }
                ]
                genChance  = 0.70
                enchChance = 0.30
                
                [lootTable."minecraft:chests/end_city_treasure"]
                locks = [
                    { lockType = "steel",   weight = 4 },
                    { lockType = "diamond", weight = 5 }
                ]
                genChance  = 0.95
                enchChance = 0.65
                
                [lootTable."minecraft:chests/stronghold_corridor"]
                locks = [
                    { lockType = "iron",    weight = 5 },
                    { lockType = "steel",   weight = 4 },
                    { lockType = "diamond", weight = 1 }
                ]
                genChance  = 0.80
                enchChance = 0.40
                
                [lootTable."minecraft:chests/stronghold_library"]
                locks = [
                    { lockType = "iron",    weight = 4 },
                    { lockType = "steel",   weight = 4 },
                    { lockType = "diamond", weight = 3 }
                ]
                genChance  = 0.90
                enchChance = 0.55
                
                [lootTable."minecraft:chests/ancient_city"]
                locks = [
                    { lockType = "steel",   weight = 5 },
                    { lockType = "diamond", weight = 4 }
                ]
                genChance  = 0.95
                enchChance = 0.60
                
                [lootTable."minecraft:chests/buried_treasure"]
                locks = [
                    { lockType = "gold",    weight = 5 },
                    { lockType = "steel",   weight = 4 },
                    { lockType = "diamond", weight = 3 }
                ]
                genChance  = 0.90
                enchChance = 0.50
                
                [lootTable."minecraft:chests/ruined_portal"]
                locks = [
                    { lockType = "gold",  weight = 6 },
                    { lockType = "iron",  weight = 3 },
                    { lockType = "steel", weight = 2 }
                ]
                genChance  = 0.65
                enchChance = 0.30
                
                [lootTable."minecraft:chests/ancient_city_ice_box"]
                locks = [
                    { lockType = "iron",  weight = 4 },
                    { lockType = "steel", weight = 3 }
                ]
                genChance  = 0.70
                enchChance = 0.35
                
                [lootTable."minecraft:chests/village/village_plains_house"]
                locks = [
                    { lockType = "wood",   weight = 6 },
                    { lockType = "copper", weight = 3 }
                ]
                genChance  = 0.30
                enchChance = 0.05
                
                [lootTable."minecraft:chests/village/village_desert_house"]
                locks = [
                    { lockType = "wood",   weight = 6 },
                    { lockType = "copper", weight = 3 }
                ]
                genChance  = 0.30
                enchChance = 0.05
                
                [lootTable."minecraft:chests/village/village_savanna_house"]
                locks = [
                    { lockType = "wood",   weight = 6 },
                    { lockType = "copper", weight = 3 }
                ]
                genChance  = 0.30
                enchChance = 0.05
                
                [lootTable."minecraft:chests/village/village_snowy_house"]
                locks = [
                    { lockType = "wood",   weight = 6 },
                    { lockType = "copper", weight = 3 }
                ]
                genChance  = 0.30
                enchChance = 0.05
                
                [lootTable."minecraft:chests/village/village_taiga_house"]
                locks = [
                    { lockType = "wood",   weight = 6 },
                    { lockType = "copper", weight = 3 }
                ]
                genChance  = 0.30
                enchChance = 0.05
                
                [lootTable."minecraft:chests/village/village_weaponsmith"]
                locks = [
                    { lockType = "iron",  weight = 5 },
                    { lockType = "steel", weight = 4 }
                ]
                genChance  = 0.70
                enchChance = 0.20
                
                [lootTable."minecraft:chests/village/village_toolsmith"]
                locks = [
                    { lockType = "copper", weight = 5 },
                    { lockType = "iron",   weight = 4 }
                ]
                genChance  = 0.60
                enchChance = 0.15
                
                [lootTable."minecraft:chests/village/village_armorer"]
                locks = [
                    { lockType = "iron",  weight = 5 },
                    { lockType = "steel", weight = 3 }
                ]
                genChance  = 0.65
                enchChance = 0.20
                
                [lootTable."minecraft:chests/village/village_cartographer"]
                locks = [
                    { lockType = "copper", weight = 5 },
                    { lockType = "gold",   weight = 3 }
                ]
                genChance  = 0.50
                enchChance = 0.10
                
                [lootTable."minecraft:chests/village/village_mason"]
                locks = [
                    { lockType = "wood",   weight = 5 },
                    { lockType = "copper", weight = 3 }
                ]
                genChance  = 0.35
                enchChance = 0.05
                
                [lootTable."minecraft:chests/village/village_shepherd"]
                locks = [
                    { lockType = "wood",   weight = 6 },
                    { lockType = "copper", weight = 2 }
                ]
                genChance  = 0.25
                enchChance = 0.05
                
                [lootTable."minecraft:chests/village/village_butcher"]
                locks = [
                    { lockType = "wood",   weight = 5 },
                    { lockType = "copper", weight = 3 }
                ]
                genChance  = 0.25
                enchChance = 0.05
                
                [lootTable."minecraft:chests/village/village_tannery"]
                locks = [
                    { lockType = "wood",   weight = 6 },
                    { lockType = "copper", weight = 2 }
                ]
                genChance  = 0.25
                enchChance = 0.05
                
                [lootTable."minecraft:chests/village/village_temple"]
                locks = [
                    { lockType = "copper", weight = 4 },
                    { lockType = "gold",   weight = 5 }
                ]
                genChance  = 0.55
                enchChance = 0.20
                """;
        try {
            Files.writeString(path, content);
            Locks.LOGGER.info("[LocksConfig] Created default {}.", GEN_CONFIG_FILE);
        } catch (IOException e) {
            Locks.LOGGER.error("[LocksConfig] Failed to write {}: {}", GEN_CONFIG_FILE, e.getMessage());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static boolean matchString(Block block) {
        String name = BuiltInRegistries.BLOCK.getKey(block).toString();
        if (lockableGenBlocks == null) init();
        for (Pattern p : lockableGenBlocks) {
            if (p.matcher(name).matches()) return true;
        }
        return false;
    }

    /**
     * Main lock generation entry point.
     * Tries specific loot table entry, then "default", then returns null.
     * Item registry lookups are deferred to first call so registration is complete.
     */
    public static ItemStack rollLock(RandomSource rng, ResourceLocation lootTableId) {
        if (lootTableEntries == null) init();

        LootTableEntry entry = null;
        if (lootTableId != null) entry = lootTableEntries.get(lootTableId.toString());
        if (entry == null)
        {
            Locks.LOGGER.warn("[LocksConfig] Unable to roll for {}, now attempting default.", lootTableId.toString());
            entry = lootTableEntries.get("default");
        }
        if (entry == null)
        {
            Locks.LOGGER.warn("[LocksConfig] Unable to roll for default as well. returning null???");
            return null;
        }

        Locks.LOGGER.info("[LocksConfig] rollLock id='{}' entry={} genChance={} weightTotal={}",
                lootTableId, entry != null ? "found" : "null(using default)",
                entry != null ? entry.genChance : -1,
                entry != null ? entry.weightTotal : -1);
        return entry.roll(rng);
    }

    /**
     * Used by StructureTemplateMixin for RANDOMIZE_LOADED_LOCKS.
     * Uses "default" entry from locks-generation.toml.
     */
    public static ItemStack getRandomLock(RandomSource rng) {
        if (lootTableEntries == null) init();
        LootTableEntry def = lootTableEntries.get("default");
        if (def == null) return null;
        // For RANDOMIZE_LOADED_LOCKS we always generate (skip genChance roll)
        def.resolveIfNeeded();
        if (def.weightTotal == 0 || def.weightedLocks.isEmpty()) return null;
        Map.Entry<Integer, Item> found = def.weightedLocks.ceilingEntry(rng.nextInt(def.weightTotal) + 1);
        if (found == null) return null;
        ItemStack stack = new ItemStack(found.getValue());
        return LocksUtil.chance(rng, def.enchChance)
                ? EnchantmentHelper.enchantItem(rng, stack, 5 + rng.nextInt(30), false)
                : stack;
    }

    public static boolean canGen(RandomSource rng, Block block) {
        if (lootTableEntries == null) init();
        LootTableEntry def = lootTableEntries.get("default");
        double genChance = def != null ? def.genChance : 0.85;
        return LocksUtil.chance(rng, genChance) && matchString(block);
    }

    public static boolean canEnchant(RandomSource rng) {
        if (lootTableEntries == null) init();
        LootTableEntry def = lootTableEntries.get("default");
        double enchChance = def != null ? def.enchChance : 0.40;
        return LocksUtil.chance(rng, enchChance);
    }
}