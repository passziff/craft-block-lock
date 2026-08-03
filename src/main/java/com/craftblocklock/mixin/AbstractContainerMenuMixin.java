package com.craftblocklock.mixin;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.BrewingStandLockAccess;
import com.craftblocklock.lock.LockManager;
import com.craftblocklock.lock.OperationKeys;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(AbstractContainerMenu.class)
abstract class AbstractContainerMenuMixin {
    @Unique
    private static final int craftblocklock$trackedBrewingSlots = 4;

    @Unique
    private BrewingStandBlockEntity craftblocklock$brewingStand;

    @Unique
    private ItemStack[] craftblocklock$brewingBefore;

    @Unique
    private UUID craftblocklock$brewingActor;

    @Inject(method = "clicked", at = @At("HEAD"))
    private void craftblocklock$captureBrewingInteraction(
        int slotIndex,
        int button,
        ContainerInput input,
        Player player,
        CallbackInfo callback
    ) {
        craftblocklock$clearBrewingInteraction();
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (!(menu instanceof BrewingStandMenu)
            || !(player instanceof ServerPlayer serverPlayer)
            || !(menu.getSlot(0).container instanceof BrewingStandBlockEntity stand)) {
            return;
        }

        craftblocklock$brewingStand = stand;
        craftblocklock$brewingActor = serverPlayer.getUUID();
        craftblocklock$brewingBefore = craftblocklock$snapshotBrewingSlots(stand);
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void craftblocklock$recordBrewingInteraction(
        int slotIndex,
        int button,
        ContainerInput input,
        Player player,
        CallbackInfo callback
    ) {
        try {
            if (craftblocklock$brewingStand == null
                || craftblocklock$brewingBefore == null
                || craftblocklock$brewingActor == null
                || !craftblocklock$brewingSlotsChanged(
                    craftblocklock$brewingStand,
                    craftblocklock$brewingBefore
                )) {
                return;
            }

            ((BrewingStandLockAccess) craftblocklock$brewingStand)
                .craftblocklock$setBrewer(craftblocklock$brewingActor);
        } finally {
            craftblocklock$clearBrewingInteraction();
        }
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void craftblocklock$clearResultPreviews(
        int slotIndex,
        int button,
        ContainerInput input,
        Player player,
        CallbackInfo callback
    ) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        OperationKeys.clearPreview(menu.getCarried());
        player.getInventory().getNonEquipmentItems().forEach(OperationKeys::clearPreview);
    }

    @Redirect(
        method = "doClick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private ItemStack craftblocklock$recheckEveryQuickMove(
        AbstractContainerMenu menu,
        Player player,
        int slotIndex
    ) {
        Slot slot = menu.getSlot(slotIndex);
        if (!slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack moved = menu.quickMoveStack(player, slotIndex);
        if (CraftBlockLock.CONFIG.recipeLockEnabled && player instanceof ServerPlayer serverPlayer) {
            LockManager.consumeProvenance(serverPlayer, moved);
        }
        return moved;
    }

    @Unique
    private static ItemStack[] craftblocklock$snapshotBrewingSlots(BrewingStandBlockEntity stand) {
        ItemStack[] snapshot = new ItemStack[craftblocklock$trackedBrewingSlots];
        for (int slot = 0; slot < snapshot.length; slot++) {
            snapshot[slot] = stand.getItem(slot).copy();
        }
        return snapshot;
    }

    @Unique
    private static boolean craftblocklock$brewingSlotsChanged(
        BrewingStandBlockEntity stand,
        ItemStack[] before
    ) {
        for (int slot = 0; slot < before.length; slot++) {
            ItemStack previous = before[slot];
            ItemStack current = stand.getItem(slot);
            if (previous.getCount() != current.getCount()
                || !ItemStack.isSameItemSameComponents(previous, current)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private void craftblocklock$clearBrewingInteraction() {
        craftblocklock$brewingStand = null;
        craftblocklock$brewingBefore = null;
        craftblocklock$brewingActor = null;
    }
}
