package bcquarryfluids.menu;

import bcquarryfluids.Config;
import bcquarryfluids.MultiFluidTank;
import bcquarryfluids.QuarryExtras;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;

public record QuarryMenuProvider(Level level, BlockPos pos, QuarryExtras quarry) implements ExtendedMenuProvider<QuarryMenuData> {
    @Override
    public QuarryMenuData getScreenOpeningData(ServerPlayer player) {
        return data();
    }

    private QuarryMenuData data() {
        MultiFluidTank tank = quarry.bcqf$getTank();
        return new QuarryMenuData(pos, quarry.bcqf$getInventory().getContainerSize() / 9, tank.slotCount(),
            (int) (tank.capacity() / (FluidConstants.BUCKET / 1000)));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftbuilders.quarry");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new QuarryMenu(containerId, playerInventory, data(), quarry.bcqf$getInventory(), quarry.bcqf$getUpgrades(),
            new TankData(quarry.bcqf$getTank()), ContainerLevelAccess.create(level, pos));
    }

    /** Live view of the tank for the vanilla menu data sync: registry id of the fluid and its amount in mB per slot. */
    private record TankData(MultiFluidTank tank) implements ContainerData {
        @Override
        public int get(int index) {
            SingleFluidStorage slot = tank.slot(index / 2);
            if (index % 2 == 0) {
                return slot.isResourceBlank() ? -1 : BuiltInRegistries.FLUID.getId(slot.variant.getFluid());
            }
            return (int) (slot.amount * 1000 / FluidConstants.BUCKET);
        }

        @Override
        public void set(int index, int value) {
            // read-only on the client
        }

        @Override
        public int getCount() {
            return tank.slotCount() * 2;
        }
    }

    /** Capacity per slot in mB, exposed for the config-independent client. */
    public static int capacityMb() {
        return Config.tankCapacityMb();
    }
}
