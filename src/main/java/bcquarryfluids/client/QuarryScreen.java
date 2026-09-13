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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Draws everything with plain rectangles, so no texture is needed: the main panel with the 3 x 3 grid and
 * BuildCraft-style tank gauges, plus the Refined-Storage-style upgrade side panel on the right. Panel corners follow
 * the vanilla container texture pixel for pixel. The gauges are holes in the panel: the (dimmed) world shows through
 * a translucent dark tint, the fluid level and BuildCraft's tick scale are drawn on top.
 */
public class QuarryScreen extends AbstractContainerScreen<QuarryMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int BORDER = 0xFF000000;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SHADOW = 0xFF555555;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int GAUGE_TINT = 0x40000000;
    private static final int GAUGE_FRAME = 0xFF373737;
    private static final int TICK = 0xFFE8E8E8;
    private static final int GAUGE_MAX_WIDTH = 16;

    /** Left edge of each gauge relative to the panel, computed once from the slot count. */
    private final int[] gaugeX;
    private final int gaugeWidth;

    public QuarryScreen(QuarryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, QuarryMenu.TOTAL_WIDTH, QuarryMenu.MAIN_HEIGHT);
        int slots = Math.max(1, menu.data.fluidSlots());
        gaugeWidth = Math.min(GAUGE_MAX_WIDTH, (QuarryMenu.TANK_WIDTH - 4 * (slots - 1)) / slots);
        int gap = slots > 1 ? (QuarryMenu.TANK_WIDTH - gaugeWidth * slots) / (slots - 1) : 0;
        gaugeX = new int[slots];
        for (int i = 0; i < slots; i++) {
            gaugeX[i] = QuarryMenu.TANK_X + i * (gaugeWidth + gap);
        }
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

        List<int[]> holes = new ArrayList<>();
        for (int gx : gaugeX) {
            holes.add(new int[]{leftPos + gx, topPos + QuarryMenu.TANK_Y, leftPos + gx + gaugeWidth, topPos + QuarryMenu.TANK_Y + QuarryMenu.TANK_HEIGHT});
        }
        drawPanel(graphics, leftPos, topPos, QuarryMenu.MAIN_WIDTH, QuarryMenu.MAIN_HEIGHT, holes);
        drawPanel(graphics, leftPos + QuarryMenu.SIDE_X, topPos, QuarryMenu.SIDE_WIDTH, QuarryMenu.SIDE_HEIGHT, List.of());

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

    /** One BuildCraft-style vertical gauge per tank slot in the holes left open by the panel. */
    private List<Component> drawGauges(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int capacity = Math.max(1, menu.data.capacityMb());
        int gaugeH = QuarryMenu.TANK_HEIGHT;
        List<Component> tooltip = null;

        for (int i = 0; i < gaugeX.length; i++) {
            int x = leftPos + gaugeX[i];
            int y = topPos + QuarryMenu.TANK_Y;
            int fluidId = menu.tankFluidId(i);
            int amount = menu.tankAmountMb(i);
            Fluid fluid = fluidId < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(fluidId);
            boolean filled = fluid != Fluids.EMPTY && amount > 0;

            // translucent tint over the world, a thin frame, the fluid level, BuildCraft's scale on top
            graphics.fill(x, y, x + gaugeWidth, y + gaugeH, GAUGE_TINT);
            graphics.fill(x - 1, y - 1, x + gaugeWidth + 1, y, GAUGE_FRAME);
            graphics.fill(x - 1, y + gaugeH, x + gaugeWidth + 1, y + gaugeH + 1, GAUGE_FRAME);
            graphics.fill(x - 1, y, x, y + gaugeH, GAUGE_FRAME);
            graphics.fill(x + gaugeWidth, y, x + gaugeWidth + 1, y + gaugeH, GAUGE_FRAME);
            if (filled) {
                int level = Math.max(1, Math.min(gaugeH, (int) ((long) gaugeH * amount / capacity)));
                drawFluid(graphics, fluid, x, y + gaugeH - level, gaugeWidth, level);
            }
            drawScale(graphics, x, y, gaugeH, gaugeWidth);

            if (mouseX >= x && mouseX < x + gaugeWidth && mouseY >= y && mouseY < y + gaugeH) {
                tooltip = filled
                    ? List.of(FluidVariantAttributes.getName(FluidVariant.of(fluid)), Component.literal(amount + " / " + capacity + " mB"))
                    : List.of(Component.translatable("gui.bcquarryfluids.tank_empty"), Component.literal("0 / " + capacity + " mB"));
            }
        }
        return tooltip;
    }

    /**
     * BuildCraft's tank overlay: a tick every 4 px, 5 px long; 8 px at the quarter marks, the full width at the half
     * mark and 7 px at the top.
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

    /**
     * Vanilla container panel: transparent corner pixels, 1 px black border, 2 px white highlight, 2 px shadow.
     * {@code holes} (absolute x0, y0, x1, y1; all in one horizontal band) are left unpainted.
     */
    private static void drawPanel(GuiGraphicsExtractor graphics, int x0, int y0, int w, int h, List<int[]> holes) {
        int x1 = x0 + w;
        int y1 = y0 + h;
        fillExcept(graphics, x0 + 1, y0 + 1, x1 - 1, y1 - 1, holes);
        // border (corners left out: the two outermost pixels of each corner stay transparent)
        graphics.fill(x0 + 2, y0, x1 - 2, y0 + 1, BORDER);
        graphics.fill(x0 + 2, y1 - 1, x1 - 2, y1, BORDER);
        graphics.fill(x0, y0 + 2, x0 + 1, y1 - 2, BORDER);
        graphics.fill(x1 - 1, y0 + 2, x1, y1 - 2, BORDER);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y0 + 2, BORDER);
        graphics.fill(x1 - 2, y0 + 1, x1 - 1, y0 + 2, BORDER);
        graphics.fill(x0 + 1, y1 - 2, x0 + 2, y1 - 1, BORDER);
        graphics.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, BORDER);
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

    /** Fills a rectangle with the panel colour, skipping the given holes (which must share one y range). */
    private static void fillExcept(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, List<int[]> holes) {
        if (holes.isEmpty()) {
            graphics.fill(x0, y0, x1, y1, PANEL);
            return;
        }
        int bandTop = holes.get(0)[1];
        int bandBottom = holes.get(0)[3];
        graphics.fill(x0, y0, x1, bandTop, PANEL);
        graphics.fill(x0, bandBottom, x1, y1, PANEL);
        int cursor = x0;
        for (int[] hole : holes) {
            graphics.fill(cursor, bandTop, hole[0], bandBottom, PANEL);
            cursor = hole[2];
        }
        graphics.fill(cursor, bandTop, x1, bandBottom, PANEL);
    }

    private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, SLOT);
        graphics.fill(x, y, x + 17, y + 1, SLOT_DARK);
        graphics.fill(x, y, x + 1, y + 17, SLOT_DARK);
        graphics.fill(x + 1, y + 17, x + 18, y + 18, LIGHT);
        graphics.fill(x + 17, y + 1, x + 18, y + 18, LIGHT);
    }

    /**
     * Tiles the fluid's real still texture (with its tint, e.g. biome water colour) into the rectangle, using
     * BuildCraft's appearance cache so mod fluids look exactly like in BuildCraft's own tanks. Falls back to the
     * map colour when the fluid has no sprite.
     */
    private void drawFluid(GuiGraphicsExtractor graphics, Fluid fluid, int x, int y, int width, int height) {
        buildcraft.lib.client.fluid.BcFluidAppearance appearance = buildcraft.lib.client.fluid.BcFluidAppearanceCache.get(fluid);
        if (appearance == null || appearance.sprite() == null) {
            graphics.fill(x, y, x + width, y + height, fluidColor(fluid));
            return;
        }
        int tint = 0xFF000000 | appearance.tint();
        graphics.enableScissor(x, y, x + width, y + height);
        // tiles are anchored to the bottom so the texture does not shift while the level rises
        for (int tileY = y + height - 16; tileY > y - 16; tileY -= 16) {
            for (int tileX = x; tileX < x + width; tileX += 16) {
                graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, appearance.sprite(), tileX, tileY, 16, 16, tint);
            }
        }
        graphics.disableScissor();
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
