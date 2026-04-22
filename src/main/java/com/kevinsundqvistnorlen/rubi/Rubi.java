package com.kevinsundqvistnorlen.rubi;

import net.minecraftforge.fml.common.Mod;

@Mod(Rubi.MODID)
public final class Rubi {
    public static final String MODID = "rubi";

    public Rubi() {
        KnownReadings.load();
        Utils.LOGGER.info("Rubi (Forge) loaded; {} known readings.", KnownReadings.totalReadings());
    }
}
