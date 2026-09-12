package bcquarryfluids;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * {@code config/bcquarryfluids.json}:
 * <ul>
 *   <li>{@code mode}: IGNORE / VOID / COLLECT</li>
 *   <li>{@code tankCapacityMb}: capacity of each fluid slot of the quarry tank</li>
 *   <li>{@code fluidSlots}: how many different fluids the tank holds at once (1-8)</li>
 *   <li>{@code inventoryRows}: rows of 9 slots in the quarry inventory (4-9)</li>
 * </ul>
 */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "bcquarryfluids.json";

    private static volatile FluidMode mode = FluidMode.COLLECT;
    private static volatile int tankCapacityMb = 16000;
    private static volatile int fluidSlots = 6;
    private static volatile int inventoryRows = 6;

    private Config() {
    }

    public static FluidMode mode() {
        return mode;
    }

    public static int tankCapacityMb() {
        return tankCapacityMb;
    }

    public static int fluidSlots() {
        return fluidSlots;
    }

    public static int inventoryRows() {
        return inventoryRows;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (!Files.exists(path)) {
            write(path);
            BcQuarryFluids.LOGGER.info("Created default config at {}", path.toAbsolutePath());
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("mode")) {
                String value = root.get("mode").getAsString().trim().toUpperCase(Locale.ROOT);
                try {
                    mode = FluidMode.valueOf(value);
                } catch (IllegalArgumentException e) {
                    BcQuarryFluids.LOGGER.warn("Unknown mode '{}' in {}, keeping {}", value, FILE_NAME, mode);
                }
            }
            tankCapacityMb = clamp(root, "tankCapacityMb", tankCapacityMb, 1000, 1_000_000);
            fluidSlots = clamp(root, "fluidSlots", fluidSlots, 1, 8);
            inventoryRows = clamp(root, "inventoryRows", inventoryRows, 4, 9);
        } catch (Exception e) {
            BcQuarryFluids.LOGGER.error("Failed to read {}, using defaults", path, e);
        }
        BcQuarryFluids.LOGGER.info("Quarry fluid mode: {}, {} x {} mB tank slots, {} inventory rows", mode, fluidSlots, tankCapacityMb, inventoryRows);
    }

    private static int clamp(JsonObject root, String key, int fallback, int min, int max) {
        if (!root.has(key)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, root.get(key).getAsInt()));
    }

    private static void write(Path path) {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "mode: IGNORE (leave fluids like upstream), VOID (delete them), COLLECT (fill the quarry tank, pushed to adjacent pipes/tanks). "
            + "tankCapacityMb per fluid slot, fluidSlots 1-8, inventoryRows 4-9 (9 needs GUI scale 3 or lower).");
        root.addProperty("mode", mode.name());
        root.addProperty("tankCapacityMb", tankCapacityMb);
        root.addProperty("fluidSlots", fluidSlots);
        root.addProperty("inventoryRows", inventoryRows);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            BcQuarryFluids.LOGGER.error("Failed to write {}", path, e);
        }
    }
}
