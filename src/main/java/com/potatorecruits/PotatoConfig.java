package com.potatorecruits;

import net.minecraftforge.common.ForgeConfigSpec;

public class PotatoConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue RANGE;
    public static final ForgeConfigSpec.IntValue MIN_INTERVAL_TICKS;
    public static final ForgeConfigSpec.BooleanValue CONSUME_AMMO;
    public static final ForgeConfigSpec.BooleanValue DAMAGE_CANNON;
    public static final ForgeConfigSpec.BooleanValue AVOID_FRIENDLY_FIRE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        RANGE = b.comment("Maximum distance (blocks) at which a recruit fires the Potato Cannon.")
                .defineInRange("range", 24.0, 4.0, 64.0);
        MIN_INTERVAL_TICKS = b.comment("Minimum ticks between two shots (the ammo's own reload time is used if it is longer).")
                .defineInRange("minIntervalTicks", 10, 1, 200);
        CONSUME_AMMO = b.comment("Each shot uses one item of ammo from the recruit's inventory.")
                .define("consumeAmmo", true);
        DAMAGE_CANNON = b.comment("Each shot costs 1 durability of the cannon.")
                .define("damageCannon", true);
        AVOID_FRIENDLY_FIRE = b.comment("Do not fire if the owner or another recruit of the same owner is in the line of fire.")
                .define("avoidFriendlyFire", true);
        SPEC = b.build();
    }
}
