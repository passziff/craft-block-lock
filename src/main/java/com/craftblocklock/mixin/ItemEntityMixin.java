package com.craftblocklock.mixin;
import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.LockManager;
import com.craftblocklock.lock.OperationKeys;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @Shadow public abstract ItemStack getItem();

    @Unique
    private int craftblocklock$pickupCount = -1;
    @Unique
    private ItemStack craftblocklock$pickupStack = ItemStack.EMPTY;
    @Unique
    private UUID craftblocklock$lastDeniedPlayer;
    @Unique
    private long craftblocklock$lastDeniedTick = Long.MIN_VALUE;

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void craftblocklock$beforeTaggedWorldPickup(Player player, CallbackInfo callback) {
        craftblocklock$clearPendingPickup();
        if (!CraftBlockLock.CONFIG.recipeLockEnabled || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack stack = getItem();
        if (!LockManager.mayAcquireProvenance(serverPlayer, stack)) {
            craftblocklock$showDeniedFeedback(serverPlayer);
            callback.cancel();
            return;
        }

        if (OperationKeys.readProvenance(stack).isPresent()) {
            craftblocklock$pickupCount = stack.getCount();
            craftblocklock$pickupStack = stack.copy();
        }
    }

    @Inject(method = "playerTouch", at = @At("RETURN"))
    private void craftblocklock$afterTaggedWorldPickup(Player player, CallbackInfo callback) {
        try {
            if (craftblocklock$pickupCount < 0 || !(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            ItemEntity self = (ItemEntity) (Object) this;
            ItemStack remaining = getItem();
            if (self.isRemoved() || remaining.getCount() < craftblocklock$pickupCount) {
                ItemStack provenanceSource = OperationKeys.readProvenance(remaining).isPresent()
                    ? remaining
                    : craftblocklock$pickupStack;
                LockManager.consumeProvenance(serverPlayer, provenanceSource);
            }
        } finally {
            craftblocklock$clearPendingPickup();
        }
    }

    @Unique
    private void craftblocklock$showDeniedFeedback(ServerPlayer player) {
        ItemEntity self = (ItemEntity) (Object) this;
        long gameTime = self.level().getGameTime();
        UUID playerId = player.getUUID();
        if (!playerId.equals(craftblocklock$lastDeniedPlayer)
            || craftblocklock$lastDeniedTick == Long.MIN_VALUE
            || gameTime - craftblocklock$lastDeniedTick >= 20L) {
            LockManager.showRecipeLocked(player);
            craftblocklock$lastDeniedPlayer = playerId;
            craftblocklock$lastDeniedTick = gameTime;
        }
    }

    @Unique
    private void craftblocklock$clearPendingPickup() {
        craftblocklock$pickupCount = -1;
        craftblocklock$pickupStack = ItemStack.EMPTY;
    }
}
