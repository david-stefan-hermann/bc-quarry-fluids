package bcquarryfluids;

import bcquarryfluids.menu.QuarryMenu;
import bcquarryfluids.menu.QuarryMenuData;
import bcquarryfluids.menu.QuarryMenuProvider;
import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.core.item.ItemWrench_Neptune;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

import java.util.Set;

public final class BcQuarryFluids implements ModInitializer {
    public static final String MOD_ID = "bcquarryfluids";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ExtendedMenuType<QuarryMenu, QuarryMenuData> QUARRY_MENU =
        new ExtendedMenuType<>(QuarryMenu::new, QuarryMenuData.STREAM_CODEC);
    /** Dev aid: {@code -Dbcqf.screenshot=x,y,z} opens the quarry menu at that position for every joining player. */
    public static final String SCREENSHOT_PROPERTY = "bcqf.screenshot";

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
        registerScreenshotHook();
    }

    /** Right-click on a quarry opens the inventory / tank screen, unless sneaking or holding a BuildCraft wrench. */
    private static InteractionResult openQuarryMenu(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || player.getMainHandItem().getItem() instanceof ItemWrench_Neptune) {
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

    private static void registerScreenshotHook() {
        String property = System.getProperty(SCREENSHOT_PROPERTY);
        if (property == null) {
            return;
        }
        String[] parts = property.split(",");
        BlockPos pos = new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        int[] countdown = {-1};
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (countdown[0] < 0) {
                if (!server.getPlayerList().getPlayers().isEmpty()) {
                    countdown[0] = 60;
                }
                return;
            }
            if (countdown[0]-- != 0) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerLevel level = server.overworld();
                player.teleportTo(level, pos.getX() + 1.5, pos.getY() + 1.0, pos.getZ() + 1.5, Set.<Relative>of(), 0.0F, 0.0F, false);
                if (level.getBlockEntity(pos) instanceof QuarryExtras quarry) {
                    player.openMenu(new QuarryMenuProvider(level, pos, quarry));
                    LOGGER.info("Screenshot hook: opened the quarry menu at {} for {}", pos, player.getName().getString());
                }
            }
        });
    }
}
