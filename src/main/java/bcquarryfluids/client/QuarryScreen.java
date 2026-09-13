package bcquarryfluids.client;

import bcquarryfluids.menu.QuarryMenu;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.List;
import java.util.Optional;

/**
 * Draws everything with plain rectangles, so no texture is needed: the dispenser-sized main panel with the 3 x 3
 * grid and the tank reservoirs, plus the Refined-Storage-style upgrade side panel on the right.
 */
public class QuarryScreen extends AbstractContainerScreen<QuarryMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int DARK = 0xFF373737;
    private static final int SHADOW = 0xFF555555;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int RESERVOIR_BG = 0x38000000;
    private static final int RESERVOIR_BORDER = 0xA0373737;
    private static final int GAP = 5;

    public QuarryScreen(QuarryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, QuarryMenu.TOTAL_WIDTH, QuarryMenu.MAIN_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        // Centre the main panel like a vanilla 176-wide screen; the side panel hangs off its right edge.
        leftPos = (width - QuarryMenu.MAIN_WIDTH) / 2;
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = QuarryMenu.PLAYER_Y - 12;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(graphics, leftPos, topPos, QuarryMenu.MAIN_WIDTH, QuarryMenu.MAIN_HEIGHT);
        drawPanel(graphics, leftPos + QuarryMenu.SIDE_X, topPos, QuarryMenu.SIDE_WIDTH, QuarryMenu.SIDE_HEIGHT);

        List<Component> tooltip = null;
        for (Slot slot : menu.slots) {
            drawSlot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1);
            if (menu.isUpgradeSlot(slot) && !slot.hasItem() && menu.getCarried().isEmpty()
                && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                tooltip = List.of(Component.translatable("gui.bcquarryfluids.upgrade_slot"),
                    Component.translatable("gui.bcquarryfluids.upgrade_slot.hint"));
            }
        }

        List<Component> tankTooltip = drawReservoirs(graphics, mouseX, mouseY);
        if (tankTooltip != null) {
            tooltip = tankTooltip;
        }
        if (tooltip != null) {
            graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    /** One reservoir per tank slot in a 2-row grid right of the item grid; returns the tooltip of the hovered one. */
    private List<Component> drawReservoirs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int slots = menu.data.fluidSlots();
        int capacity = Math.max(1, menu.data.capacityMb());
        int rows = slots >= 2 ? 2 : 1;
        int cols = (slots + rows - 1) / rows;
        int cellW = (QuarryMenu.TANK_WIDTH - (cols - 1) * GAP) / cols;
        int cellH = (QuarryMenu.TANK_HEIGHT - (rows - 1) * GAP) / rows;
        List<Component> tooltip = null;

        for (int i = 0; i < slots; i++) {
            int x = leftPos + QuarryMenu.TANK_X + (i % cols) * (cellW + GAP);
            int y = topPos + QuarryMenu.TANK_Y + (i / cols) * (cellH + GAP);
            graphics.fill(x, y, x + cellW, y + cellH, RESERVOIR_BG);
            graphics.fill(x, y, x + cellW, y + 1, RESERVOIR_BORDER);
            graphics.fill(x, y + cellH - 1, x + cellW, y + cellH, RESERVOIR_BORDER);
            graphics.fill(x, y, x + 1, y + cellH, RESERVOIR_BORDER);
            graphics.fill(x + cellW - 1, y, x + cellW, y + cellH, RESERVOIR_BORDER);

            int fluidId = menu.tankFluidId(i);
            int amount = menu.tankAmountMb(i);
            Fluid fluid = fluidId < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(fluidId);
            boolean filled = fluid != Fluids.EMPTY && amount > 0;
            if (filled) {
                int inner = cellH - 4;
                int level = Math.max(1, Math.min(inner, (int) ((long) inner * amount / capacity)));
                graphics.fill(x + 2, y + 2 + inner - level, x + cellW - 2, y + cellH - 2, fluidColor(fluid));
            }
            if (mouseX >= x && mouseX < x + cellW && mouseY >= y && mouseY < y + cellH) {
                tooltip = filled
                    ? List.of(FluidVariantAttributes.getName(FluidVariant.of(fluid)), Component.literal(amount + " / " + capacity + " mB"))
                    : List.of(Component.translatable("gui.bcquarryfluids.tank_empty"), Component.literal("0 / " + capacity + " mB"));
            }
        }
        return tooltip;
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, int x0, int y0, int w, int h) {
        int x1 = x0 + w;
        int y1 = y0 + h;
        graphics.fill(x0, y0, x1, y1, DARK);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, PANEL);
        graphics.fill(x0 + 1, y0 + 1, x1 - 2, y0 + 2, LIGHT);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 2, LIGHT);
        graphics.fill(x0 + 2, y1 - 2, x1 - 1, y1 - 1, SHADOW);
        graphics.fill(x1 - 2, y0 + 2, x1 - 1, y1 - 1, SHADOW);
    }

    private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, SLOT);
        graphics.fill(x, y, x + 17, y + 1, DARK);
        graphics.fill(x, y, x + 1, y + 17, DARK);
        graphics.fill(x + 1, y + 17, x + 18, y + 18, LIGHT);
        graphics.fill(x + 17, y + 1, x + 18, y + 18, LIGHT);
    }

    /** Map colour of the fluid's block (water blue, lava orange, oil black ...) as an opaque ARGB value. */
    private int fluidColor(Fluid fluid) {
        try {
            ClientLevel level = Minecraft.getInstance().level;
            BlockState state = fluid.defaultFluidState().createLegacyBlock();
            if (level != null && !state.isAir()) {
                return 0xFF000000 | state.getMapColor(level, menu.pos()).col;
            }
        } catch (RuntimeException ignored) {
            // fall through to the default colour
        }
        return 0xFF3F76E4;
    }
}
