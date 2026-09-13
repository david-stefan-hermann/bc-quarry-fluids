package bcquarryfluids;

import net.minecraft.world.SimpleContainer;

/** Implemented on BuildCraft's {@code TileQuarry} by {@code TileQuarryMixin}. */
public interface QuarryExtras {
    MultiFluidTank bcqf$getTank();

    /** Mined items land here first; pipes and importers pull from it. */
    SimpleContainer bcqf$getInventory();

    /** Four slots for Refined Storage fortune / silk touch upgrades. */
    SimpleContainer bcqf$getUpgrades();

    /** Game time before which pumping is not retried (set when the tank had no room). */
    long bcqf$getPumpRetryAt();

    void bcqf$setPumpRetryAt(long gameTime);
}
