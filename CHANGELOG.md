# Changelog

## 1.0.5
- Fixed the drill flying off to a far corner of the mining area (sometimes far enough to hang the quarry) right after placing a new quarry, before it had mined anything. The "placed-block scanner" mistook "not started yet" for "the whole pit is already mined" and chased a phantom blocker at the untouched far corner.

## 1.0.4
- First public release on CurseForge and Modrinth.
- Warns in the log when an untested BuildCraft Refabricated build is installed (tested with 26.7.27+mc26.2).

## 1.0.3
- Pump mode: a fluid source pulls the connected sources of that fluid on the layer and pumps the whole pool in one tick (as many as fit into the tank), which defeats vanilla infinite water. Cost `pumpMjPerBucket` per source block; waits and retries once a second when the tank has no room.

## 1.0.2
- Flowing fluid is ignored, only source blocks are pumped.
- Blocks and fluid sources placed anywhere in the already mined pit are found by a top-down scan and mined first, highest block first, also after the quarry has finished.

## 1.0.1
- Tank gauges show the fluid's real texture and tint.
- Blocks placed on an already mined layer are mined before the drill continues.
- The menu opens with any item in hand except a BuildCraft wrench.
- New icon.

## 1.0.0
- Fluid mining with a multi-fluid tank, 3 x 3 quarry inventory, Refined Storage fortune / silk touch upgrade slots, GUI with BuildCraft-style gauges.
