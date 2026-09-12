package bcquarryfluids.mixin;

import bcquarryfluids.Config;
import bcquarryfluids.FluidMode;
import bcquarryfluids.MultiFluidTank;
import bcquarryfluids.QuarryExtras;
import bcquarryfluids.Upgrades;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.lib.fluid.stack.FluidStack;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.data.Box;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Two jobs when a break task completes:
 * <ol>
 *   <li>Fluid blocks: drain the whole mining layer at once. Removing them one by one never finishes, because two
 *       neighbouring source blocks re-create a source in the gap (vanilla "infinite water"). Sources are worth one
 *       bucket; in COLLECT mode that bucket goes into the quarry tank, and if it does not fit the task reports
 *       failure without advancing, so the quarry retries until the tank is emptied (like the BuildCraft pump).</li>
 *   <li>Solid blocks: the pickaxe BuildCraft breaks with gets the fortune / silk touch level of the installed
 *       Refined Storage upgrades, and the drops go into the quarry inventory before anything reaches neighbours.</li>
 * </ol>
 */
@Mixin(TileQuarry.TaskBreakBlock.class)
public abstract class TaskBreakBlockMixin {
    @Shadow
    public BlockPos breakPos;

    /** Synthetic reference to the enclosing {@code TileQuarry} (non-static inner class). */
    @Shadow
    @Final
    private TileQuarry this$0;

    @Inject(method = "finish", at = @At("HEAD"), cancellable = true)
    private void bcqf$finishFluidBlock(long added, long target, CallbackInfoReturnable<Boolean> cir) {
        if (Config.mode() == FluidMode.IGNORE || !(this$0.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getBlockState(breakPos).getBlock() instanceof LiquidBlock)) {
            return; // solid or waterlogged block: upstream breaks it, the RETURN hook below cleans up leftovers
        }

        TileQuarryAccessor quarry = (TileQuarryAccessor) this$0;
        quarry.bcqf$setBlockPercentSoFar(quarry.bcqf$getBlockPercentSoFar() + (double) added / target);
        level.destroyBlockProgress(breakPos.hashCode(), breakPos, -1);

        bcqf$drainLayer(level, quarry.bcqf$getMiningBox(), breakPos.getY());

        if (level.getBlockState(breakPos).getBlock() instanceof LiquidBlock) {
            // Tank full (or holds other fluids): do not advance, upstream refunds the power, retry next tick.
            cir.setReturnValue(false);
            return;
        }
        quarry.bcqf$check(breakPos);
        quarry.bcqf$advanceMiningIteratorPast(breakPos);
        cir.setReturnValue(true);
    }

    /** Clears every fluid block of one Y layer inside the mining box in a single tick. */
    private void bcqf$drainLayer(ServerLevel level, Box box, int y) {
        if (box == null || !box.isInitialized()) {
            bcqf$removeFluid(level, breakPos);
            return;
        }
        BlockPos min = box.min();
        BlockPos max = box.max();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                pos.set(x, y, z);
                if (level.getBlockState(pos).getBlock() instanceof LiquidBlock) {
                    bcqf$removeFluid(level, pos.immutable());
                }
            }
        }
    }

    /** @return true if the block was cleared (false only when a source could not be stored in COLLECT mode) */
    private boolean bcqf$removeFluid(ServerLevel level, BlockPos pos) {
        FluidState fluidState = level.getFluidState(pos);
        if (Config.mode() == FluidMode.COLLECT && fluidState.isSource() && !bcqf$collect(fluidState.getType(), FluidConstants.BUCKET)) {
            return false;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        return true;
    }

    /** Breaking a waterlogged block (kelp, seagrass, slabs ...) leaves its water behind; take that too. */
    @Inject(method = "finish", at = @At("RETURN"))
    private void bcqf$clearLeftoverFluid(long added, long target, CallbackInfoReturnable<Boolean> cir) {
        if (Config.mode() == FluidMode.IGNORE || !(this$0.getLevel() instanceof ServerLevel level)) {
            return;
        }
        FluidState fluidState = level.getFluidState(breakPos);
        if (fluidState.isEmpty() || !(level.getBlockState(breakPos).getBlock() instanceof LiquidBlock)) {
            return;
        }
        if (Config.mode() == FluidMode.COLLECT && fluidState.isSource()) {
            bcqf$collect(fluidState.getType(), FluidConstants.BUCKET); // best effort: the iterator has moved on, overflow is voided
        }
        level.setBlock(breakPos, Blocks.AIR.defaultBlockState(), 3);
    }

    /** @return true if the whole amount was stored in the quarry tank */
    private boolean bcqf$collect(net.minecraft.world.level.material.Fluid fluid, long droplets) {
        MultiFluidTank tank = ((QuarryExtras) this$0).bcqf$getTank();
        try (Transaction transaction = Transaction.openOuter()) {
            if (tank.insert(FluidVariant.of(fluid), droplets, transaction) < droplets) {
                transaction.abort();
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    /**
     * Wraps the upstream block break: enchanted tool in, drops into the quarry inventory out. Whatever does not fit
     * is handed back to upstream, which pushes it to neighbours or drops it on the ground as before.
     */
    @Redirect(
        method = "finish",
        at = @At(
            value = "INVOKE",
            target = "Lbuildcraft/lib/misc/BlockUtil;breakBlockAndGetDropsWithXp(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/authlib/GameProfile;)Ljava/util/Optional;"
        )
    )
    private Optional<BlockUtil.BreakResult> bcqf$breakWithUpgrades(ServerLevel level, BlockPos pos, ItemStack tool, GameProfile owner) {
        QuarryExtras quarry = (QuarryExtras) this$0;
        Optional<BlockUtil.BreakResult> result = BlockUtil.breakBlockAndGetDropsWithXp(level, pos, bcqf$tool(level, quarry, tool), owner);
        if (result.isEmpty()) {
            return result;
        }
        BlockUtil.BreakResult broken = result.get();
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack drop : broken.drops()) {
            ItemStack rest = quarry.bcqf$getInventory().addItem(drop);
            if (!rest.isEmpty()) {
                remaining.add(rest);
            }
        }
        FluidStack captured = broken.capturedFluid();
        if (captured != null && !captured.isEmpty() && Config.mode() == FluidMode.COLLECT) {
            bcqf$collect(captured.getFluid(), (long) captured.getAmount() * (FluidConstants.BUCKET / 1000));
        }
        return Optional.of(new BlockUtil.BreakResult(remaining, broken.xp(), FluidStack.EMPTY));
    }

    private static ItemStack bcqf$tool(ServerLevel level, QuarryExtras quarry, ItemStack upstreamTool) {
        int fortune = Upgrades.fortuneLevel(quarry.bcqf$getUpgrades());
        boolean silkTouch = Upgrades.hasSilkTouch(quarry.bcqf$getUpgrades());
        if (fortune == 0 && !silkTouch) {
            return upstreamTool;
        }
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (silkTouch) {
            Holder<Enchantment> silk = enchantments.getOrThrow(Enchantments.SILK_TOUCH);
            tool.enchant(silk, 1);
        } else {
            Holder<Enchantment> fortuneHolder = enchantments.getOrThrow(Enchantments.FORTUNE);
            tool.enchant(fortuneHolder, fortune);
        }
        return tool;
    }
}
