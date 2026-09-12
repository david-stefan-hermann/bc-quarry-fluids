package bcquarryfluids;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Recognises Refined Storage's fortune / silk touch upgrade items by id, so no compile dependency on RS is needed. */
public final class Upgrades {
    private static final String RS = "refinedstorage";

    private Upgrades() {
    }

    /** @return 1-3 for a fortune upgrade, 0 otherwise */
    public static int fortuneLevel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!RS.equals(id.getNamespace())) {
            return 0;
        }
        return switch (id.getPath()) {
            case "fortune_1_upgrade" -> 1;
            case "fortune_2_upgrade" -> 2;
            case "fortune_3_upgrade" -> 3;
            default -> 0;
        };
    }

    public static boolean isSilkTouch(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return RS.equals(id.getNamespace()) && "silk_touch_upgrade".equals(id.getPath());
    }

    public static boolean isUpgrade(ItemStack stack) {
        return fortuneLevel(stack) > 0 || isSilkTouch(stack);
    }

    /** Highest fortune level among the installed upgrades. */
    public static int fortuneLevel(Container upgrades) {
        int best = 0;
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            best = Math.max(best, fortuneLevel(upgrades.getItem(i)));
        }
        return best;
    }

    public static boolean hasSilkTouch(Container upgrades) {
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            if (isSilkTouch(upgrades.getItem(i))) {
                return true;
            }
        }
        return false;
    }
}
