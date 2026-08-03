package com.craftblocklock.command;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.data.LockSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CraftBlockLockCommands {
    private CraftBlockLockCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cbl")
            .executes(CraftBlockLockCommands::showStatus)
            .then(Commands.literal("status")
                .executes(CraftBlockLockCommands::showStatus))
            .then(Commands.literal("craft")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("on").executes(context -> setCraftLock(context, true)))
                .then(Commands.literal("off").executes(context -> setCraftLock(context, false))))
            .then(Commands.literal("blocks")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("on").executes(context -> setBlockLock(context, true)))
                .then(Commands.literal("off").executes(context -> setBlockLock(context, false))))
            .then(Commands.literal("feedback")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("messages")
                    .then(Commands.literal("on").executes(context -> setMessages(context, true)))
                    .then(Commands.literal("off").executes(context -> setMessages(context, false))))
                .then(Commands.literal("sounds")
                    .then(Commands.literal("on").executes(context -> setSounds(context, true)))
                    .then(Commands.literal("off").executes(context -> setSounds(context, false)))))
            .then(Commands.literal("reload")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(CraftBlockLockCommands::reload))
            .then(Commands.literal("reset")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.literal("recipes").executes(context -> reset(context, ResetTarget.RECIPES)))
                    .then(Commands.literal("blocks").executes(context -> reset(context, ResetTarget.BLOCKS)))
                    .then(Commands.literal("all").executes(context -> reset(context, ResetTarget.ALL)))))
            .then(Commands.literal("exceptions")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("recipe")
                    .then(Commands.literal("list").executes(context -> listExceptions(context, true)))
                    .then(Commands.literal("add")
                        .then(Commands.argument("recipe", StringArgumentType.greedyString())
                            .suggests(CraftBlockLockCommands::suggestRecipes)
                            .executes(context -> changeRecipeException(context, true))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("recipe", StringArgumentType.greedyString())
                            .suggests(CraftBlockLockCommands::suggestRecipeExceptions)
                            .executes(context -> changeRecipeException(context, false)))))
                .then(Commands.literal("block")
                    .then(Commands.literal("list").executes(context -> listExceptions(context, false)))
                    .then(Commands.literal("add")
                        .then(Commands.argument("block", StringArgumentType.greedyString())
                            .suggests(CraftBlockLockCommands::suggestBlocks)
                            .executes(context -> changeBlockException(context, true))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("block", StringArgumentType.greedyString())
                            .suggests(CraftBlockLockCommands::suggestBlockExceptions)
                            .executes(context -> changeBlockException(context, false)))))));
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
            "Craft lock: " + state(CraftBlockLock.CONFIG.recipeLockEnabled)
                + " | Block lock: " + state(CraftBlockLock.CONFIG.blockLockEnabled)
                + " | Messages: " + state(CraftBlockLock.CONFIG.messagesEnabled)
                + " | Sounds: " + state(CraftBlockLock.CONFIG.denialSoundsEnabled)
        ), false);
        context.getSource().sendSuccess(() -> Component.literal(
            "Exceptions: " + CraftBlockLock.CONFIG.recipeExceptions.size() + " recipes, "
                + CraftBlockLock.CONFIG.blockExceptions.size() + " blocks"
        ), false);
        return 1;
    }

    private static int setCraftLock(CommandContext<CommandSourceStack> context, boolean enabled) {
        CraftBlockLock.CONFIG.recipeLockEnabled = enabled;
        CraftBlockLock.CONFIG.save();
        return confirm(context, "Craft lock is now " + state(enabled) + ".");
    }

    private static int setBlockLock(CommandContext<CommandSourceStack> context, boolean enabled) {
        CraftBlockLock.CONFIG.blockLockEnabled = enabled;
        CraftBlockLock.CONFIG.save();
        return confirm(context, "Block lock is now " + state(enabled) + ".");
    }

    private static int setMessages(CommandContext<CommandSourceStack> context, boolean enabled) {
        CraftBlockLock.CONFIG.messagesEnabled = enabled;
        CraftBlockLock.CONFIG.save();
        return confirm(context, "Denial messages are now " + state(enabled) + ".");
    }

    private static int setSounds(CommandContext<CommandSourceStack> context, boolean enabled) {
        CraftBlockLock.CONFIG.denialSoundsEnabled = enabled;
        CraftBlockLock.CONFIG.save();
        return confirm(context, "Denial sounds are now " + state(enabled) + ".");
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        CraftBlockLock.reloadConfig();
        return confirm(context, "Reloaded craftblocklock.json.");
    }

    private static int reset(CommandContext<CommandSourceStack> context, ResetTarget target) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        LockSavedData data = LockSavedData.get(context.getSource().getServer());
        int cleared = 0;
        if (target != ResetTarget.BLOCKS) {
            cleared += data.clearRecipes(player.getUUID());
        }
        if (target != ResetTarget.RECIPES) {
            cleared += data.clearPlacements(player.getUUID());
        }

        int total = cleared;
        context.getSource().sendSuccess(() -> Component.literal(
            "Reset " + target.label + " for " + player.getScoreboardName() + " (" + total + " locks cleared)."
        ), true);
        return cleared + 1;
    }

    private static int listExceptions(CommandContext<CommandSourceStack> context, boolean recipes) {
        List<String> exceptions = recipes
            ? CraftBlockLock.CONFIG.recipeExceptions
            : CraftBlockLock.CONFIG.blockExceptions;
        String label = recipes ? "Recipe" : "Block";
        String values = exceptions.isEmpty() ? "none" : String.join(", ", exceptions);
        context.getSource().sendSuccess(() -> Component.literal(label + " exceptions: " + values), false);
        return exceptions.size() + 1;
    }

    private static int changeRecipeException(CommandContext<CommandSourceStack> context, boolean add) {
        String key = StringArgumentType.getString(context, "recipe");
        boolean changed = add
            ? CraftBlockLock.CONFIG.addRecipeException(key)
            : CraftBlockLock.CONFIG.removeRecipeException(key);
        return exceptionResult(context, "recipe", key, add, changed);
    }

    private static int changeBlockException(CommandContext<CommandSourceStack> context, boolean add) {
        String key = StringArgumentType.getString(context, "block");
        boolean changed = add
            ? CraftBlockLock.CONFIG.addBlockException(key)
            : CraftBlockLock.CONFIG.removeBlockException(key);
        return exceptionResult(context, "block", key, add, changed);
    }

    private static int exceptionResult(
        CommandContext<CommandSourceStack> context,
        String type,
        String key,
        boolean add,
        boolean changed
    ) {
        if (!changed) {
            context.getSource().sendFailure(Component.literal(
                key + (add ? " is already a " : " is not a ") + type + " exception."
            ));
            return 0;
        }
        return confirm(context, (add ? "Added " : "Removed ") + key + (add ? " to" : " from")
            + " the " + type + " exceptions.");
    }

    private static CompletableFuture<Suggestions> suggestRecipes(
        CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(
            context.getSource().getServer().getRecipeManager().getRecipes().stream()
                .map(recipe -> recipe.id().identifier().toString()),
            builder
        );
    }

    private static CompletableFuture<Suggestions> suggestRecipeExceptions(
        CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(CraftBlockLock.CONFIG.recipeExceptions, builder);
    }

    private static CompletableFuture<Suggestions> suggestBlocks(
        CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(
            BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> BuiltInRegistries.ITEM.getValue(id) instanceof BlockItem)
                .map(Object::toString),
            builder
        );
    }

    private static CompletableFuture<Suggestions> suggestBlockExceptions(
        CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(CraftBlockLock.CONFIG.blockExceptions, builder);
    }

    private static int confirm(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static String state(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private enum ResetTarget {
        RECIPES("recipe locks"),
        BLOCKS("block locks"),
        ALL("all locks");

        private final String label;

        ResetTarget(String label) {
            this.label = label;
        }
    }
}
