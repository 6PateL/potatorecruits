package com.potatorecruits;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(PotatoRecruits.MODID)
public class PotatoRecruits {
    public static final String MODID = "potatorecruits";

    public PotatoRecruits() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PotatoConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(new PotatoEvents());
    }
}
