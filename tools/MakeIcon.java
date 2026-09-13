import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

/** Generates the mod icon: BuildCraft-yellow quarry frame around a water drop, 16 x 16 pixel art scaled to 128 px. */
public class MakeIcon {
    private static final String[] ROWS = {
        "KFFFFFFFFFFFFFFK",
        "FKFFFFFFFFFFFFKF",
        "FF............FF",
        "FF............FF",
        "FF......W.....FF",
        "FF.....WWW....FF",
        "FF....WWWWW...FF",
        "FF...WWWWWWW..FF",
        "FF..WWWWWWWWW.FF",
        "FF..WWLWWWWWW.FF",
        "FF..WWLWWWWWW.FF",
        "FF...WWWWWWW..FF",
        "FF....WWWWW...FF",
        "FF............FF",
        "FKFFFFFFFFFFFFKF",
        "KFFFFFFFFFFFFFFK",
    };
    private static final Map<Character, Integer> COLORS = Map.of(
        'K', 0xFF1A1A1A,
        'F', 0xFFE8B923,
        '.', 0xFF2E2E2E,
        'W', 0xFF3F76E4,
        'L', 0xFF9CC4FF
    );

    public static void main(String[] args) throws Exception {
        int scale = 8;
        BufferedImage img = new BufferedImage(16 * scale, 16 * scale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int argb = COLORS.get(ROWS[y].charAt(x));
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        img.setRGB(x * scale + dx, y * scale + dy, argb);
                    }
                }
            }
        }
        File out = new File(args[0]);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }
}
