package com.craftblocklock.lock;

import java.util.UUID;

public interface BrewingStandLockAccess {
    void craftblocklock$setBrewer(UUID playerId);

    UUID craftblocklock$getBrewer();

    boolean craftblocklock$shouldNotifyDenied(String operation, long gameTime);

    void craftblocklock$clearDeniedOperation();
}
