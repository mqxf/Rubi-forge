package com.kevinsundqvistnorlen.rubi;

import com.kevinsundqvistnorlen.rubi.command.RubiCommand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// RegisterClientCommandsEvent is dispatched on the FORGE event bus, not the MOD bus, so this lives in
// its own subscriber class separate from the reload-listener registration in RubiClient.
@Mod.EventBusSubscriber(modid = Rubi.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RubiClientCommands {
    private RubiClientCommands() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        RubiCommand.register(event.getDispatcher());
    }
}
