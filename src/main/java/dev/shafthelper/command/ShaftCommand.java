package dev.shafthelper.command;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import dev.shafthelper.client.ConfigScreen;
import dev.shafthelper.client.NetworkTracker;
import dev.shafthelper.client.ShaftTracker;
import dev.shafthelper.client.WaypointsScreen;
import dev.shafthelper.config.ModConfig;
import dev.shafthelper.core.HttpFetcher;
import dev.shafthelper.core.Prices;
import dev.shafthelper.core.ShaftOptions;
import dev.shafthelper.core.ShaftSuggestions;
import dev.shafthelper.ui.GuideText;
import dev.shafthelper.ui.ShaftText;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The /shaft client command. Options are space-separated key:value pairs, the same syntax the
 * Discord bot's slash command used: /shaft mining_speed:1500 type:Amber lapis:3.
 * Run /shaft with no options for the guide.
 */
public final class ShaftCommand {

    private static final HttpFetcher FETCHER = HttpFetcher.real();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {  
        dispatcher.register(  
            ClientCommands.literal("shaft")  
                .executes(context -> openMenu(context.getSource()))   // was run(source, "")  
                .then(ClientCommands.literal("config")  
                    .executes(context -> openConfig(context.getSource())))  
                .then(ClientCommands.literal("waypoints")  
                    .executes(context -> openWaypoints(context.getSource())))  
                .then(ClientCommands.literal("ping")  
                    .then(ClientCommands.argument("ms", IntegerArgumentType.integer(0))  
                        .executes(context -> setPing(context.getSource(), IntegerArgumentType.getInteger(context, "ms")))))  
                .then(ClientCommands.literal("miningspeed")  
                    .then(ClientCommands.argument("speed", IntegerArgumentType.integer(1))  
                        .executes(context -> setMiningSpeed(context.getSource(), IntegerArgumentType.getInteger(context, "speed")))))  
                .then(ClientCommands.argument("options", StringArgumentType.greedyString())  
                    .suggests(ShaftCommand::suggestOptions)  
                    .executes(context -> run(context.getSource(),  
                        StringArgumentType.getString(context, "options"))))  
        );  
    }  
    
    private static int openMenu(FabricClientCommandSource source) {  
        // Queued so it opens after the chat screen closes itself at the end of the command.  
        source.getClient().schedule(() -> source.getClient().setScreen(new dev.shafthelper.client.ShaftMenuScreen()));  
        return 1;  
    }

    private static CompletableFuture<Suggestions> suggestOptions(
            CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        ShaftSuggestions.Result result = ShaftSuggestions.suggest(builder.getRemaining());
        SuggestionsBuilder offset = builder.createOffset(builder.getStart() + result.offset());
        for (String suggestion : result.suggestions()) {
            offset.suggest(suggestion);
        }
        return offset.buildFuture();
    }

    private static int openGuide(FabricClientCommandSource source, int page) {  
        // Queued so it opens after the chat screen closes itself at the end of the command.  
        final int p = page;  
        source.getClient().schedule(() ->  
            source.getClient().setScreen(new dev.shafthelper.client.ShaftGuideScreen(p)));  
        return 1;  
    }

    private static int openConfig(FabricClientCommandSource source) {
        // Queued so it opens after the chat screen closes itself at the end of the command.
        source.getClient().schedule(() -> source.getClient().setScreen(new ConfigScreen()));
        return 1;
    }

    private static int openWaypoints(FabricClientCommandSource source) {
        // Queued so it opens after the chat screen closes itself at the end of the command.
        source.getClient().schedule(() -> source.getClient().setScreen(new WaypointsScreen()));
        return 1;
    }

    private static int setPing(FabricClientCommandSource source, int ping) {
        NetworkTracker.setPing(ping);
        source.sendFeedback(Component.literal("Ping set to " + ping + "ms")
            .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setMiningSpeed(FabricClientCommandSource source, int speed) {
        ModConfig config = ShaftTracker.config();
        if (config.miningSpeed > 0)
            config.miningSpeed = speed;
        else
            speed = -1;  // Indicate that the config is not being used. Throw error if the user tries to use it in a calculation.
        source.sendFeedback(Component.literal("Mining speed set to " + speed)
            .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int run(FabricClientCommandSource source, String input) {
        ShaftOptions options;
        try {
            options = ShaftOptions.parse(input);
        } catch (IllegalArgumentException error) {
            source.sendError(Component.literal(error.getMessage()));
            return 0;
        }
        applyConfigDefaults(options);

        if (options.help != null || options.miningSpeed == null) {
            openGuide(source, options.help == null ? 1 : options.help);  
            return 1;
        }

        source.sendFeedback(Component.literal("Fetching Bazaar prices...")
            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        Prices.load(options.priceMode, options.priceData, options.priceBasis, FETCHER)
            .thenAccept(result -> source.getClient().execute(() -> {
                if (options.type != null && result.prices() != null) {
                    send(source, ShaftText.shaftRun(options, result));
                } else if (result.prices() != null) {
                    send(source, ShaftText.overview(options, result));
                } else {
                    send(source, ShaftText.noPrices(options));
                }
            }));
        return 1;
    }

    /** Saved / auto-read stats fill in anything the player did not type explicitly. */
    private static void applyConfigDefaults(ShaftOptions options) {
        ModConfig config = ShaftTracker.config();
        if (config == null) return;
        if (!options.given.contains("mining_speed") && config.miningSpeed > 0) {
            options.miningSpeed = config.miningSpeed;
        }
        if (!options.given.contains("mining_fortune")) options.fortune = config.miningFortune;
        if (!options.given.contains("pristine")) options.pristine = config.pristine;
        if (!options.given.contains("efficiency")) options.efficiency = config.efficiency;
        if (!options.given.contains("cold_res")) options.coldRes = config.coldRes;
        if (!options.given.contains("benchmark")) options.benchmark = config.benchmark;
        if (!options.given.contains("prices")) options.priceMode = config.priceMode();
        if (!options.given.contains("price_data")) options.priceData = config.priceDataMode();
        if (!options.given.contains("price_basis")) options.priceBasis = config.priceBasisMode();
    }

    private static void send(FabricClientCommandSource source, List<Component> lines) {
        for (Component line : lines) {
            source.sendFeedback(line);
        }
    }

    private ShaftCommand() {}
}
