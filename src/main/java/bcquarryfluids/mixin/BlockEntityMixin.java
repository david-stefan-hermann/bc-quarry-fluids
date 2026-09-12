package bcquarryfluids.mixin;

import bcquarryfluids.QuarryExtras;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drops the quarry inventory and upgrades when the quarry block is removed (fluid in the tank is lost). */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {
    @Shadow
    protected Level level;

    @Inject(method = "preRemoveSideEffects", at = @At("HEAD"))
    private void bcqf$dropQuarryContents(BlockPos pos, BlockState state, CallbackInfo ci) {
        if ((Object) this instanceof QuarryExtras quarry && level != null && !level.isClientSide()) {
            Containers.dropContents(level, pos, quarry.bcqf$getInventory());
            Containers.dropContents(level, pos, quarry.bcqf$getUpgrades());
        }
    }
}
