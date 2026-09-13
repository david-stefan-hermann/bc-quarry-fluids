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
 * grid and BuildCraft-style tank gauges, plus the Refined-Storage-style upgrade side panel on the right. Panel
 * corners follow the vanilla container texture pixel for pixel (transparent corner, 1 px black border, 2 px
 * highlight / shadow).
 */
public class QuarryScreen extends AbstractContainerScreen<QuarryMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int BORDER = 0xFF000000;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SHADOW = 0xFF555555;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int TICK = 0xFF000000;
    private static final int GAUGE_MAX_WIDTH = 16;

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

    /** Dev aid (see {@link bcquarryfluids.BcQuarryFluids#SCREENSHOT_PROPERTY}): save a screenshot, then quit. */
    private int screenshotTicks = 0;

    @Override
    protected void containerTick() {
        super.containerTick();
        if (System.getProperty(bcquarryfluids.BcQuarryFluids.SCREENSHOT_PROPERTY) == null) {
            return;
        }
        screenshotTicks++;
        if (screenshotTicks == 40) {
            net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, "bcqf-quarry.png", minecraft.gameRenderer.mainRenderTarget(), 1,
                message -> bcquarryfluids.BcQuarryFluids.LOGGER.info("Screenshot: {}", message.getString()));
        } else if (screenshotTicks == 80) {
            minecraft.stop();
        }
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

        List<Component> gaugeTooltip = drawGauges(graphics, mouseX, mouseY);
        if (gaugeTooltip != null) {
            tooltip = gaugeTooltip;
        }
        if (tooltip != null) {
            graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    /** One BuildCraft-style vertical gauge per tank slot, spread over the area right of the item grid. */
    private List<Component> drawGauges(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int slots = menu.data.fluidSlots();
        int capacity = Math.max(1, menu.data.capacityMb());
        int gaugeW = Math.min(GAUGE_MAX_WIDTH, (QuarryMenu.TANK_WIDTH - 4 * (slots - 1)) / slots);
        int gap = slots > 1 ? (QuarryMenu.TANK_WIDTH - gaugeW * slots) / (slots - 1) : 0;
        int gaugeH = QuarryMenu.TANK_HEIGHT;
        List<Component> tooltip = null;

        for (int i = 0; i < slots; i++) {
            int x = leftPos + QuarryMenu.TANK_X + i * (gaugeW + gap);
            int y = topPos + QuarryMenu.TANK_Y;
            int fluidId = menu.tankFluidId(i);
            int amount = menu.tankAmountMb(i);
            Fluid fluid = fluidId < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(fluidId);
            boolean filled = fluid != Fluids.EMPTY && amount > 0;

            // fluid level on the bare panel (no frame, no background), BuildCraft's scale on top of it
            if (filled) {
                int level = Math.max(1, Math.min(gaugeH, (int) ((long) gaugeH * amount / capacity)));
                graphics.fill(x, y + gaugeH - level, x + gaugeW, y + gaugeH, fluidColor(fluid));
            }
            drawScale(graphics, x, y, gaugeH, gaugeW);

            if (mouseX >= x && mouseX < x + gaugeW && mouseY >= y && mouseY < y + gaugeH) {
                tooltip = filled
                    ? List.of(FluidVariantAttributes.getName(FluidVariant.of(fluid)), Component.literal(amount + " / " + capacity + " mB"))
                    : List.of(Component.translatable("gui.bcquarryfluids.tank_empty"), Component.literal("0 / " + capacity + " mB"));
            }
        }
        return tooltip;
    }

    /**
     * BuildCraft's tank overlay, redrawn in black: a tick every 4 px, 5 px long; 8 px at the quarter marks,
     * the full width at the half mark and 7 px at the top.
     */
    private static void drawScale(GuiGraphicsExtractor graphics, int x, int y, int height, int width) {
        for (int row = 0; row < height; row += 4) {
            int length;
            if (row == height / 2) {
                length = width;
            } else if (row == height / 4 || row == height * 3 / 4) {
                length = 8;
            } else if (row == 0) {
                length = 7;
            } else {
                length = 5;
            }
            graphics.fill(x, y + row, x + Math.min(length, width), y + row + 1, TICK);
        }
    }

    /** Vanilla container panel: transparent corner pixels, 1 px black border, 2 px white highlight, 2 px shadow. */
    private static void drawPanel(GuiGraphicsExtractor graphics, int x0, int y0, int w, int h) {
        int x1 = x0 + w;
        int y1 = y0 + h;
        // border (corners left out: the two outermost pixels of each corner stay transparent)
        graphics.fill(x0 + 2, y0, x1 - 2, y0 + 1, BORDER);
        graphics.fill(x0 + 2, y1 - 1, x1 - 2, y1, BORDER);
        graphics.fill(x0, y0 + 2, x0 + 1, y1 - 2, BORDER);
        graphics.fill(x1 - 1, y0 + 2, x1, y1 - 2, BORDER);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y0 + 2, BORDER);
        graphics.fill(x1 - 2, y0 + 1, x1 - 1, y0 + 2, BORDER);
        graphics.fill(x0 + 1, y1 - 2, x0 + 2, y1 - 1, BORDER);
        graphics.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, BORDER);
        // face
        graphics.fill(x0 + 2, y0 + 1, x1 - 2, y1 - 1, PANEL);
        graphics.fill(x0 + 1, y0 + 2, x1 - 1, y1 - 2, PANEL);
        // highlight: two rows at the top, two columns on the left, with the vanilla inner rounding
        graphics.fill(x0 + 2, y0 + 1, x1 - 3, y0 + 2, LIGHT);
        graphics.fill(x0 + 1, y0 + 2, x1 - 3, y0 + 3, LIGHT);
        graphics.fill(x0 + 1, y0 + 3, x0 + 3, y1 - 3, LIGHT);
        graphics.fill(x0 + 3, y0 + 3, x0 + 4, y0 + 4, LIGHT);
        // shadow: two rows at the bottom, two columns on the right
        graphics.fill(x0 + 3, y1 - 2, x1 - 2, y1 - 1, SHADOW);
        graphics.fill(x0 + 3, y1 - 3, x1 - 1, y1 - 2, SHADOW);
        graphics.fill(x1 - 3, y0 + 3, x1 - 1, y1 - 3, SHADOW);
        graphics.fill(x1 - 4, y1 - 4, x1 - 3, y1 - 3, SHADOW);
    }

    private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, SLOT);
        graphics.fill(x, y, x + 17, y + 1, SLOT_DARK);
        graphics.fill(x, y, x + 1, y + 17, SLOT_DARK);
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
