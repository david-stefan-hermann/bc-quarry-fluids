package bcquarryfluids;

import net.minecraft.world.SimpleContainer;

/** Implemented on BuildCraft's {@code TileQuarry} by {@code TileQuarryMixin}. */
public interface QuarryExtras {
    MultiFluidTank bcqf$getTank();

    /** Mined items land here first; pipes and importers pull from it. */
    SimpleContainer bcqf$getInventory();

    /** Four slots for Refined Storage fortune / silk touch upgrades. */
    SimpleContainer bcqf$getUpgrades();
}
