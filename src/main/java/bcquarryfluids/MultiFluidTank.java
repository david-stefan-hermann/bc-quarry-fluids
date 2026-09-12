package bcquarryfluids;

import buildcraft.lib.nbt.BcValueIn;
import buildcraft.lib.nbt.BcValueOut;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** A tank with several slots, each holding one fluid up to the same capacity. Same-fluid slots are topped up first. */
public final class MultiFluidTank implements Storage<FluidVariant> {
    public record Entry(FluidVariant variant, long amount) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FluidVariant.CODEC.fieldOf("variant").forGetter(Entry::variant),
            Codec.LONG.fieldOf("amount").forGetter(Entry::amount)
        ).apply(instance, Entry::new));
        public static final Codec<List<Entry>> LIST_CODEC = CODEC.listOf();
    }

    private final List<SingleFluidStorage> slots = new ArrayList<>();
    private final long capacity;

    public MultiFluidTank(int slotCount, long capacityDroplets, Runnable onChange) {
        this.capacity = capacityDroplets;
        for (int i = 0; i < slotCount; i++) {
            slots.add(SingleFluidStorage.withFixedCapacity(capacityDroplets, onChange));
        }
    }

    public int slotCount() {
        return slots.size();
    }

    public SingleFluidStorage slot(int index) {
        return slots.get(index);
    }

    public long capacity() {
        return capacity;
    }

    public boolean isEmpty() {
        for (SingleFluidStorage slot : slots) {
            if (!slot.isResourceBlank()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        long inserted = 0;
        for (SingleFluidStorage slot : slots) {
            if (!slot.isResourceBlank() && slot.variant.equals(resource)) {
                inserted += slot.insert(resource, maxAmount - inserted, transaction);
                if (inserted >= maxAmount) {
                    return inserted;
                }
            }
        }
        for (SingleFluidStorage slot : slots) {
            if (slot.isResourceBlank()) {
                inserted += slot.insert(resource, maxAmount - inserted, transaction);
                if (inserted >= maxAmount) {
                    return inserted;
                }
            }
        }
        return inserted;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        long extracted = 0;
        for (SingleFluidStorage slot : slots) {
            extracted += slot.extract(resource, maxAmount - extracted, transaction);
            if (extracted >= maxAmount) {
                break;
            }
        }
        return extracted;
    }

    @Override
    public Iterator<StorageView<FluidVariant>> iterator() {
        return slots.stream().map(slot -> (StorageView<FluidVariant>) slot).iterator();
    }

    public void write(BcValueOut output, String key) {
        List<Entry> entries = new ArrayList<>();
        for (SingleFluidStorage slot : slots) {
            entries.add(new Entry(slot.variant, slot.amount));
        }
        output.store(key, Entry.LIST_CODEC, entries);
    }

    public void read(BcValueIn input, String key) {
        input.read(key, Entry.LIST_CODEC).ifPresent(entries -> {
            for (int i = 0; i < slots.size() && i < entries.size(); i++) {
                Entry entry = entries.get(i);
                SingleFluidStorage slot = slots.get(i);
                if (entry.variant().isBlank() || entry.amount() <= 0) {
                    slot.variant = FluidVariant.blank();
                    slot.amount = 0;
                } else {
                    slot.variant = entry.variant();
                    slot.amount = Math.min(entry.amount(), capacity);
                }
            }
        });
    }
}
