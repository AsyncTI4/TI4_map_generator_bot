package ti4.processors;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.buttons.UnfiledButtonHandlers;
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

public class ButtonProcessor {

    private static final ButtonProcessor instance = new ButtonProcessor();
    private static final Map<String, Consumer<ButtonContext>> knownButtons = AnnotationHandler.findKnownHandlers(ButtonContext.class, ButtonHandler.class);

    private final BlockingQueue<ButtonInteractionEvent> buttonInteractionQueue = new LinkedBlockingQueue<>();
    private final Set<String> userButtonPressSet = ConcurrentHashMap.newKeySet();
    private final Thread worker;
    private boolean running = true;

    private ButtonProcessor() {
        worker = new Thread(() -> {
            while (running || !buttonInteractionQueue.isEmpty()) {
                try {
                    ButtonInteractionEvent buttonInteractionEvent = buttonInteractionQueue.poll(1, TimeUnit.SECONDS);
                    if (buttonInteractionEvent != null) {
                        process(buttonInteractionEvent);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    BotLogger.log("ButtonProcessor worker threw an exception.", e);
                }
            }
        });
    }

    public static void start() {
        instance.worker.start();
    }

    public static boolean shutdown() {
        instance.running = false;
        try {
            instance.worker.join(20000);
            return !instance.worker.isAlive();
        } catch (InterruptedException e) {
            BotLogger.log("ButtonProcessor shutdown interrupted.");
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static void queue(ButtonInteractionEvent event) {
        String key = event.getUser().getId() + event.getButton().getId();
        if (instance.userButtonPressSet.contains(key)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "The bot hasn't processed this button press since you last pressed it. Please wait.");
            return;
        }
        instance.userButtonPressSet.add(key);
        instance.buttonInteractionQueue.add(event);
    }

    private void process(ButtonInteractionEvent event) {
        BotLogger.logButton(event);
        long eventTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long startTime = System.currentTimeMillis();
        try {
            ButtonContext context = new ButtonContext(event);
            if (context.isValid()) {
                resolveButtonInteractionEvent(context);
            }
            context.save(event);
        } catch (Exception e) {
            BotLogger.log(event, "Something went wrong with button interaction", e);
        }

        long endTime = System.currentTimeMillis();
        final int milliThreshold = 2000;
        if (startTime - eventTime > milliThreshold || endTime - startTime > milliThreshold) {
            String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(startTime - eventTime);
            String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(endTime - startTime);
            String message = "[" + event.getChannel().getName() + "](" + event.getMessage().getJumpUrl() + ") " + event.getUser().getEffectiveName() + " pressed button: " + ButtonHelper.getButtonRepresentation(event.getButton()) +
                "\n> Warning: This button took over " + milliThreshold + "ms to respond or execute\n> " +
                DateTimeHelper.getTimestampFromMillesecondsEpoch(eventTime) + " button was pressed by user\n> " +
                DateTimeHelper.getTimestampFromMillesecondsEpoch(startTime) + " `" + responseTime + "` to respond\n> " +
                DateTimeHelper.getTimestampFromMillesecondsEpoch(endTime) + " `" + executionTime + "` to execute" + (endTime - startTime > startTime - eventTime ? "😲" : "");
            BotLogger.log(message);
        }
        instance.userButtonPressSet.remove(event.getUser().getId() + event.getButton().getId());
    }

    private boolean handleKnownButtons(ButtonContext context) {
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

    public void resolveButtonInteractionEvent(ButtonContext context) {
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
            ButtonHelper.addReaction(event, false, false, null, "");
        } else if (buttonID.startsWith("movedNExplored_")) {
            UnfiledButtonHandlers.movedNExplored(event, player, buttonID, game, mainGameChannel);
        } else if (buttonID.startsWith("autoAssignGroundHits_")) {
            ButtonHelperModifyUnits.autoAssignGroundCombatHits(player, game, buttonID.split("_")[1], Integer.parseInt(buttonID.split("_")[2]), event);
        } else if (buttonID.startsWith("strategicAction_")) {
            UnfiledButtonHandlers.strategicAction(event, player, buttonID, game, mainGameChannel);
        } else if (buttonID.startsWith("getSwapButtons_")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Swap", ButtonHelper.getButtonsToSwitchWithAllianceMembers(player, game, true));
        } else if (buttonID.startsWith("milty_")) {
            game.getMiltyDraftManager().doMiltyPick(event, game, buttonID, player);
        } else if (buttonID.startsWith("showMiltyDraft")) {
            game.getMiltyDraftManager().repostDraftInformation(game);
        } else if (player != null && buttonID.startsWith("miltyFactionInfo_")) {
            UnfiledButtonHandlers.miltyFactionInfo(player, buttonID, game);
        } else if (buttonID.startsWith("jmfA_") || buttonID.startsWith("jmfN_")) {
            game.initializeMiltySettings().parseButtonInput(event);
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
                case "convert_2_comms" -> ButtonHelperStats.convertComms(event, game, player, 2);
                case "convert_3_comms" -> ButtonHelperStats.convertComms(event, game, player, 3);
                case "convert_4_comms" -> ButtonHelperStats.convertComms(event, game, player, 4);
                case "convert_2_comms_stay" -> ButtonHelperStats.convertComms(event, game, player, 2, false);
                // Don't add anymore cases - use @ButtonHandler
                case "play_when" -> AgendaHelper.playWhen(event, game, mainGameChannel);
                case "gain_1_tg" -> UnfiledButtonHandlers.gain1TG(event, player, game, mainGameChannel);
                case "gain1tgFromLetnevCommander" -> UnfiledButtonHandlers.gain1tgFromLetnevCommander(event, player, game, mainGameChannel);
                case "gain1tgFromMuaatCommander" -> UnfiledButtonHandlers.gain1tgFromMuaatCommander(event, player, game, mainGameChannel);
                case "gain1tgFromCommander" -> UnfiledButtonHandlers.gain1tgFromCommander(event, player, game, mainGameChannel); // should be deprecated
                case "decline_explore" -> UnfiledButtonHandlers.declineExplore(event, player, game, mainGameChannel);
                case "resolveHarness" -> ButtonHelperStats.replenishComms(event, game, player, false);
                case "pass_on_abilities" -> ButtonHelper.addReaction(event, false, false, " Is " + event.getButton().getLabel(), "");
                case "lastMinuteDeliberation" -> UnfiledButtonHandlers.lastMinuteDeliberation(event, player, game, actionsChannel);
                case "declinePDS" -> MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmojiOrColor() + " officially declines to fire PDS");
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
}
