package bcquarryfluids.menu;

import bcquarryfluids.BcQuarryFluids;
import bcquarryfluids.Upgrades;
import buildcraft.builders.BCBuildersBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Layout in GUI pixels (origin = top-left of the main panel, which is the vanilla 176 x 166 dispenser size):
 * 3 x 3 quarry inventory at (8, 17), tank gauges to its right, player inventory at y 98. The four upgrade slots
 * sit in a separate side panel attached to the right edge, exactly where Refined Storage puts them (x 187, y 6 + 18 i).
 */
public class QuarryMenu extends AbstractContainerMenu {
    public static final int MAIN_WIDTH = 176;
    /** 180 like BuildCraft's tank screen: leaves room between the 64 px gauges and the player inventory. */
    public static final int MAIN_HEIGHT = 180;
    public static final int SIDE_X = 180;
    public static final int SIDE_WIDTH = 30;
    public static final int SIDE_HEIGHT = 82;
    public static final int TOTAL_WIDTH = SIDE_X + SIDE_WIDTH;
    public static final int INVENTORY_SLOTS = 9;
    public static final int UPGRADE_SLOTS = 4;
    public static final int GRID_X = 8;
    public static final int GRID_Y = 17;
    /** Area for the fluid reservoirs, right of the 3 x 3 grid. */
    public static final int TANK_X = 68;
    public static final int TANK_Y = 17;
    public static final int TANK_WIDTH = 100;
    /** Same height as BuildCraft's own tank gauge (its overlay is 16 x 64). */
    public static final int TANK_HEIGHT = 64;
    public static final int PLAYER_Y = 98;

    public final QuarryMenuData data;
    private final Container inventory;
    private final Container upgrades;
    /** [fluidRegistryId, amountMb] per tank slot. */
    private final ContainerData tankData;
    private final ContainerLevelAccess access;

    /** Client side: containers are placeholders, contents arrive through the normal slot sync. */
    public QuarryMenu(int containerId, Inventory playerInventory, QuarryMenuData data) {
        this(containerId, playerInventory, data,
            new SimpleContainer(INVENTORY_SLOTS), new SimpleContainer(UPGRADE_SLOTS),
            new SimpleContainerData(data.fluidSlots() * 2), ContainerLevelAccess.NULL);
    }

    public QuarryMenu(int containerId, Inventory playerInventory, QuarryMenuData data, Container inventory, Container upgrades,
                      ContainerData tankData, ContainerLevelAccess access) {
        super(BcQuarryFluids.QUARRY_MENU, containerId);
        this.data = data;
        this.inventory = inventory;
        this.upgrades = upgrades;
        this.tankData = tankData;
        this.access = access;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(inventory, row * 3 + col, GRID_X + col * 18, GRID_Y + row * 18));
            }
        }
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            addSlot(new UpgradeSlot(upgrades, i, 187, 6 + i * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, PLAYER_Y + 58));
        }
        addDataSlots(tankData);
    }

    public BlockPos pos() {
        return data.pos();
    }

    public int tankFluidId(int slot) {
        return tankData.get(slot * 2);
    }

    public int tankAmountMb(int slot) {
        return tankData.get(slot * 2 + 1);
    }

    public boolean isUpgradeSlot(Slot slot) {
        return slot instanceof UpgradeSlot;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return result;
        }
        ItemStack stack = slot.getItem();
        result = stack.copy();
        int quarryEnd = INVENTORY_SLOTS + UPGRADE_SLOTS;
        if (index < quarryEnd) {
            if (!moveItemStackTo(stack, quarryEnd, quarryEnd + 36, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = Upgrades.isUpgrade(stack) && moveItemStackTo(stack, INVENTORY_SLOTS, quarryEnd, false);
            if (!moved && !moveItemStackTo(stack, 0, INVENTORY_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, BCBuildersBlocks.QUARRY);
    }

    private static final class UpgradeSlot extends Slot {
        UpgradeSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return Upgrades.isUpgrade(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
