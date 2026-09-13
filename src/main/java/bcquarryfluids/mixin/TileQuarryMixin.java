package bcquarryfluids.mixin;

import bcquarryfluids.Config;
import bcquarryfluids.FluidMode;
import bcquarryfluids.MultiFluidTank;
import bcquarryfluids.QuarryExtras;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.lib.fabric.transfer.NeighborTransfers;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.misc.data.BoxIterator;
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
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Unique
    private long bcqf$pumpRetryAt;

    @Override
    public long bcqf$getPumpRetryAt() {
        return bcqf$pumpRetryAt;
    }

    @Override
    public void bcqf$setPumpRetryAt(long gameTime) {
        bcqf$pumpRetryAt = gameTime;
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
        if (state.getBlock() instanceof LiquidBlock && !state.getFluidState().isSource()) {
            // flowing fluid: nothing to take, and it would just flow back; the drill passes through it
            cir.setReturnValue(false);
            return;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(!(level instanceof ServerLevel serverLevel && !BlockUtil.canMachineBreak(serverLevel, pos, self.getOwner())));
    }

    /** Fluid source blocks must stop the mining iterator instead of being "passable", otherwise they are skipped. */
    @Inject(method = "canMoveThrough", at = @At("HEAD"), cancellable = true)
    private void bcqf$sourcesAreNotPassable(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (Config.mode() == FluidMode.IGNORE) {
            return;
        }
        TileQuarry self = (TileQuarry) (Object) this;
        Level level = self.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof LiquidBlock && state.getFluidState().isSource()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * A minable block inside the already mined part of the pit (placed after that layer was mined). Found by a
     * top-down scan, so the highest such block is always handled first; that keeps water from being released into
     * the hole by mining something underneath it.
     */
    @Unique
    private BlockPos bcqf$blocker;
    /** Scan cursor over the mined region (top layer first); {@code bcqf$scanY == Integer.MIN_VALUE} = idle. */
    @Unique
    private int bcqf$scanX;
    @Unique
    private int bcqf$scanY = Integer.MIN_VALUE;
    @Unique
    private int bcqf$scanZ;
    @Unique
    private int bcqf$scanCooldown;
    private static final boolean DEBUG = System.getProperty("bcqf.debug") != null;
    private static final int SCAN_BUDGET_PER_TICK = 4096;
    private static final int SCAN_COOLDOWN_TICKS = 40;

    /** Server tick: advance the top-down scan of the mined region by a fixed budget of block checks. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void bcqf$scanMinedRegion(CallbackInfo ci) {
        TileQuarry self = (TileQuarry) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        TileQuarryAccessor acc = (TileQuarryAccessor) this;
        Box box = acc.bcqf$getMiningBox();
        if (box == null || !box.isInitialized()) {
            return;
        }
        if (bcqf$blocker != null) {
            if (!acc.bcqf$canMoveThrough(bcqf$blocker) && acc.bcqf$canMine(bcqf$blocker)) {
                return; // still there, the drill is working on it
            }
            bcqf$blocker = null;
            bcqf$scanY = Integer.MIN_VALUE; // something changed: restart from the top
            bcqf$scanCooldown = 0;
        }
        if (bcqf$scanCooldown > 0) {
            bcqf$scanCooldown--;
            return;
        }
        BlockPos min = box.min();
        BlockPos max = box.max();
        BoxIterator iterator = acc.bcqf$getBoxIterator();
        BlockPos current = iterator != null && iterator.hasNext() ? iterator.getCurrent() : null;
        // everything above the layer the drill is working on counts as "already mined"; a finished pit entirely
        int floorY = current != null ? current.getY() : min.getY() - 1;
        if (bcqf$scanY == Integer.MIN_VALUE) {
            bcqf$scanY = max.getY();
            bcqf$scanX = min.getX();
            bcqf$scanZ = min.getZ();
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int budget = SCAN_BUDGET_PER_TICK; budget > 0; budget--) {
            if (bcqf$scanY <= floorY) {
                // clean pass finished: nothing new above the drill, look again in a moment
                bcqf$scanY = Integer.MIN_VALUE;
                bcqf$scanCooldown = SCAN_COOLDOWN_TICKS;
                return;
            }
            pos.set(bcqf$scanX, bcqf$scanY, bcqf$scanZ);
            if (!acc.bcqf$canMoveThrough(pos) && acc.bcqf$canMine(pos)) {
                bcqf$blocker = pos.immutable();
                bcqf$scanY = Integer.MIN_VALUE;
                if (DEBUG) {
                    bcquarryfluids.BcQuarryFluids.LOGGER.info("[bcqf] blocker found at {} (iterator current {}, hasNext {})", bcqf$blocker,
                        current, iterator != null && iterator.hasNext());
                }
                return;
            }
            if (++bcqf$scanX > max.getX()) {
                bcqf$scanX = min.getX();
                if (++bcqf$scanZ > max.getZ()) {
                    bcqf$scanZ = min.getZ();
                    bcqf$scanY--;
                }
            }
        }
    }

    /**
     * Upstream skips a column entirely when something above the current block is in the way
     * ({@code canMoveDownTo} fails, iterator advances). With a blocker pending the iterator stays put; otherwise a
     * block above the current column becomes the blocker immediately (the scan would find it a moment later anyway).
     */
    @Inject(method = "advancePastNonWorkableBlocks", at = @At("HEAD"), cancellable = true)
    private void bcqf$mineBlockersFirst(CallbackInfo ci) {
        TileQuarryAccessor self = (TileQuarryAccessor) this;
        BoxIterator iterator = self.bcqf$getBoxIterator();
        if (iterator == null) {
            return;
        }
        ci.cancel();
        if (bcqf$blocker != null) {
            return;
        }
        Box box = self.bcqf$getMiningBox();
        while (iterator.hasNext()) {
            BlockPos current = iterator.getCurrent();
            if (self.bcqf$canMoveThrough(current) || !self.bcqf$canMine(current)) {
                iterator.advance();
                continue;
            }
            BlockPos blocker = bcqf$topmostBlocker(self, box, current);
            if (blocker == null) {
                return; // column is clear, current block is the next target
            }
            if (self.bcqf$canMine(blocker)) {
                bcqf$blocker = blocker;
                return;
            }
            iterator.advance(); // unbreakable block above: skip the column like upstream does
        }
    }

    @Unique
    private static BlockPos bcqf$topmostBlocker(TileQuarryAccessor self, Box box, BlockPos current) {
        int top = box != null && box.isInitialized() ? box.max().getY() : current.getY();
        for (int y = top; y > current.getY(); y--) {
            BlockPos pos = new BlockPos(current.getX(), y, current.getZ());
            if (!self.bcqf$canMoveThrough(pos)) {
                return pos;
            }
        }
        return null;
    }

    /** While a blocker is pending, every use of the iterator position in {@code tick} targets the blocker instead. */
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lbuildcraft/lib/misc/data/BoxIterator;getCurrent()Lnet/minecraft/core/BlockPos;"))
    private BlockPos bcqf$currentOrBlocker(BoxIterator iterator) {
        if (bcqf$blocker != null) {
            if (DEBUG) {
                TileQuarry self = (TileQuarry) (Object) this;
                bcquarryfluids.BcQuarryFluids.LOGGER.info("[bcqf] targeting blocker {} (drill {}, task {})", bcqf$blocker, self.drillPos, self.currentTask);
            }
            return bcqf$blocker;
        }
        return iterator.getCurrent();
    }

    /** A pending blocker is work even when the pit is finished, so a finished quarry still cleans up placed blocks. */
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lbuildcraft/lib/misc/data/BoxIterator;hasNext()Z"))
    private boolean bcqf$hasNextOrBlocker(BoxIterator iterator) {
        return bcqf$blocker != null || iterator.hasNext();
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
