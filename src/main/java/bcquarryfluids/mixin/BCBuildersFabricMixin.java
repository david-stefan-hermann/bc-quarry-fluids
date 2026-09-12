package bcquarryfluids.mixin;

import bcquarryfluids.QuarryExtras;
import buildcraft.builders.platform.BCBuildersFabric;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.BiFunction;

/**
 * BuildCraft registers a dummy item storage for the quarry (pipes connect, but nothing can be pulled). Replace that
 * first registration with an extract-only view of the quarry inventory, so pipes and importers can take items out.
 */
@Mixin(BCBuildersFabric.class)
public abstract class BCBuildersFabricMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(
        method = "registerNativeTransfer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/fabricmc/fabric/api/lookup/v1/block/BlockApiLookup;registerForBlockEntity(Ljava/util/function/BiFunction;Lnet/minecraft/world/level/block/entity/BlockEntityType;)V",
            ordinal = 0
        )
    )
    private static void bcqf$exposeQuarryInventory(BlockApiLookup lookup, BiFunction provider, BlockEntityType type) {
        BlockApiLookup<Object, Direction> typed = lookup;
        typed.registerForBlockEntity((BlockEntity blockEntity, Direction direction) -> blockEntity instanceof QuarryExtras quarry
            ? FilteringStorage.extractOnlyOf(ContainerStorage.of(quarry.bcqf$getInventory(), direction))
            : null, type);
    }
}
