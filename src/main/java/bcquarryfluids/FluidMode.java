package bcquarryfluids;

/** What the quarry does with fluid blocks inside its mining area. */
public enum FluidMode {
    /** Upstream behaviour: fluids are left in place. */
    IGNORE,
    /** Fluid blocks are removed and the fluid is lost. */
    VOID,
    /** Fluid blocks are removed; source blocks fill the quarry's tank, which pushes into adjacent pipes and tanks. */
    COLLECT
}
