# BuildCraft Quarry Extras

Add-on for **[BuildCraft Refabricated](https://modrinth.com/mod/buildcraftrefabricated)** (Fabric, Minecraft 26.2). It teaches the quarry a few things the classic one never learned, without touching the BuildCraft jar.

![Quarry GUI](https://raw.githubusercontent.com/david-stefan-hermann/bc-quarry-fluids/main/docs/quarry-gui.png)

## What the quarry gets

**Fluids are pumped, not skipped.** When the drill reaches a fluid source, the quarry collects the connected sources of that fluid on the layer and pumps the whole pool at once. Pumping in one tick is what beats vanilla "infinite water"; nothing can flow back into the gaps. Water, lava, BuildCraft oil, any mod fluid. Flowing fluid is ignored and drains by itself.

**A multi-fluid tank.** Four slots of 16 buckets by default, one fluid per slot. The tank pushes into adjacent fluid pipes and tanks and can be pulled from by Refined Storage importers and external storages or anything else that speaks the Fabric fluid API. When the tank is full the quarry waits, like the BuildCraft pump. Prefer to just get rid of the fluid? Set `mode` to `VOID`.

**Its own inventory.** Mined items land in a 3 x 3 inventory inside the quarry first. Only the overflow goes to neighbours or the ground as before. Pipes and importers can extract from it.

**Refined Storage upgrades.** Four upgrade slots take RS Fortune I-III and Silk Touch upgrades. The highest fortune level (or silk touch) is applied to every block the quarry breaks.

**A GUI.** Right-click the quarry: inventory, BuildCraft-style gauges with the real fluid textures, upgrade slots in a Refined-Storage-style side panel.

**No skipped blocks.** Anything placed into the already mined pit is found by a top-down scan and mined before the drill continues, highest block first, also after the quarry has finished.

## Config

`config/bcquarryfluids.json`: `mode` (`COLLECT` / `VOID` / `IGNORE`), `tankCapacityMb`, `fluidSlots` (1-8), `pumpMjPerBucket`.

## Requirements

Minecraft 26.2, Fabric Loader, Fabric API and **BuildCraft Refabricated 26.7.27+mc26.2** (the build the mixins were tested with; other builds log a warning). Refined Storage is optional and only needed for the upgrades. Install on server and client.

## Source

MIT, on [GitHub](https://github.com/david-stefan-hermann/bc-quarry-fluids). Issues and ideas welcome there.
