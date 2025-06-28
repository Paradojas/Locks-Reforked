package melonslise.locks.common.config;

import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.regex.Pattern;

public class LocksServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MAX_LOCKABLE_VOLUME;
    public static final ForgeConfigSpec.BooleanValue ALLOW_REMOVING_LOCKS;
    public static final ForgeConfigSpec.BooleanValue PROTECT_LOCKABLES;
    public static final ForgeConfigSpec.BooleanValue EASY_LOCK;
    public static final ForgeConfigSpec.BooleanValue STRONG_PREVENTION;

    public static Pattern[] lockableBlocks;

    static {
        ForgeConfigSpec.Builder cfg = new ForgeConfigSpec.Builder();

        MAX_LOCKABLE_VOLUME = cfg
                .comment("Maximum amount of blocks that can be locked at once")
                .defineInRange("Max Lockable Volume", 6, 1, Integer.MAX_VALUE);
        ALLOW_REMOVING_LOCKS = cfg
                .comment("Open locks can be removed with an empty hand while sneaking")
                .define("Allow Removing Locks", true);
        PROTECT_LOCKABLES = cfg
                .comment("Locked blocks cannot be destroyed in survival mode")
                .define("Protect Lockables", true);
        EASY_LOCK = cfg
                .comment("Lock blocks with just one click! It's magic! (Cancel will probably fail spectacularly with custom doors, custom double chests, etc)")
                .define("Easy Lock", true);
        STRONG_PREVENTION = cfg
                .comment("Use stronger checks to prevent blocks on locks from being broken, but its compatibility is unknown")
                .define("Stronger Prevention", false);
        SPEC = cfg.build();
    }

    private LocksServerConfig() {
    }

    public static void init() {
    }

    public static boolean canLock(Level world, BlockPos pos) {
        var block = world.getBlockState(pos);
        var lockable = TagKey.create(Registries.BLOCK, new ResourceLocation("locks:lockable"));
        return block.is(lockable);
    }
}