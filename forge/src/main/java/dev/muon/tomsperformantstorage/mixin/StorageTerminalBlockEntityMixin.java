package dev.muon.tomsperformantstorage.mixin;

import com.tom.storagemod.tile.StorageTerminalBlockEntity;
import com.tom.storagemod.util.StoredItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = StorageTerminalBlockEntity.class, remap = false)
public abstract class StorageTerminalBlockEntityMixin {

    @Shadow(remap = false) private Map<StoredItemStack, Long> items;
    @Shadow(remap = false) private boolean updateItems;
    @Shadow(remap = false) private net.minecraftforge.items.IItemHandler itemHandler;

    @Unique
    private long toms_storage_perf$lastRebuildTick = -100;

    @Unique
    private long toms_storage_perf$lastFingerprint = 0;

    @Unique
    private net.minecraftforge.items.IItemHandler toms_storage_perf$lastItemHandler;

    @Unique
    private java.util.Map<net.minecraft.world.item.Item, it.unimi.dsi.fastutil.ints.IntList> toms_storage_perf$slotIndex = new java.util.HashMap<>();

    @Unique
    private it.unimi.dsi.fastutil.ints.IntList toms_storage_perf$emptySlots = new it.unimi.dsi.fastutil.ints.IntArrayList();

    @Inject(method = "updateServer", at = @At("HEAD"), remap = false)
    private void throttleUpdateServer(CallbackInfo ci) {
        if (this.updateItems) {
            BlockEntity be = (BlockEntity) (Object) this;
            if (be.getLevel() == null) return;
            long currentTick = be.getLevel().getGameTime();

            // Always update the item handler so it isn't stale while we throttle
            net.minecraft.world.level.block.state.BlockState state = be.getLevel().getBlockState(be.getBlockPos());
            net.minecraft.core.Direction facing = state.getValue(com.tom.storagemod.block.AbstractStorageTerminalBlock.FACING);
            com.tom.storagemod.block.AbstractStorageTerminalBlock.TerminalPos pos = state.getValue(com.tom.storagemod.block.AbstractStorageTerminalBlock.TERMINAL_POS);
            if (pos == com.tom.storagemod.block.AbstractStorageTerminalBlock.TerminalPos.UP) facing = net.minecraft.core.Direction.UP;
            if (pos == com.tom.storagemod.block.AbstractStorageTerminalBlock.TerminalPos.DOWN) facing = net.minecraft.core.Direction.DOWN;

            BlockEntity adj = be.getLevel().getBlockEntity(be.getBlockPos().relative(facing));
            if (adj != null) {
                this.itemHandler = adj.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, facing.getOpposite()).orElse(null);
            } else {
                this.itemHandler = null;
            }

            if (this.itemHandler != toms_storage_perf$lastItemHandler) {
                toms_storage_perf$lastRebuildTick = -100;
                toms_storage_perf$lastItemHandler = this.itemHandler;
            }

            if (currentTick - toms_storage_perf$lastRebuildTick < 20) {
                this.updateItems = false;
            } else {
                toms_storage_perf$lastRebuildTick = currentTick;
                if (this.itemHandler != null) {
                    toms_storage_perf$slotIndex.clear();
                    toms_storage_perf$emptySlots.clear();
                    long fingerprint = 0;
                    int slots = this.itemHandler.getSlots();
                    for (int i = 0; i < slots; i++) {
                        net.minecraft.world.item.ItemStack stack = this.itemHandler.getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            fingerprint = fingerprint * 31 + net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(stack.getItem());
                            fingerprint = fingerprint * 31 + stack.getCount();
                            fingerprint = fingerprint * 31 + (stack.getTag() != null ? stack.getTag().hashCode() : 0);

                            it.unimi.dsi.fastutil.ints.IntList list = toms_storage_perf$slotIndex.get(stack.getItem());
                            if (list == null) {
                                list = new it.unimi.dsi.fastutil.ints.IntArrayList();
                                toms_storage_perf$slotIndex.put(stack.getItem(), list);
                            }
                            list.add(i);
                        } else {
                            fingerprint = fingerprint * 31;
                            toms_storage_perf$emptySlots.add(i);
                        }
                    }
                    if (fingerprint == toms_storage_perf$lastFingerprint) {
                        this.updateItems = false;
                    } else {
                        toms_storage_perf$lastFingerprint = fingerprint;
                    }
                }
            }
        }
    }

    @Inject(method = "pullStack", at = @At("HEAD"), cancellable = true, remap = false)
    private void fastPullStack(StoredItemStack stack, long max, CallbackInfoReturnable<StoredItemStack> cir) {
        if (stack == null || this.itemHandler == null || max <= 0) {
            cir.setReturnValue(null);
            return;
        }

        net.minecraft.world.item.ItemStack st = stack.getStack();
        StoredItemStack ret = null;

        it.unimi.dsi.fastutil.ints.IntList slots = toms_storage_perf$slotIndex.get(st.getItem());
        if (slots != null) {
            for (int i = slots.size() - 1; i >= 0; i--) {
                int slot = slots.getInt(i);
                net.minecraft.world.item.ItemStack s = this.itemHandler.getStackInSlot(slot);
                if (net.minecraft.world.item.ItemStack.isSameItemSameTags(s, st)) {
                    net.minecraft.world.item.ItemStack pulled = this.itemHandler.extractItem(slot, (int) max, false);
                    if (!pulled.isEmpty()) {
                        if (this.itemHandler.getStackInSlot(slot).isEmpty()) {
                            toms_storage_perf$emptySlots.add(slot);
                        }
                        if (ret == null) {
                            ret = new StoredItemStack(pulled);
                        } else {
                            ret.grow(pulled.getCount());
                        }
                        max -= pulled.getCount();
                        if (max <= 0) {
                            break;
                        }
                    }
                }
            }
        }

        if (ret != null) {
            toms_storage_perf$updateMap(ret, -ret.getQuantity());
        }
        cir.setReturnValue(ret);
    }

    @Inject(method = "pullStackFuzzy", at = @At("HEAD"), cancellable = true, remap = false)
    private void fastPullStackFuzzy(StoredItemStack stack, long max, CallbackInfoReturnable<StoredItemStack> cir) {
        if (stack == null || this.itemHandler == null || max <= 0) {
            cir.setReturnValue(null);
            return;
        }

        net.minecraft.world.item.ItemStack st = stack.getStack();
        StoredItemStack ret = null;

        it.unimi.dsi.fastutil.ints.IntList slots = toms_storage_perf$slotIndex.get(st.getItem());
        if (slots != null) {
            for (int i = slots.size() - 1; i >= 0; i--) {
                int slot = slots.getInt(i);
                net.minecraft.world.item.ItemStack s = this.itemHandler.getStackInSlot(slot);
                if (net.minecraft.world.item.ItemStack.isSameItem(s, st) && (net.minecraft.world.item.ItemStack.isSameItemSameTags(s, st) || !s.isEnchanted())) {
                    net.minecraft.world.item.ItemStack pulled = this.itemHandler.extractItem(slot, (int) max, false);
                    if (!pulled.isEmpty()) {
                        if (this.itemHandler.getStackInSlot(slot).isEmpty()) {
                            toms_storage_perf$emptySlots.add(slot);
                        }
                        if (ret == null) {
                            ret = new StoredItemStack(pulled);
                        } else {
                            ret.grow(pulled.getCount());
                        }
                        max -= pulled.getCount();
                        if (max <= 0) {
                            break;
                        }
                    }
                }
            }
        }

        // Deliberately skipping delta updates for fuzzy pulls to avoid key mismatches
        cir.setReturnValue(ret);
    }

    @Inject(method = "pushStack(Lcom/tom/storagemod/util/StoredItemStack;)Lcom/tom/storagemod/util/StoredItemStack;", at = @At("HEAD"), cancellable = true, remap = false)
    private void fastPushStack(StoredItemStack stack, CallbackInfoReturnable<StoredItemStack> cir) {
        if (stack == null || stack.getQuantity() == 0 || this.itemHandler == null) {
            cir.setReturnValue(stack);
            return;
        }

        net.minecraft.world.item.ItemStack remainder = stack.getActualStack();
        if (remainder.isEmpty()) {
            cir.setReturnValue(null);
            return;
        }

        boolean isStackable = remainder.isStackable();

        // 1. Merge into existing slots (only if stackable)
        if (isStackable) {
            it.unimi.dsi.fastutil.ints.IntList slots = toms_storage_perf$slotIndex.get(remainder.getItem());
            if (slots != null) {
                for (int i = 0; i < slots.size(); i++) {
                    int slot = slots.getInt(i);
                    net.minecraft.world.item.ItemStack existing = this.itemHandler.getStackInSlot(slot);
                    if (!existing.isEmpty() && net.minecraftforge.items.ItemHandlerHelper.canItemStacksStackRelaxed(existing, remainder)) {
                        remainder = this.itemHandler.insertItem(slot, remainder, false);
                        if (remainder.isEmpty()) {
                            break;
                        }
                    }
                }
            }
        }

        // 2. Insert into empty slots (or any slot if unstackable, but realistically empties are where they go)
        if (!remainder.isEmpty()) {
            for (int i = 0; i < toms_storage_perf$emptySlots.size(); i++) {
                int slot = toms_storage_perf$emptySlots.getInt(i);
                net.minecraft.world.item.ItemStack existing = this.itemHandler.getStackInSlot(slot);
                if (existing.isEmpty()) {
                    remainder = this.itemHandler.insertItem(slot, remainder, false);
                    net.minecraft.world.item.ItemStack afterInsert = this.itemHandler.getStackInSlot(slot);
                    if (!afterInsert.isEmpty()) {
                        // Slot became occupied, track it
                        net.minecraft.world.item.Item item = afterInsert.getItem();
                        it.unimi.dsi.fastutil.ints.IntList itemSlots = toms_storage_perf$slotIndex.get(item);
                        if (itemSlots == null) {
                            itemSlots = new it.unimi.dsi.fastutil.ints.IntArrayList();
                            toms_storage_perf$slotIndex.put(item, itemSlots);
                        }
                        itemSlots.add(slot);
                        toms_storage_perf$emptySlots.removeInt(i);
                        i--; // Adjust index since we removed from the list
                    }
                    if (remainder.isEmpty()) {
                        break;
                    }
                }
            }
        }

        StoredItemStack ret = remainder.isEmpty() ? null : new StoredItemStack(remainder);

        long remainderCount = ret != null ? ret.getQuantity() : 0;
        long inserted = stack.getQuantity() - remainderCount;

        if (inserted > 0) {
            toms_storage_perf$updateMap(stack, inserted);
        }

        cir.setReturnValue(ret);
    }

    @Unique
    private void toms_storage_perf$updateMap(StoredItemStack stackTemplate, long delta) {
        if (this.items == null) return;

        StoredItemStack key = new StoredItemStack(stackTemplate.getStack());
        Long current = this.items.get(key);
        long newCount = (current != null ? current : 0) + delta;

        if (newCount <= 0) {
            this.items.remove(key);
        } else {
            this.items.put(key, newCount);
        }
    }
}