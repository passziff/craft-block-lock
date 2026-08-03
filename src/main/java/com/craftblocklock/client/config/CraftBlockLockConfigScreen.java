package com.craftblocklock.client.config;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.config.ModConfig;
import com.craftblocklock.lock.LockManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

public final class CraftBlockLockConfigScreen extends Screen {
    private static final int BUTTON_WIDTH = 240;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private boolean recipeLockEnabled;
    private boolean blockLockEnabled;
    private boolean creativeModeBypass;
    private boolean messagesEnabled;
    private boolean denialSoundsEnabled;
    private boolean lockedRecipeVisualsEnabled;
    private final List<String> recipeExceptions;
    private final List<String> blockExceptions;

    public CraftBlockLockConfigScreen(Screen parent) {
        super(Component.literal("Craft & Block Lock Settings"));
        this.parent = parent;

        ModConfig config = CraftBlockLock.CONFIG;
        this.recipeLockEnabled = config.recipeLockEnabled;
        this.blockLockEnabled = config.blockLockEnabled;
        this.creativeModeBypass = config.creativeModeBypass;
        this.messagesEnabled = config.messagesEnabled;
        this.denialSoundsEnabled = config.denialSoundsEnabled;
        this.lockedRecipeVisualsEnabled = config.lockedRecipeVisualsEnabled;
        this.recipeExceptions = new ArrayList<>(config.recipeExceptions);
        this.blockExceptions = new ArrayList<>(config.blockExceptions);
    }

    @Override
    protected void init() {
        int x = (this.width - BUTTON_WIDTH) / 2;
        int y = 48;

        this.addRenderableWidget(Button.builder(toggleText("Recipe locks", this.recipeLockEnabled), button -> {
            this.recipeLockEnabled = !this.recipeLockEnabled;
            button.setMessage(toggleText("Recipe locks", this.recipeLockEnabled));
        }).pos(x, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        y += 24;
        this.addRenderableWidget(Button.builder(toggleText("Block locks", this.blockLockEnabled), button -> {
            this.blockLockEnabled = !this.blockLockEnabled;
            button.setMessage(toggleText("Block locks", this.blockLockEnabled));
        }).pos(x, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        y += 24;
        this.addRenderableWidget(Button.builder(toggleText("Creative mode bypass", this.creativeModeBypass), button -> {
            this.creativeModeBypass = !this.creativeModeBypass;
            button.setMessage(toggleText("Creative mode bypass", this.creativeModeBypass));
        }).pos(x, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        y += 24;
        this.addRenderableWidget(Button.builder(toggleText("Denial messages", this.messagesEnabled), button -> {
            this.messagesEnabled = !this.messagesEnabled;
            button.setMessage(toggleText("Denial messages", this.messagesEnabled));
        }).pos(x, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        y += 24;
        this.addRenderableWidget(Button.builder(toggleText("Denial sounds", this.denialSoundsEnabled), button -> {
            this.denialSoundsEnabled = !this.denialSoundsEnabled;
            button.setMessage(toggleText("Denial sounds", this.denialSoundsEnabled));
        }).pos(x, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        y += 24;
        this.addRenderableWidget(Button.builder(toggleText("Locked recipe visuals", this.lockedRecipeVisualsEnabled), button -> {
            this.lockedRecipeVisualsEnabled = !this.lockedRecipeVisualsEnabled;
            button.setMessage(toggleText("Locked recipe visuals", this.lockedRecipeVisualsEnabled));
        }).pos(x, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        y += 24;
        this.addRenderableWidget(Button.builder(exceptionText("Recipe exceptions", this.recipeExceptions), button ->
            this.minecraft.gui.setScreen(new CraftBlockLockExceptionsScreen(this, true, this.recipeExceptions))
        ).pos(x, y).size((BUTTON_WIDTH - 4) / 2, BUTTON_HEIGHT).build());
        this.addRenderableWidget(Button.builder(exceptionText("Block exceptions", this.blockExceptions), button ->
            this.minecraft.gui.setScreen(new CraftBlockLockExceptionsScreen(this, false, this.blockExceptions))
        ).pos(x + (BUTTON_WIDTH + 4) / 2, y).size((BUTTON_WIDTH - 4) / 2, BUTTON_HEIGHT).build());

        int bottomY = this.height - 28;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
            .pos(this.width / 2 - 154, bottomY).size(150, BUTTON_HEIGHT).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.saveAndClose())
            .pos(this.width / 2 + 4, bottomY).size(150, BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
        graphics.centeredText(
            this.font,
            Component.literal("Multiplayer servers use their own operator-controlled settings."),
            this.width / 2,
            30,
            0xFFAAAAAA
        );
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    private void saveAndClose() {
        ModConfig config = CraftBlockLock.CONFIG;
        config.recipeLockEnabled = this.recipeLockEnabled;
        config.blockLockEnabled = this.blockLockEnabled;
        config.creativeModeBypass = this.creativeModeBypass;
        config.messagesEnabled = this.messagesEnabled;
        config.denialSoundsEnabled = this.denialSoundsEnabled;
        config.lockedRecipeVisualsEnabled = this.lockedRecipeVisualsEnabled;
        config.recipeExceptions = new ArrayList<>(this.recipeExceptions);
        config.blockExceptions = new ArrayList<>(this.blockExceptions);
        config.save();

        MinecraftServer server = this.minecraft.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> LockManager.syncAllPlayers(server));
        }
        this.minecraft.gui.setScreen(this.parent);
    }

    private static Component toggleText(String label, boolean enabled) {
        return Component.literal(label + ": " + (enabled ? "ON" : "OFF"));
    }

    private static Component exceptionText(String label, List<String> exceptions) {
        return Component.literal(label + " (" + exceptions.size() + ")");
    }
}
