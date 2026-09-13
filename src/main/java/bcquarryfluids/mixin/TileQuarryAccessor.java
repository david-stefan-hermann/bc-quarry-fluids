package bcquarryfluids.mixin;

import buildcraft.builders.tile.TileQuarry;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TileQuarry.class)
public interface TileQuarryAccessor {
    @Invoker("check")
    void bcqf$check(BlockPos pos);

    @Invoker("advanceMiningIteratorPast")
    void bcqf$advanceMiningIteratorPast(BlockPos pos);

    @Accessor("blockPercentSoFar")
    double bcqf$getBlockPercentSoFar();

    @Accessor("blockPercentSoFar")
    void bcqf$setBlockPercentSoFar(double value);
}
