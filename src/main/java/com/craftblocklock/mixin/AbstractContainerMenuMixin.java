package com.craftblocklock.mixin;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.LockManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractContainerMenu.class)
abstract class AbstractContainerMenuMixin {
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
}
