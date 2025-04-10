package ti4.processors;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.buttons.UnfiledButtonHandlers;
import ti4.executors.ExecutorManager;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.DisplayType;
import ti4.helpers.SearchGameHelper;
import ti4.listeners.annotations.AnnotationHandler;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.context.ButtonContext;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionService;
import ti4.service.game.GameNameService;

public class ButtonProcessor {

    private static final Map<String, Consumer<ButtonContext>> knownButtons = AnnotationHandler.findKnownHandlers(ButtonContext.class, ButtonHandler.class);
    private static final ButtonRuntimeWarningService runtimeWarningService = new ButtonRuntimeWarningService();

    public static void queue(ButtonInteractionEvent event) {
        BotLogger.logButton(event);

        String gameName = GameNameService.getGameNameFromChannel(event);
        ExecutorManager.runAsync("ButtonProcessor task", gameName, event.getMessageChannel(), () -> process(event));
    }

    private static void process(ButtonInteractionEvent event) {
        long startTime = System.currentTimeMillis();
        long contextRuntime = 0;
        long resolveRuntime = 0;
        long saveRuntime = 0;
        try {
            long beforeTime = System.currentTimeMillis();
            ButtonContext context = new ButtonContext(event);
            contextRuntime = System.currentTimeMillis() - beforeTime;

            if (context.isValid()) {
                beforeTime = System.currentTimeMillis();
                resolveButtonInteractionEvent(context);
                resolveRuntime = System.currentTimeMillis() - beforeTime;

                beforeTime = System.currentTimeMillis();
                context.save();
                saveRuntime = System.currentTimeMillis() - beforeTime;
            }
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(event), "Something went wrong with button interaction", e);
        }

        runtimeWarningService.submitNewRuntime(event, startTime, System.currentTimeMillis(), contextRuntime, resolveRuntime, saveRuntime);
    }

    private static boolean handleKnownButtons(ButtonContext context) {
        String buttonID = context.getButtonID();
        // Check for exact match first
        if (knownButtons.containsKey(buttonID)) {
            knownButtons.get(buttonID).accept(context);
            return true;
        }

        // Then check for prefix match
        String longestPrefixMatch = null;
        for (String key : knownButtons.keySet()) {
            if (buttonID.startsWith(key)) {
                if (longestPrefixMatch == null || key.length() > longestPrefixMatch.length()) {
                    longestPrefixMatch = key;
                }
            }
        }

        if (longestPrefixMatch != null) {
            knownButtons.get(longestPrefixMatch).accept(context);
            return true;
        }
        return false;
    }

    public static void resolveButtonInteractionEvent(ButtonContext context) {
        // pull values from context for easier access
        ButtonInteractionEvent event = context.getEvent();
        Player player = context.getPlayer();
        String buttonID = context.getButtonID();
        Game game = context.getGame();
        MessageChannel privateChannel = context.getPrivateChannel();
        MessageChannel mainGameChannel = context.getMainGameChannel();
        MessageChannel actionsChannel = context.getActionsChannel();

        // Check the list of ButtonHandlers first
        if (handleKnownButtons(context)) return;

        // TODO Convert all else..if..startsWith to use @ButtonHandler
        if (false) {
            // Don't add anymore if/else startWith statements - use @ButtonHandler
        } else if (buttonID.startsWith("ac_discard_from_hand_")) {
            UnfiledButtonHandlers.acDiscardFromHand(event, buttonID, game, player, mainGameChannel);
        } else if (buttonID.startsWith(Constants.SO_SCORE_FROM_HAND)) {
            UnfiledButtonHandlers.soScoreFromHand(event, buttonID, game, player, privateChannel, mainGameChannel, mainGameChannel);
        } else if (buttonID.startsWith(Constants.PO_SCORING)) {
            UnfiledButtonHandlers.poScoring(event, player, buttonID, game, privateChannel);
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            ReactionService.addReaction(event, game, player);
        } else if (buttonID.startsWith("movedNExplored_")) {
            UnfiledButtonHandlers.movedNExplored(event, player, buttonID, game, mainGameChannel);
        } else if (buttonID.startsWith("autoAssignGroundHits_")) {
            ButtonHelperModifyUnits.autoAssignGroundCombatHits(player, game, buttonID.split("_")[1], Integer.parseInt(buttonID.split("_")[2]), event);
        } else if (buttonID.startsWith("strategicAction_")) {
            UnfiledButtonHandlers.strategicAction(event, player, buttonID, game, mainGameChannel);
        } else if (buttonID.startsWith("getSwapButtons_")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Swap", ButtonHelper.getButtonsToSwitchWithAllianceMembers(player, game, true));
            // Don't add anymore if/else startWith statements - use @ButtonHandler
        } else {
            switch (buttonID) { // TODO Convert all switch case to use @ButtonHandler
                // Don't add anymore cases - use @ButtonHandler
                case "refreshInfoButtons" -> MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), null, Buttons.REFRESH_INFO_BUTTONS);
                case "factionEmbedRefresh" -> MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCardsInfoThread(), null, List.of(player.getRepresentationEmbed()), List.of(Buttons.FACTION_EMBED));
                case "gain_1_comms" -> ButtonHelperStats.gainComms(event, game, player, 1, true);
                case "gain_2_comms" -> ButtonHelperStats.gainComms(event, game, player, 2, true);
                case "gain_3_comms" -> ButtonHelperStats.gainComms(event, game, player, 3, true);
                case "gain_4_comms" -> ButtonHelperStats.gainComms(event, game, player, 4, true);
                case "gain_1_comms_stay" -> ButtonHelperStats.gainComms(event, game, player, 1, false);
                case "gain_2_comms_stay" -> ButtonHelperStats.gainComms(event, game, player, 2, false);
                case "gain_3_comms_stay" -> ButtonHelperStats.gainComms(event, game, player, 3, false);
                case "gain_4_comms_stay" -> ButtonHelperStats.gainComms(event, game, player, 4, false);
                case "convert_1_comms" -> ButtonHelperStats.convertComms(event, game, player, 1);
                case "convert_2_comms" -> ButtonHelperStats.convertComms(event, game, player, 2, true);
                case "convert_3_comms" -> ButtonHelperStats.convertComms(event, game, player, 3);
                case "convert_4_comms" -> ButtonHelperStats.convertComms(event, game, player, 4);
                case "convert_2_comms_stay" -> ButtonHelperStats.convertComms(event, game, player, 2, false);
                // Don't add anymore cases - use @ButtonHandler
                case "play_when" -> AgendaHelper.playWhen(event, game, player, mainGameChannel);
                case "gain_1_tg" -> UnfiledButtonHandlers.gain1TG(event, player, game, mainGameChannel);
                case "gain1tgFromLetnevCommander" -> UnfiledButtonHandlers.gain1tgFromLetnevCommander(event, player, game, mainGameChannel);
                case "gain1tgFromMuaatCommander" -> UnfiledButtonHandlers.gain1tgFromMuaatCommander(event, player, game, mainGameChannel);
                case "gain1tgFromCommander" -> UnfiledButtonHandlers.gain1tgFromCommander(event, player, game, mainGameChannel); // should be deprecated
                case "decline_explore" -> UnfiledButtonHandlers.declineExplore(event, player, game, mainGameChannel);
                case "resolveHarness" -> ButtonHelperStats.replenishComms(event, game, player, false);
                case "pass_on_abilities" -> ReactionService.addReaction(event, game, player, " is " + event.getButton().getLabel().toLowerCase() + ".");
                case "lastMinuteDeliberation" -> UnfiledButtonHandlers.lastMinuteDeliberation(event, player, game, actionsChannel);
                case "searchMyGames" -> SearchGameHelper.searchGames(event.getUser(), event, false, false, false, true, false, true, false, false);
                case "checkWHView" -> ButtonHelper.showFeatureType(event, game, DisplayType.wormholes);
                case "checkAnomView" -> ButtonHelper.showFeatureType(event, game, DisplayType.anomalies);
                case "checkLegendView" -> ButtonHelper.showFeatureType(event, game, DisplayType.legendaries);
                case "checkEmptyView" -> ButtonHelper.showFeatureType(event, game, DisplayType.empties);
                case "checkAetherView" -> ButtonHelper.showFeatureType(event, game, DisplayType.aetherstream);
                case "checkCannonView" -> ButtonHelper.showFeatureType(event, game, DisplayType.spacecannon);
                case "checkTraitView" -> ButtonHelper.showFeatureType(event, game, DisplayType.traits);
                case "checkTechSkipView" -> ButtonHelper.showFeatureType(event, game, DisplayType.techskips);
                case "checkAttachmView" -> ButtonHelper.showFeatureType(event, game, DisplayType.attachments);
                case "checkShiplessView" -> ButtonHelper.showFeatureType(event, game, DisplayType.shipless);
                // Don't add anymore cases - use @ButtonHandler
                default -> MessageHelper.sendMessageToEventChannel(event, "Button " + ButtonHelper.getButtonRepresentation(event.getButton()) + " pressed. This button does not do anything.");
            }
        }
    }

    public static String getButtonProcessingStatistics() {
        var decimalFormatter = new DecimalFormat("#.##");
        return "Button Processor Statistics: " + DateTimeHelper.getCurrentTimestamp() + "\n" +
            "> Total button presses: " + runtimeWarningService.getTotalRuntimeSubmissionCount() + ".\n" +
            "> Total threshold misses: " + runtimeWarningService.getTotalRuntimeThresholdMissCount() + ".\n" +
            "> Average preprocessing time: " + decimalFormatter.format(runtimeWarningService.getAveragePreprocessingTime()) + "ms.\n" +
            "> Average processing time: " + decimalFormatter.format(runtimeWarningService.getAverageProcessingTime()) + "ms.";
    }
}
