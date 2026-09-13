package bcquarryfluids.mixin;

import bcquarryfluids.Config;
import bcquarryfluids.FluidMode;
import bcquarryfluids.MultiFluidTank;
import bcquarryfluids.QuarryExtras;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.lib.fabric.transfer.NeighborTransfers;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.nbt.BcValueIn;
import buildcraft.lib.nbt.BcValueOut;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the fluid tank, the item inventory and the upgrade slots to the quarry, persists them, pushes collected fluid
 * to neighbours, and makes fluid blocks minable (upstream skips every block that carries a fluid). The actual removal
 * happens in {@link TaskBreakBlockMixin}.
 */
@Mixin(TileQuarry.class)
public abstract class TileQuarryMixin implements QuarryExtras {
    private static final String TANK_KEY = "bcqf_tank";
    private static final String ITEMS_KEY = "bcqf_items";
    private static final String UPGRADES_KEY = "bcqf_upgrades";
    private static final int INVENTORY_SLOTS = 9;

    @Unique
    private MultiFluidTank bcqf$tank;
    @Unique
    private SimpleContainer bcqf$inventory;
    @Unique
    private SimpleContainer bcqf$upgrades;

    @Override
    public MultiFluidTank bcqf$getTank() {
        if (bcqf$tank == null) {
            BlockEntity self = (BlockEntity) (Object) this;
            bcqf$tank = new MultiFluidTank(Config.fluidSlots(), Config.tankCapacityMb() * (FluidConstants.BUCKET / 1000), self::setChanged);
        }
        return bcqf$tank;
    }

    @Override
    public SimpleContainer bcqf$getInventory() {
        if (bcqf$inventory == null) {
            bcqf$inventory = bcqf$container(INVENTORY_SLOTS);
        }
        return bcqf$inventory;
    }

    @Override
    public SimpleContainer bcqf$getUpgrades() {
        if (bcqf$upgrades == null) {
            bcqf$upgrades = bcqf$container(4);
        }
        return bcqf$upgrades;
    }

    @Unique
    private SimpleContainer bcqf$container(int size) {
        BlockEntity self = (BlockEntity) (Object) this;
        return new SimpleContainer(size) {
            @Override
            public void setChanged() {
                super.setChanged();
                self.setChanged();
            }
        };
    }

    /** Upstream: {@code fluid != null -> false}. With a mode set, fluids obey the same rules as solid blocks. */
    @Inject(method = "canMine", at = @At("HEAD"), cancellable = true)
    private void bcqf$canMineFluids(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (Config.mode() == FluidMode.IGNORE) {
            return;
        }
        TileQuarry self = (TileQuarry) (Object) this;
        Level level = self.getLevel();
        if (level == null || BlockUtil.getFluidWithFlowing(level, pos) == null) {
            return; // no fluid here, upstream logic applies
        }
        BlockState state = level.getBlockState(pos);
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(!(level instanceof ServerLevel serverLevel && !BlockUtil.canMachineBreak(serverLevel, pos, self.getOwner())));
    }

    /** Pure fluid blocks must stop the mining iterator instead of being "passable", otherwise they are skipped. */
    @Inject(method = "canMoveThrough", at = @At("HEAD"), cancellable = true)
    private void bcqf$fluidsAreNotPassable(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (Config.mode() == FluidMode.IGNORE) {
            return;
        }
        TileQuarry self = (TileQuarry) (Object) this;
        Level level = self.getLevel();
        if (level != null && level.getBlockState(pos).getBlock() instanceof LiquidBlock) {
            cir.setReturnValue(false);
        }
    }

    /** Push collected fluid into neighbouring pipes and tanks every tick, like the BuildCraft pump does. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void bcqf$pushFluid(CallbackInfo ci) {
        TileQuarry self = (TileQuarry) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide() || bcqf$tank == null || bcqf$tank.isEmpty()) {
            return;
        }
        NeighborTransfers.pushFluidToNeighbors(level, self.getBlockPos(), bcqf$tank);
    }

    @Inject(method = "writeData", at = @At("TAIL"))
    private void bcqf$writeExtras(BcValueOut output, CallbackInfo ci) {
        if (bcqf$tank != null && !bcqf$tank.isEmpty()) {
            bcqf$tank.write(output, TANK_KEY);
        }
        if (bcqf$inventory != null && !bcqf$inventory.isEmpty()) {
            output.store(ITEMS_KEY, ItemStack.OPTIONAL_CODEC.listOf(), new ArrayList<>(bcqf$inventory.getItems()));
        }
        if (bcqf$upgrades != null && !bcqf$upgrades.isEmpty()) {
            output.store(UPGRADES_KEY, ItemStack.OPTIONAL_CODEC.listOf(), new ArrayList<>(bcqf$upgrades.getItems()));
        }
    }

    @Inject(method = "readData", at = @At("TAIL"))
    private void bcqf$readExtras(BcValueIn input, CallbackInfo ci) {
        input.read(TANK_KEY, MultiFluidTank.Entry.LIST_CODEC).ifPresent(entries -> bcqf$getTank().read(input, TANK_KEY));
        input.read(ITEMS_KEY, ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(items -> {
            // Worlds from the first build had a bigger inventory: keep a larger container so nothing is lost;
            // the screen shows the first 9 slots, pipes and importers still reach all of them.
            int used = items.size();
            while (used > INVENTORY_SLOTS && items.get(used - 1).isEmpty()) {
                used--;
            }
            if (used > INVENTORY_SLOTS) {
                bcqf$inventory = bcqf$container(used);
            }
            bcqf$load(bcqf$getInventory(), items);
        });
        input.read(UPGRADES_KEY, ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(items -> bcqf$load(bcqf$getUpgrades(), items));
    }

    @Unique
    private static void bcqf$load(SimpleContainer container, List<ItemStack> items) {
        NonNullList<ItemStack> target = container.getItems();
        for (int i = 0; i < target.size(); i++) {
            target.set(i, i < items.size() ? items.get(i) : ItemStack.EMPTY);
        }
    }
}
