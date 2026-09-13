import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Generates the mod icon procedurally: a BuildCraft-style hazard-striped quarry frame around a drill that dips into
 * water, 32 x 32 pixel art scaled up 8x to 256 px. Usage: java tools/MakeIcon.java <out.png>
 */
public class MakeIcon {
    private static final int SIZE = 32;
    private static final int SCALE = 8;

    private static final int TRANSPARENT = 0x00000000;
    private static final int BLACK = 0xFF141414;
    private static final int YELLOW = 0xFFE8B923;
    private static final int YELLOW_DARK = 0xFFC99A18;
    private static final int BACKGROUND = 0xFF262A30;
    private static final int STEEL = 0xFFA6A6A6;
    private static final int STEEL_LIGHT = 0xFFD0D0D0;
    private static final int STEEL_DARK = 0xFF5E5E5E;
    private static final int WATER = 0xFF3F76E4;
    private static final int WATER_LIGHT = 0xFF7FAAF2;
    private static final int WATER_DARK = 0xFF2F5FC0;

    private static final int[][] px = new int[SIZE][SIZE];

    public static void main(String[] args) throws Exception {
        // frame: black outline, 3 px hazard band, inner black line
        fill(0, 0, SIZE, SIZE, BLACK);
        for (int y = 1; y < SIZE - 1; y++) {
            for (int x = 1; x < SIZE - 1; x++) {
                boolean band = x <= 3 || y <= 3 || x >= SIZE - 4 || y >= SIZE - 4;
                boolean innerLine = x == 4 || y == 4 || x == SIZE - 5 || y == SIZE - 5;
                if (band) {
                    px[y][x] = ((x + y) / 3) % 2 == 0 ? YELLOW : BLACK;
                    if (px[y][x] == YELLOW && (x == 1 || y == 1)) {
                        px[y][x] = YELLOW_DARK;
                    }
                } else if (innerLine) {
                    px[y][x] = BLACK;
                } else {
                    px[y][x] = BACKGROUND;
                }
            }
        }
        // rounded corners like the vanilla GUI panels: two outermost pixels of each corner are transparent
        for (int[] c : new int[][]{{0, 0}, {1, 0}, {0, 1}, {SIZE - 1, 0}, {SIZE - 2, 0}, {SIZE - 1, 1},
            {0, SIZE - 1}, {1, SIZE - 1}, {0, SIZE - 2}, {SIZE - 1, SIZE - 1}, {SIZE - 2, SIZE - 1}, {SIZE - 1, SIZE - 2}}) {
            px[c[1]][c[0]] = TRANSPARENT;
        }

        // water at the bottom of the pit, with a wave line and a few highlights
        fill(5, 21, SIZE - 5, SIZE - 5, WATER);
        for (int x = 5; x < SIZE - 5; x++) {
            if (x % 4 < 2) {
                px[21][x] = WATER_LIGHT;
            } else {
                px[20][x] = WATER_LIGHT;
                px[21][x] = WATER;
            }
        }
        for (int x = 7; x < SIZE - 6; x += 5) {
            px[24][x] = WATER_LIGHT;
            px[25][x + 1] = WATER_DARK;
        }

        // gantry arm across the top of the pit
        fill(7, 6, SIZE - 7, 8, STEEL);
        fill(7, 6, SIZE - 7, 7, STEEL_LIGHT);
        fill(7, 8, SIZE - 7, 9, STEEL_DARK);
        // drill shaft hanging from the arm, dipping into the water
        fill(14, 9, 18, 19, STEEL);
        fill(14, 9, 15, 19, STEEL_LIGHT);
        fill(17, 9, 18, 19, STEEL_DARK);
        // drill bit: tapering, with thread grooves
        fill(13, 19, 19, 20, STEEL_DARK);
        fill(14, 20, 18, 21, STEEL);
        fill(14, 21, 18, 22, STEEL_DARK);
        fill(15, 22, 17, 23, STEEL);
        fill(15, 23, 17, 24, STEEL_DARK);
        fill(15, 24, 17, 25, STEEL_LIGHT);
        px[25][15] = STEEL_DARK;
        px[25][16] = STEEL_DARK;

        BufferedImage img = new BufferedImage(SIZE * SCALE, SIZE * SCALE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                for (int dy = 0; dy < SCALE; dy++) {
                    for (int dx = 0; dx < SCALE; dx++) {
                        img.setRGB(x * SCALE + dx, y * SCALE + dy, px[y][x]);
                    }
                }
            }
        }
        File out = new File(args[0]);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    private static void fill(int x0, int y0, int x1, int y1, int color) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                px[y][x] = color;
            }
        }
    }
}
