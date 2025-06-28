package melonslise.locks.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class LocksClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue DEAF_MODE;
    public static final ForgeConfigSpec.BooleanValue OVERLAY;

    private LocksClientConfig() {
    }

    static {
        ForgeConfigSpec.Builder cfg = new ForgeConfigSpec.Builder();

        DEAF_MODE = cfg
                .comment("Display visual feedback when trying to use a locked block for certain hearing impaired individuals")
                .define("Deaf Mode", true);

        OVERLAY = cfg
                .comment("Whether or not to display the lock's information overlay when holding the lock picker")
                .define("Show Overlay", true);

        SPEC = cfg.build();
    }
}