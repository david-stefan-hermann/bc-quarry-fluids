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

/** Draws the panel, slots and fluid gauges with plain rectangles, so it needs no texture and works for any row count. */
public class QuarryScreen extends AbstractContainerScreen<QuarryMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int DARK = 0xFF373737;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int GAUGE_BG = 0xFF1E1E1E;

    public QuarryScreen(QuarryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, QuarryMenu.PANEL_WIDTH, QuarryMenu.panelHeight(menu.data.rows()));
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = 8;
        inventoryLabelX = 8;
        inventoryLabelY = QuarryMenu.playerInventoryTop(menu.data.rows()) - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x0 = leftPos;
        int y0 = topPos;
        int x1 = leftPos + imageWidth;
        int y1 = topPos + imageHeight;
        graphics.fill(x0, y0, x1, y1, DARK);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, PANEL);
        graphics.fill(x0 + 1, y0 + 1, x1 - 2, y0 + 2, LIGHT);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 2, LIGHT);
        graphics.fill(x0 + 2, y1 - 2, x1 - 1, y1 - 1, 0xFF555555);
        graphics.fill(x1 - 2, y0 + 2, x1 - 1, y1 - 1, 0xFF555555);

        for (Slot slot : menu.slots) {
            drawSlot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1);
        }

        int slots = menu.data.fluidSlots();
        int capacity = Math.max(1, menu.data.capacityMb());
        List<Component> tooltip = null;
        for (int i = 0; i < slots; i++) {
            int gx = leftPos + QuarryMenu.GAUGE_X + (i % 2) * QuarryMenu.GAUGE_STEP_X;
            int gy = topPos + QuarryMenu.GAUGE_Y + (i / 2) * QuarryMenu.GAUGE_STEP_Y;
            graphics.fill(gx - 1, gy - 1, gx + QuarryMenu.GAUGE_W + 1, gy + QuarryMenu.GAUGE_H + 1, DARK);
            graphics.fill(gx, gy, gx + QuarryMenu.GAUGE_W, gy + QuarryMenu.GAUGE_H, GAUGE_BG);

            int fluidId = menu.tankFluidId(i);
            int amount = menu.tankAmountMb(i);
            Fluid fluid = fluidId < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(fluidId);
            if (fluid != Fluids.EMPTY && amount > 0) {
                int filled = Math.max(1, Math.min(QuarryMenu.GAUGE_H, (int) ((long) QuarryMenu.GAUGE_H * amount / capacity)));
                graphics.fill(gx, gy + QuarryMenu.GAUGE_H - filled, gx + QuarryMenu.GAUGE_W, gy + QuarryMenu.GAUGE_H, fluidColor(fluid));
            }
            if (mouseX >= gx && mouseX < gx + QuarryMenu.GAUGE_W && mouseY >= gy && mouseY < gy + QuarryMenu.GAUGE_H) {
                if (fluid == Fluids.EMPTY || amount <= 0) {
                    tooltip = List.of(Component.translatable("gui.bcquarryfluids.tank_empty"), Component.literal("0 / " + capacity + " mB"));
                } else {
                    tooltip = List.of(FluidVariantAttributes.getName(FluidVariant.of(fluid)), Component.literal(amount + " / " + capacity + " mB"));
                }
            }
        }
        if (tooltip != null) {
            graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
        }
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
