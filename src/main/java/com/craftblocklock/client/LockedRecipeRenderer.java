package com.craftblocklock.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class LockedRecipeRenderer {
    private static final ItemStack BARRIER_ICON = new ItemStack(Items.BARRIER);
    private static final int SHADE = 0x88000000;

    private LockedRecipeRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, SHADE);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + width - 8, y + height - 8);
        graphics.pose().scale(0.5F, 0.5F);
        graphics.fakeItem(BARRIER_ICON, 0, 0);
        graphics.pose().popMatrix();
    }
}
