package bcquarryfluids.client;

import bcquarryfluids.BcQuarryFluids;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class BcQuarryFluidsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(BcQuarryFluids.QUARRY_MENU, QuarryScreen::new);
    }
}
