package bcquarryfluids;

import bcquarryfluids.menu.QuarryMenu;
import bcquarryfluids.menu.QuarryMenuData;
import bcquarryfluids.menu.QuarryMenuProvider;
import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.BCBuildersBlocks;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;

public final class BcQuarryFluids implements ModInitializer {
    public static final String MOD_ID = "bcquarryfluids";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ExtendedMenuType<QuarryMenu, QuarryMenuData> QUARRY_MENU =
        new ExtendedMenuType<>(QuarryMenu::new, QuarryMenuData.STREAM_CODEC);

    private static boolean tankExposed = false;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Config.load();
        Registry.register(BuiltInRegistries.MENU, id("quarry"), QUARRY_MENU);
        exposeQuarryTank();
        // Re-read the config on every server start so edits apply after a world reload, and retry the tank
        // registration in case BuildCraft initialised after us.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Config.load();
            exposeQuarryTank();
        });
        UseBlockCallback.EVENT.register(BcQuarryFluids::openQuarryMenu);
    }

    /** Right-click on a quarry with an empty main hand opens the inventory / tank screen. */
    private static InteractionResult openQuarryMenu(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level,
                                                    InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        BlockPos pos = hit.getBlockPos();
        if (!level.getBlockState(pos).is(BCBuildersBlocks.QUARRY)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof QuarryExtras quarry) {
            serverPlayer.openMenu(new QuarryMenuProvider(level, pos, quarry));
        }
        return InteractionResult.SUCCESS;
    }

    /** Lets fluid pipes, tanks and other mods' importers pull from the quarry tank (extract only). */
    private static void exposeQuarryTank() {
        if (tankExposed || BCBuildersBlockEntities.QUARRY == null) {
            return;
        }
        FluidStorage.SIDED.registerForBlockEntity(
            (quarry, direction) -> FilteringStorage.extractOnlyOf(((QuarryExtras) quarry).bcqf$getTank()),
            BCBuildersBlockEntities.QUARRY
        );
        tankExposed = true;
        LOGGER.info("Exposed the BuildCraft quarry tank via the Fabric fluid transfer API");
    }
}
