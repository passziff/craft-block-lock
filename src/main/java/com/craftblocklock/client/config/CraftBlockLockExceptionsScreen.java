package com.craftblocklock.client.config;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;

import java.util.List;

public final class CraftBlockLockExceptionsScreen extends Screen {
    private static final int CONTENT_WIDTH = 320;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private final boolean recipes;
    private final List<String> exceptions;
    private EditBox idInput;
    private String inputValue = "";
    private Component status = Component.empty();
    private int page;
    private int rowsPerPage;

    public CraftBlockLockExceptionsScreen(Screen parent, boolean recipes, List<String> exceptions) {
        super(Component.literal((recipes ? "Recipe" : "Block") + " Exceptions"));
        this.parent = parent;
        this.recipes = recipes;
        this.exceptions = exceptions;
    }

    @Override
    protected void init() {
        int x = (this.width - CONTENT_WIDTH) / 2;
        int inputY = 48;
        int addWidth = 56;

        this.idInput = this.addRenderableWidget(new EditBox(
            this.font,
            x,
            inputY,
            CONTENT_WIDTH - addWidth - 4,
            BUTTON_HEIGHT,
            Component.literal((this.recipes ? "Recipe" : "Block") + " ID")
        ));
        this.idInput.setMaxLength(256);
        this.idInput.setHint(Component.literal("minecraft:" + (this.recipes ? "recipe_id" : "block_id")));
        this.idInput.setValue(this.inputValue);
        this.idInput.setResponder(value -> this.inputValue = value);

        this.addRenderableWidget(Button.builder(Component.literal("Add"), button -> this.addException())
            .pos(x + CONTENT_WIDTH - addWidth, inputY).size(addWidth, BUTTON_HEIGHT).build());

        this.rowsPerPage = Math.max(1, (this.height - 150) / ROW_HEIGHT);
        int totalPages = this.totalPages();
        this.page = Math.min(this.page, totalPages - 1);
        int start = this.page * this.rowsPerPage;
        int end = Math.min(start + this.rowsPerPage, this.exceptions.size());
        int rowY = 80;

        for (int index = start; index < end; index++) {
            String exception = this.exceptions.get(index);
            this.addRenderableWidget(Button.builder(Component.literal("Remove"), button -> this.removeException(exception))
                .pos(x + CONTENT_WIDTH - 62, rowY).size(62, BUTTON_HEIGHT).build());
            rowY += ROW_HEIGHT;
        }

        int footerY = this.height - 28;
        Button previous = this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            this.page--;
            this.rebuildWidgets();
        }).pos(x, footerY).size(34, BUTTON_HEIGHT).build());
        previous.active = this.page > 0;

        Button next = this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            this.page++;
            this.rebuildWidgets();
        }).pos(x + 38, footerY).size(34, BUTTON_HEIGHT).build());
        next.active = this.page + 1 < totalPages;

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
            .pos(x + CONTENT_WIDTH - 100, footerY).size(100, BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int x = (this.width - CONTENT_WIDTH) / 2;
        graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
        graphics.centeredText(
            this.font,
            Component.literal("Exceptions stay unlimited and do not create locks."),
            this.width / 2,
            30,
            0xFFAAAAAA
        );

        int start = this.page * this.rowsPerPage;
        int end = Math.min(start + this.rowsPerPage, this.exceptions.size());
        int rowY = 86;
        for (String exception : this.exceptions.subList(start, end)) {
            graphics.text(this.font, this.font.plainSubstrByWidth(exception, CONTENT_WIDTH - 70), x + 4, rowY, 0xFFFFFFFF);
            rowY += ROW_HEIGHT;
        }

        if (this.exceptions.isEmpty()) {
            graphics.centeredText(this.font, Component.literal("No exceptions"), this.width / 2, 94, 0xFFAAAAAA);
        }

        if (!this.status.getString().isEmpty()) {
            graphics.centeredText(this.font, this.status, this.width / 2, this.height - 48, 0xFFFFFFFF);
        }

        graphics.centeredText(
            this.font,
            Component.literal("Page " + (this.page + 1) + "/" + this.totalPages()),
            this.width / 2,
            this.height - 22,
            0xFFAAAAAA
        );
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    private void addException() {
        String value = this.inputValue.trim();
        Identifier id = Identifier.tryParse(value);
        if (id == null) {
            this.status = Component.literal("Enter a valid namespaced ID.").withStyle(ChatFormatting.RED);
            return;
        }

        String normalized = id.toString();
        if (!this.recipes && (!BuiltInRegistries.ITEM.containsKey(id)
            || !(BuiltInRegistries.ITEM.getValue(id) instanceof BlockItem))) {
            this.status = Component.literal("That ID is not a registered block item.").withStyle(ChatFormatting.RED);
            return;
        }
        if (this.exceptions.contains(normalized)) {
            this.status = Component.literal("That exception is already listed.").withStyle(ChatFormatting.YELLOW);
            return;
        }

        this.exceptions.add(normalized);
        this.exceptions.sort(String::compareTo);
        this.page = Math.max(0, (this.exceptions.indexOf(normalized)) / Math.max(1, this.rowsPerPage));
        this.inputValue = "";
        this.status = Component.literal("Added " + normalized).withStyle(ChatFormatting.GREEN);
        this.rebuildWidgets();
    }

    private void removeException(String exception) {
        this.exceptions.remove(exception);
        this.status = Component.literal("Removed " + exception).withStyle(ChatFormatting.GREEN);
        this.rebuildWidgets();
    }

    private int totalPages() {
        return Math.max(1, (this.exceptions.size() + Math.max(1, this.rowsPerPage) - 1) / Math.max(1, this.rowsPerPage));
    }
}
