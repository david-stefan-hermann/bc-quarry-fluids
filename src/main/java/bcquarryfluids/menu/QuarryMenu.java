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
 * Layout (in GUI pixels, origin = top-left of the panel): quarry inventory 9 x rows starting at (8, 18); four upgrade
 * slots at (180|198, 18|36); fluid gauges below them; player inventory under the quarry inventory.
 */
public class QuarryMenu extends AbstractContainerMenu {
    public static final int PANEL_WIDTH = 220;
    public static final int UPGRADE_SLOTS = 4;
    public static final int GAUGE_X = 180;
    public static final int GAUGE_Y = 58;
    public static final int GAUGE_W = 16;
    public static final int GAUGE_H = 20;
    public static final int GAUGE_STEP_X = 18;
    public static final int GAUGE_STEP_Y = 22;

    public final QuarryMenuData data;
    private final Container inventory;
    private final Container upgrades;
    /** [fluidRegistryId, amountMb] per tank slot. */
    private final ContainerData tankData;
    private final ContainerLevelAccess access;

    /** Client side: containers are placeholders, contents arrive through the normal slot sync. */
    public QuarryMenu(int containerId, Inventory playerInventory, QuarryMenuData data) {
        this(containerId, playerInventory, data,
            new SimpleContainer(data.rows() * 9), new SimpleContainer(UPGRADE_SLOTS),
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

        int rows = data.rows();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, row * 9 + col, 8 + col * 18, 18 + row * 18));
            }
        }
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            addSlot(new UpgradeSlot(upgrades, i, 180 + (i % 2) * 18, 18 + (i / 2) * 18));
        }
        int top = playerInventoryTop(rows);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, top + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, top + 58));
        }
        addDataSlots(tankData);
    }

    public static int playerInventoryTop(int rows) {
        return 18 + rows * 18 + 14;
    }

    public static int panelHeight(int rows) {
        return playerInventoryTop(rows) + 76 + 6;
    }

    public int quarrySlotCount() {
        return data.rows() * 9;
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

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return result;
        }
        ItemStack stack = slot.getItem();
        result = stack.copy();
        int quarryEnd = quarrySlotCount() + UPGRADE_SLOTS;
        if (index < quarryEnd) {
            if (!moveItemStackTo(stack, quarryEnd, quarryEnd + 36, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = Upgrades.isUpgrade(stack) && moveItemStackTo(stack, quarrySlotCount(), quarryEnd, false);
            if (!moved && !moveItemStackTo(stack, 0, quarrySlotCount(), false)) {
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
