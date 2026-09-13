package bcquarryfluids.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Sent to the client when the quarry menu opens: where the quarry is and how the server configured its tank. */
public record QuarryMenuData(BlockPos pos, int fluidSlots, int capacityMb) {
    public static final StreamCodec<RegistryFriendlyByteBuf, QuarryMenuData> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, QuarryMenuData::pos,
        ByteBufCodecs.VAR_INT, QuarryMenuData::fluidSlots,
        ByteBufCodecs.VAR_INT, QuarryMenuData::capacityMb,
        QuarryMenuData::new
    );
}
