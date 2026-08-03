package com.craftblocklock.command;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.data.LockSavedData;
import com.craftblocklock.lock.LockManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class CraftBlockLockCommands {
    private static final long RESET_CONFIRMATION_WINDOW_MS = 15_000L;
    private static final int BLOCKS_PER_PAGE = 8;
    private static final int RECIPES_PER_PAGE = 8;
    private static final Map<PendingReset, Long> PENDING_RESETS = new ConcurrentHashMap<>();

    private CraftBlockLockCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cbl")
            .executes(CraftBlockLockCommands::showStatus)
            .then(Commands.literal("help")
                .executes(CraftBlockLockCommands::showHelp))
            .then(Commands.literal("status")
                .executes(CraftBlockLockCommands::showStatus))
            .then(Commands.literal("craft")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("on").executes(context -> setCraftLock(context, true)))
                .then(Commands.literal("off").executes(context -> setCraftLock(context, false))))
            .then(Commands.literal("recipes")
                .then(Commands.literal("list")
                    .executes(context -> listLockedRecipes(context, 1))
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> listLockedRecipes(context, IntegerArgumentType.getInteger(context, "page"))))))
            .then(Commands.literal("blocks")
                .then(Commands.literal("list")
                    .executes(context -> listLockedBlocks(context, 1))
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> listLockedBlocks(context, IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("on")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(context -> setBlockLock(context, true)))
                .then(Commands.literal("off")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(context -> setBlockLock(context, false))))
            .then(Commands.literal("creative-bypass")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("on").executes(context -> setCreativeBypass(context, true)))
                .then(Commands.literal("off").executes(context -> setCreativeBypass(context, false))))
            .then(Commands.literal("feedback")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("messages")
                    .then(Commands.literal("on").executes(context -> setMessages(context, true)))
                    .then(Commands.literal("off").executes(context -> setMessages(context, false))))
                .then(Commands.literal("sounds")
                    .then(Commands.literal("on").executes(context -> setSounds(context, true)))
                    .then(Commands.literal("off").executes(context -> setSounds(context, false))))
                .then(Commands.literal("visuals")
                    .then(Commands.literal("on").executes(context -> setVisuals(context, true)))
                    .then(Commands.literal("off").executes(context -> setVisuals(context, false)))))
            .then(Commands.literal("reload")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(CraftBlockLockCommands::reload))
            .then(Commands.literal("reset")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.literal("recipes")
                        .executes(context -> requestReset(context, ResetTarget.RECIPES))
                        .then(Commands.literal("confirm").executes(context -> confirmReset(context, ResetTarget.RECIPES))))
                    .then(Commands.literal("blocks")
                        .executes(context -> requestReset(context, ResetTarget.BLOCKS))
                        .then(Commands.literal("confirm").executes(context -> confirmReset(context, ResetTarget.BLOCKS))))
                    .then(Commands.literal("all")
                        .executes(context -> requestReset(context, ResetTarget.ALL))
                        .then(Commands.literal("confirm").executes(context -> confirmReset(context, ResetTarget.ALL))))))
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
                + " | Creative bypass: " + state(CraftBlockLock.CONFIG.creativeModeBypass)
                + " | Messages: " + state(CraftBlockLock.CONFIG.messagesEnabled)
                + " | Sounds: " + state(CraftBlockLock.CONFIG.denialSoundsEnabled)
                + " | Locked recipe visuals: " + state(CraftBlockLock.CONFIG.lockedRecipeVisualsEnabled)
        ), false);
        context.getSource().sendSuccess(() -> Component.literal(
            "Exceptions: " + CraftBlockLock.CONFIG.recipeExceptions.size() + " recipes, "
                + CraftBlockLock.CONFIG.blockExceptions.size() + " blocks"
        ), false);
        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        sendHelpLine(context, "Craft & Block Lock commands:");
        sendHelpLine(context, "/cbl status: show the current settings");
        sendHelpLine(context, "/cbl help: show this command list");
        sendHelpLine(context, "/cbl recipes list [page]: show your locked recipes");
        sendHelpLine(context, "/cbl blocks list [page]: show your active block locks");

        if (!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(context.getSource())) {
            sendHelpLine(context, "An operator can change settings and reset progress.");
            return 1;
        }

        sendHelpLine(context, "/cbl craft on|off: toggle recipe locks");
        sendHelpLine(context, "/cbl blocks on|off: toggle block locks");
        sendHelpLine(context, "/cbl creative-bypass on|off: toggle Creative mode bypass");
        sendHelpLine(context, "/cbl feedback <messages|sounds|visuals> on|off");
        sendHelpLine(context, "/cbl reset <player> <recipes|blocks|all>");
        sendHelpLine(context, "/cbl exceptions <recipe|block> <list|add|remove>");
        sendHelpLine(context, "/cbl reload: reload craftblocklock.json");
        return 1;
    }

    private static int listLockedRecipes(CommandContext<CommandSourceStack> context, int requestedPage) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> lockedRecipes = LockSavedData.get(context.getSource().getServer())
            .getCraftedRecipes(player.getUUID())
            .stream()
            .filter(key -> !CraftBlockLock.CONFIG.isRecipeException(key))
            .sorted()
            .toList();

        if (lockedRecipes.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("You have no locked recipes."), false);
            return 1;
        }

        int totalPages = (lockedRecipes.size() + RECIPES_PER_PAGE - 1) / RECIPES_PER_PAGE;
        int page = Math.min(requestedPage, totalPages);
        int start = (page - 1) * RECIPES_PER_PAGE;
        int end = Math.min(start + RECIPES_PER_PAGE, lockedRecipes.size());

        context.getSource().sendSuccess(() -> Component.literal(
            "Locked recipes (" + lockedRecipes.size() + ") - page " + page + "/" + totalPages
        ).withStyle(ChatFormatting.GOLD), false);

        for (String recipeId : lockedRecipes.subList(start, end)) {
            Component line = Component.literal("- ")
                .append(Component.literal(recipeId).withStyle(ChatFormatting.WHITE));
            context.getSource().sendSuccess(() -> line, false);
        }

        sendPageLinks(context, "/cbl recipes list ", page, totalPages);
        return lockedRecipes.size() + 1;
    }

    private static int listLockedBlocks(CommandContext<CommandSourceStack> context, int requestedPage) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> lockedBlocks = LockSavedData.get(context.getSource().getServer())
            .getPlacedTypes(player.getUUID())
            .stream()
            .filter(key -> !CraftBlockLock.CONFIG.isBlockException(key))
            .sorted(Comparator.comparing(CraftBlockLockCommands::blockName).thenComparing(String::compareTo))
            .toList();

        if (lockedBlocks.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("You have no active block locks."), false);
            return 1;
        }

        int totalPages = (lockedBlocks.size() + BLOCKS_PER_PAGE - 1) / BLOCKS_PER_PAGE;
        int page = Math.min(requestedPage, totalPages);
        int start = (page - 1) * BLOCKS_PER_PAGE;
        int end = Math.min(start + BLOCKS_PER_PAGE, lockedBlocks.size());

        context.getSource().sendSuccess(() -> Component.literal(
            "Active block locks (" + lockedBlocks.size() + ") - page " + page + "/" + totalPages
        ).withStyle(ChatFormatting.GOLD), false);

        for (String blockId : lockedBlocks.subList(start, end)) {
            Component line = Component.literal("- ")
                .append(Component.literal(blockName(blockId)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (" + blockId + ")").withStyle(ChatFormatting.GRAY));
            context.getSource().sendSuccess(() -> line, false);
        }

        sendPageLinks(context, "/cbl blocks list ", page, totalPages);
        return lockedBlocks.size() + 1;
    }

    private static void sendPageLinks(
        CommandContext<CommandSourceStack> context,
        String command,
        int page,
        int totalPages
    ) {
        Component links = Component.empty();
        if (page > 1) {
            int previousPage = page - 1;
            links = links.copy().append(Component.literal("[Previous page]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand(command + previousPage))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Show page " + previousPage)))));
        }
        if (page < totalPages) {
            int nextPage = page + 1;
            if (page > 1) {
                links = links.copy().append(Component.literal(" "));
            }
            links = links.copy().append(Component.literal("[Next page]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand(command + nextPage))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Show page " + nextPage)))));
        }
        if (!links.getString().isEmpty()) {
            Component pageLinks = links;
            context.getSource().sendSuccess(() -> pageLinks, false);
        }
    }

    private static String blockName(String blockId) {
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(blockId);
        if (id == null) {
            return blockId;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        ItemStack stack = new ItemStack(item);
        return stack.isEmpty() ? blockId : stack.getHoverName().getString();
    }

    private static int setCraftLock(CommandContext<CommandSourceStack> context, boolean enabled) {
        CraftBlockLock.CONFIG.recipeLockEnabled = enabled;
        CraftBlockLock.CONFIG.save();
        LockManager.syncAllPlayers(context.getSource().getServer());
        return confirm(context, "Craft lock is now " + state(enabled) + ".");
    }

    private static int setBlockLock(CommandContext<CommandSourceStack> context, boolean enabled) {
        CraftBlockLock.CONFIG.blockLockEnabled = enabled;
        CraftBlockLock.CONFIG.save();
        LockManager.syncAllPlayers(context.getSource().getServer());
        return confirm(context, "Block lock is now " + state(enabled) + ".");
    }

    private static int setCreativeBypass(CommandContext<CommandSourceStack> context, boolean enabled) {
        CraftBlockLock.CONFIG.creativeModeBypass = enabled;
        CraftBlockLock.CONFIG.save();
        LockManager.syncAllPlayers(context.getSource().getServer());
        return confirm(context, "Creative mode bypass is now " + state(enabled) + ".");
    }

    private static int setMessages(CommandContext<CommandSourceStack> context, boolean enabled) {
        CraftBlockLock.CONFIG.messagesEnabled = enabled;
        CraftBlockLock.CONFIG.save();
        LockManager.syncAllPlayers(context.getSource().getServer());
        return confirm(context, "Denial messages are now " + state(enabled) + ".");
    }

    private static int setSounds(CommandContext<CommandSourceStack> context, boolean enabled) {
        CraftBlockLock.CONFIG.denialSoundsEnabled = enabled;
        CraftBlockLock.CONFIG.save();
        LockManager.syncAllPlayers(context.getSource().getServer());
        return confirm(context, "Denial sounds are now " + state(enabled) + ".");
    }

    private static int setVisuals(CommandContext<CommandSourceStack> context, boolean enabled) {
        CraftBlockLock.CONFIG.lockedRecipeVisualsEnabled = enabled;
        CraftBlockLock.CONFIG.save();
        LockManager.syncAllPlayers(context.getSource().getServer());
        return confirm(context, "Locked recipe visuals are now " + state(enabled) + ".");
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        CraftBlockLock.reloadConfig();
        LockManager.syncAllPlayers(context.getSource().getServer());
        return confirm(context, "Reloaded craftblocklock.json.");
    }

    private static int requestReset(CommandContext<CommandSourceStack> context, ResetTarget target) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        PendingReset pending = pendingReset(context, player, target);
        PENDING_RESETS.put(pending, System.currentTimeMillis() + RESET_CONFIRMATION_WINDOW_MS);

        String command = "/cbl reset " + player.getScoreboardName() + " " + target.commandName + " confirm";
        Component confirmButton = Component.literal("[Confirm]").withStyle(style -> style
            .withColor(ChatFormatting.GREEN)
            .withBold(true)
            .withClickEvent(new ClickEvent.RunCommand(command))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Confirm this reset"))));
        context.getSource().sendSuccess(() -> Component.literal(
            "Reset " + target.label + " for " + player.getScoreboardName() + "? "
        ).append(confirmButton).append(Component.literal(" Expires in 15 seconds.")), false);
        return 1;
    }

    private static int confirmReset(CommandContext<CommandSourceStack> context, ResetTarget target) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        PendingReset pending = pendingReset(context, player, target);
        Long expiresAt = PENDING_RESETS.remove(pending);
        if (expiresAt == null || System.currentTimeMillis() > expiresAt) {
            context.getSource().sendFailure(Component.literal(
                "No active reset confirmation. Run the reset command again."
            ));
            return 0;
        }

        return performReset(context, player, target);
    }

    private static int performReset(
        CommandContext<CommandSourceStack> context,
        ServerPlayer player,
        ResetTarget target
    ) {
        LockSavedData data = LockSavedData.get(context.getSource().getServer());
        int cleared = 0;
        if (target != ResetTarget.BLOCKS) {
            cleared += data.clearRecipes(player.getUUID());
        }
        if (target != ResetTarget.RECIPES) {
            cleared += data.clearPlacements(player.getUUID());
        }
        LockManager.syncLockState(player);

        int total = cleared;
        context.getSource().sendSuccess(() -> Component.literal(
            "Reset " + target.label + " for " + player.getScoreboardName() + " (" + total + " locks cleared)."
        ), true);
        return cleared + 1;
    }

    private static PendingReset pendingReset(
        CommandContext<CommandSourceStack> context,
        ServerPlayer player,
        ResetTarget target
    ) {
        return new PendingReset(context.getSource().getTextName(), player.getUUID(), target);
    }

    private static void sendHelpLine(CommandContext<CommandSourceStack> context, String line) {
        context.getSource().sendSuccess(() -> Component.literal(line), false);
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
        if (changed) {
            LockManager.syncAllPlayers(context.getSource().getServer());
        }
        return exceptionResult(context, "recipe", key, add, changed);
    }

    private static int changeBlockException(CommandContext<CommandSourceStack> context, boolean add) {
        String key = StringArgumentType.getString(context, "block");
        boolean changed = add
            ? CraftBlockLock.CONFIG.addBlockException(key)
            : CraftBlockLock.CONFIG.removeBlockException(key);
        if (changed) {
            LockManager.syncAllPlayers(context.getSource().getServer());
        }
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
        RECIPES("recipes", "recipe locks"),
        BLOCKS("blocks", "block locks"),
        ALL("all", "all locks");

        private final String commandName;
        private final String label;

        ResetTarget(String commandName, String label) {
            this.commandName = commandName;
            this.label = label;
        }
    }

    private record PendingReset(String requester, UUID player, ResetTarget target) {
    }
}
