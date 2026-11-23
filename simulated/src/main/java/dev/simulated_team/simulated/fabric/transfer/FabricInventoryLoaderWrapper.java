package dev.simulated_team.simulated.fabric.transfer;

import dev.simulated_team.simulated.multiloader.inventory.InventoryLoaderWrapper;
import dev.simulated_team.simulated.multiloader.inventory.ItemInfoWrapper;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class FabricInventoryLoaderWrapper extends InventoryLoaderWrapper {
    private final Storage<ItemVariant> storage;
    private final SlottedStorage<ItemVariant> slots;
    private Storage<ItemVariant> exposed;

    public FabricInventoryLoaderWrapper(final Storage<ItemVariant> storage) {
        this.storage = storage;
        this.slots = storage instanceof final SlottedStorage<ItemVariant> slotted ? slotted : null;
    }

    @Override
    public ItemStack extractAny(final int maxAmount, final boolean simulate, final boolean exact) {
        ItemVariant selected = ItemVariant.blank();
        long available = 0;
        for (final StorageView<ItemVariant> view : this.storage.nonEmptyViews()) {
            if (selected.isBlank()) {
                selected = view.getResource();
            }
            if (selected.equals(view.getResource())) {
                available += view.getAmount();
            }
        }
        if (selected.isBlank() || exact && available < maxAmount) {
            return ItemStack.EMPTY;
        }

        final long extracted = this.extract(selected, maxAmount, simulate);
        if (extracted <= 0) {
            return ItemStack.EMPTY;
        }
        this.modified(true, simulate);
        return selected.toStack((int) extracted);
    }

    @Override
    public int insertGeneral(final ItemInfoWrapper info, final int amountToInsert, final boolean simulate) {
        final long inserted = this.insert(ItemVariant.of(ItemInfoWrapper.generateFromInfo(info)), amountToInsert, simulate);
        this.modified(false, simulate || inserted == 0);
        return (int) inserted;
    }

    @Override
    public ItemStack insertSlot(final ItemStack stack, final int slot, final boolean simulate) {
        if (this.slots == null || slot < 0 || slot >= this.slots.getSlotCount()) {
            return stack;
        }
        final SingleSlotStorage<ItemVariant> target = this.slots.getSlot(slot);
        final long inserted;
        try (Transaction transaction = Transaction.openOuter()) {
            inserted = target.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            if (!simulate) {
                transaction.commit();
            }
        }
        this.modified(false, simulate || inserted == 0);
        return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - (int) inserted);
    }

    @Override
    public int extractGeneral(final ItemInfoWrapper info, final int amountToExtract, final boolean simulate) {
        final long extracted = this.extract(ItemVariant.of(ItemInfoWrapper.generateFromInfo(info)), amountToExtract, simulate);
        this.modified(true, simulate || extracted == 0);
        return (int) extracted;
    }

    @Override
    public ItemStack extractSlot(final int slot, final int amountToExtract, final boolean simulate) {
        if (this.slots == null || slot < 0 || slot >= this.slots.getSlotCount()) {
            return ItemStack.EMPTY;
        }
        final SingleSlotStorage<ItemVariant> target = this.slots.getSlot(slot);
        final ItemVariant variant = target.getResource();
        if (variant.isBlank()) {
            return ItemStack.EMPTY;
        }
        final long extracted;
        try (Transaction transaction = Transaction.openOuter()) {
            extracted = target.extract(variant, amountToExtract, transaction);
            if (!simulate) {
                transaction.commit();
            }
        }
        this.modified(true, simulate || extracted == 0);
        return variant.toStack((int) extracted);
    }

    @Override
    public int getContainerSize() {
        return this.slots == null ? 0 : this.slots.getSlotCount();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public @NotNull ItemStack getItem(final int slot) {
        if (this.slots == null || slot < 0 || slot >= this.slots.getSlotCount()) {
            return ItemStack.EMPTY;
        }
        final SingleSlotStorage<ItemVariant> target = this.slots.getSlot(slot);
        return target.isResourceBlank() ? ItemStack.EMPTY : target.getResource().toStack((int) target.getAmount());
    }

    private long insert(final ItemVariant variant, final long amount, final boolean simulate) {
        try (Transaction transaction = Transaction.openOuter()) {
            final long inserted = this.storage.insert(variant, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return inserted;
        }
    }

    private long extract(final ItemVariant variant, final long amount, final boolean simulate) {
        try (Transaction transaction = Transaction.openOuter()) {
            final long extracted = this.storage.extract(variant, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return extracted;
        }
    }

    /**
     * PORT-26.3: the storage to give Fabric's transfer API (funnels, hoppers, pipes) for this inventory: the real
     * underlying storage, whose committed extractions also reach this wrapper's modification callback (the way other
     * mods' extractions reach the NeoForge item handler). Exposing the wrapper itself as a container did not work: its
     * {@code getItem} hands out copies and {@code setItem} does nothing, so nothing could be taken out through it.
     */
    public Storage<ItemVariant> exposed() {
        if (this.exposed == null) {
            this.exposed = new NotifyingStorage();
        }
        return this.exposed;
    }

    private void notifyExtraction(final long amount, final net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
        if (amount > 0) {
            transaction.addOuterCloseCallback(result -> {
                if (result.wasCommitted()) {
                    this.modified(true, false);
                }
            });
        }
    }

    private final class NotifyingStorage implements Storage<ItemVariant> {
        @Override
        public boolean supportsInsertion() {
            return FabricInventoryLoaderWrapper.this.storage.supportsInsertion();
        }

        @Override
        public long insert(final ItemVariant resource, final long maxAmount, final net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
            return FabricInventoryLoaderWrapper.this.storage.insert(resource, maxAmount, transaction);
        }

        @Override
        public boolean supportsExtraction() {
            return FabricInventoryLoaderWrapper.this.storage.supportsExtraction();
        }

        @Override
        public long extract(final ItemVariant resource, final long maxAmount, final net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
            final long extracted = FabricInventoryLoaderWrapper.this.storage.extract(resource, maxAmount, transaction);
            FabricInventoryLoaderWrapper.this.notifyExtraction(extracted, transaction);
            return extracted;
        }

        @Override
        public java.util.Iterator<StorageView<ItemVariant>> iterator() {
            final java.util.Iterator<StorageView<ItemVariant>> views = FabricInventoryLoaderWrapper.this.storage.iterator();
            return new java.util.Iterator<>() {
                @Override
                public boolean hasNext() {
                    return views.hasNext();
                }

                @Override
                public StorageView<ItemVariant> next() {
                    return new NotifyingView(views.next());
                }
            };
        }
    }

    private final class NotifyingView implements StorageView<ItemVariant> {
        private final StorageView<ItemVariant> view;

        private NotifyingView(final StorageView<ItemVariant> view) {
            this.view = view;
        }

        @Override
        public long extract(final ItemVariant resource, final long maxAmount, final net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
            final long extracted = this.view.extract(resource, maxAmount, transaction);
            FabricInventoryLoaderWrapper.this.notifyExtraction(extracted, transaction);
            return extracted;
        }

        @Override
        public boolean isResourceBlank() {
            return this.view.isResourceBlank();
        }

        @Override
        public ItemVariant getResource() {
            return this.view.getResource();
        }

        @Override
        public long getAmount() {
            return this.view.getAmount();
        }

        @Override
        public long getCapacity() {
            return this.view.getCapacity();
        }

        @Override
        public StorageView<ItemVariant> getUnderlyingView() {
            return this.view.getUnderlyingView();
        }
    }

    private void modified(final boolean extraction, final boolean skip) {
        if (!skip && this.callback != null) {
            this.callback.accept(extraction);
        }
    }
}
