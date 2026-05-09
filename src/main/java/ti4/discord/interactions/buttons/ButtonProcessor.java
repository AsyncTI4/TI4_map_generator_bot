package ti4.discord.interactions.buttons;

import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.contest.replay.buttons.CombatSideBetButtonIds;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.interactions.listeners.context.ButtonContext;
import ti4.discord.interactions.routing.AnnotationHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.HandlerRegistry;
import ti4.executors.ExecutionLockType;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.DisplayType;
import ti4.helpers.SearchGameHelper;
import ti4.helpers.StatusHelper;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.logging.RollbarManager;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionService;
import ti4.service.game.GameNameService;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.context.SpringContext;

@UtilityClass
public class ButtonProcessor {

    private static final HandlerRegistry<ButtonContext> registry =
            AnnotationHandler.buildHandlerRegistry(ButtonContext.class, ButtonHandler.class);
    private static final ButtonRuntimeWarningService runtimeWarningService = new ButtonRuntimeWarningService();

    public static void checkButtonHandlersSetup() {
        if (registry.getSize() == 0) {
            throw new IllegalStateException("No button handlers were registered");
        }
    }

    public static void queue(ButtonInteractionEvent event) {
        String gameName = GameNameService.getGameNameFromChannel(event);
        String rawComponentID = event.getButton().getCustomId();
        ExecutionLockType lockType = registry.isSave(rawComponentID) ? ExecutionLockType.WRITE : ExecutionLockType.READ;
        ExecutorServiceManager.runAsyncWithLock(
                eventToString(event, gameName), gameName, event.getMessageChannel(), () -> process(event), lockType);
    }

    private static String eventToString(ButtonInteractionEvent event, String gameName) {
        return "ButtonProcessor task for `" + event.getUser().getEffectiveName() + "`"
                + (gameName == null ? "" : " in `" + gameName + "`")
                + ": "
                + ButtonHelper.getButtonRepresentation(event.getButton());
    }

    private static void process(ButtonInteractionEvent event) {
        long processStartTime = System.currentTimeMillis();

        ButtonContext context = new ButtonContext(event);
        if (!context.isValid()) return;

        long beforeTime = System.currentTimeMillis();
        log(event);
        long logRuntime = System.currentTimeMillis() - beforeTime;

        long resolveRuntime = 0;
        long saveRuntime = 0;
        try {
            CombatReplayService combatReplayService =
                    CombatContestSettings.isEnabledStatic() ? SpringContext.getBean(CombatReplayService.class) : null;
            if (combatReplayService != null) {
                CombatReplayService.PreInteractionSnapshot preInteractionSnapshot =
                        combatReplayService.capturePreInteractionSnapshot(context.getGame());
                combatReplayService.setPreInteractionSnapshot(preInteractionSnapshot);
            }
            try {
                beforeTime = System.currentTimeMillis();
                resolveButtonInteractionEvent(context);
                resolveRuntime = System.currentTimeMillis() - beforeTime;

                beforeTime = System.currentTimeMillis();
                context.save();
                saveRuntime = System.currentTimeMillis() - beforeTime;

                if (combatReplayService != null && context.getGame() != null) {
                    combatReplayService.onButtonInteractionSettled(context.getGame(), context.getPlayer(), event);
                }
            } finally {
                if (combatReplayService != null) {
                    combatReplayService.clearPreInteractionSnapshot();
                }
            }
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(event, context), "Something went wrong with button interaction", e);
        } finally {
            RollbarManager.clear();
        }

        long contextCreationRuntime = context.getCreationEndTime() - context.getCreationStartTime();
        runtimeWarningService.submitNewRuntime(
                event,
                processStartTime,
                System.currentTimeMillis(),
                contextCreationRuntime,
                logRuntime,
                resolveRuntime,
                saveRuntime);
    }

    private static void log(ButtonInteractionEvent event) {
        BotLogger.logButton(event);

        RollbarManager.putInteractionMetadata("button", event);
        RollbarManager.put("button_id", event.getButton().getCustomId());
        RollbarManager.put("game_name", GameNameService.getGameNameFromChannel(event));

        User user = event.getUser();
        UserSettings userSettings = UserSettingsManager.get(user.getId());
        int currentHourUTC = ZonedDateTime.now(ZoneId.of("UTC")).getHour();
        userSettings.addActiveHour(currentHourUTC);
        UserSettingsManager.save(userSettings);
    }

    private static boolean isCombatReplayButton(String buttonID) {
        return buttonID != null
                && (buttonID.startsWith(CombatSideBetButtonIds.PREFIX) || buttonID.startsWith("combatReplayDebug_"));
    }

    private static void resolveButtonInteractionEvent(ButtonContext context) {
        // pull values from context for easier access
        ButtonInteractionEvent event = context.getEvent();
        Player player = context.getPlayer();
        String buttonID = context.getButtonID();
        Game game = context.getGame();
        MessageChannel privateChannel = context.getPrivateChannel();
        MessageChannel mainGameChannel = context.getMainGameChannel();

        // Skip combat replay buttons when the feature is disabled
        if (!CombatContestSettings.isEnabledStatic() && isCombatReplayButton(buttonID)) return;

        // Check the list of ButtonHandlers first
        if (registry.handle(buttonID, context)) return;

        // TODO Convert all else..if..startsWith to use @ButtonHandler
        if (false) {
            // Don't add anymore if/else startWith statements - use @ButtonHandler
        } else if (buttonID.startsWith(Constants.SO_SCORE_FROM_HAND)) {
            trackButtonHandler(Constants.SO_SCORE_FROM_HAND);
            StatusHelper.soScoreFromHand(
                    event, buttonID, game, player, privateChannel, mainGameChannel, mainGameChannel);
        } else if (buttonID.startsWith(Constants.PO_SCORING)) {
            trackButtonHandler(Constants.PO_SCORING);
            StatusHelper.poScoring(event, player, buttonID, game, privateChannel);
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            trackButtonHandler(Constants.GENERIC_BUTTON_ID_PREFIX);
            ReactionService.addReaction(event, game, player);
        } else if (buttonID.startsWith("autoAssignGroundHits_")) {
            trackButtonHandler("autoAssignGroundHits_");
            ButtonHelperModifyUnits.autoAssignGroundCombatHits(
                    player, game, buttonID.split("_")[1], Integer.parseInt(buttonID.split("_")[2]), event);
        } else if (buttonID.startsWith("strategicAction_")) {
            trackButtonHandler("strategicAction_");
            strategicAction(event, player, buttonID, game, mainGameChannel);
        } else if (buttonID.startsWith("getSwapButtons_")) {
            trackButtonHandler("getSwapButtons_");
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    "Swap",
                    ButtonHelper.getButtonsToSwitchWithAllianceMembers(player, game, true));
            // Don't add anymore if/else startWith statements - use @ButtonHandler
        } else {
            switch (buttonID) { // TODO Convert all switch case to use @ButtonHandler
                // Don't add anymore cases - use @ButtonHandler
                case "refreshInfoButtons" -> {
                    trackButtonHandler("refreshInfoButtons");
                    MessageHelper.sendMessageToChannelWithButtons(
                            event.getChannel(), null, getRefreshInfoButtons(game));
                }
                case "gain_1_comms" -> {
                    trackButtonHandler("gain_1_comms");
                    ButtonHelperStats.gainComms(event, game, player, 1, true);
                }
                case "gain_2_comms" -> {
                    trackButtonHandler("gain_2_comms");
                    ButtonHelperStats.gainComms(event, game, player, 2, true);
                }
                case "gain_3_comms" -> {
                    trackButtonHandler("gain_3_comms");
                    ButtonHelperStats.gainComms(event, game, player, 3, true);
                }
                case "gain_4_comms" -> {
                    trackButtonHandler("gain_4_comms");
                    ButtonHelperStats.gainComms(event, game, player, 4, true);
                }
                case "gain_1_comms_stay" -> {
                    trackButtonHandler("gain_1_comms_stay");
                    ButtonHelperStats.gainComms(event, game, player, 1, false);
                }
                case "gain_2_comms_stay" -> {
                    trackButtonHandler("gain_2_comms_stay");
                    ButtonHelperStats.gainComms(event, game, player, 2, false);
                }
                case "gain_3_comms_stay" -> {
                    trackButtonHandler("gain_3_comms_stay");
                    ButtonHelperStats.gainComms(event, game, player, 3, false);
                }
                case "gain_4_comms_stay" -> {
                    trackButtonHandler("gain_4_comms_stay");
                    ButtonHelperStats.gainComms(event, game, player, 4, false);
                }
                case "convert_1_comms" -> {
                    trackButtonHandler("convert_1_comms");
                    ButtonHelperStats.convertComms(event, game, player, 1);
                }
                case "convert_2_comms" -> {
                    trackButtonHandler("convert_2_comms");
                    ButtonHelperStats.convertComms(event, game, player, 2, true);
                }
                case "convert_3_comms" -> {
                    trackButtonHandler("convert_3_comms");
                    ButtonHelperStats.convertComms(event, game, player, 3);
                }
                case "convert_4_comms" -> {
                    trackButtonHandler("convert_4_comms");
                    ButtonHelperStats.convertComms(event, game, player, 4);
                }
                case "convert_2_comms_stay" -> {
                    trackButtonHandler("convert_2_comms_stay");
                    ButtonHelperStats.convertComms(event, game, player, 2, false);
                }
                // Don't add anymore cases - use @ButtonHandler
                case "play_when" -> {
                    trackButtonHandler("play_when");
                    AgendaHelper.playWhen(event, game, player, mainGameChannel);
                }
                case "gain_1_tg" -> {
                    trackButtonHandler("gain_1_tg");
                    gain1TG(event, player, game, mainGameChannel);
                }
                case "gain1tgFromLetnevCommander" -> {
                    trackButtonHandler("gain1tgFromLetnevCommander");
                    gain1tgFromLetnevCommander(event, player, game, mainGameChannel);
                }
                case "gain1tgFromMuaatCommander" -> {
                    trackButtonHandler("gain1tgFromMuaatCommander");
                    gain1tgFromMuaatCommander(event, player, game, mainGameChannel);
                }
                case "gain1tgFromCommander" -> {
                    trackButtonHandler("gain1tgFromCommander");
                    gain1tgFromCommander(event, player, game, mainGameChannel); // should be deprecated
                }
                case "resolveHarness" -> {
                    trackButtonHandler("resolveHarness");
                    ButtonHelperStats.replenishComms(event, game, player, false);
                }
                case "pass_on_abilities" -> {
                    trackButtonHandler("pass_on_abilities");
                    ReactionService.addReaction(
                            event,
                            game,
                            player,
                            " is " + event.getButton().getLabel().toLowerCase() + ".");
                }
                case "searchMyGames" -> {
                    trackButtonHandler("searchMyGames");
                    SearchGameHelper.searchGames(
                            event.getUser(), event, false, false, false, true, false, true, false, false);
                }
                case "checkWHView" -> {
                    trackButtonHandler("checkWHView");
                    ButtonHelper.showFeatureType(event, game, DisplayType.wormholes);
                }
                case "checkAnomView" -> {
                    trackButtonHandler("checkAnomView");
                    ButtonHelper.showFeatureType(event, game, DisplayType.anomalies);
                }
                case "checkLegendView" -> {
                    trackButtonHandler("checkLegendView");
                    ButtonHelper.showFeatureType(event, game, DisplayType.legendaries);
                }
                case "checkEmptyView" -> {
                    trackButtonHandler("checkEmptyView");
                    ButtonHelper.showFeatureType(event, game, DisplayType.empties);
                }
                case "checkAetherView" -> {
                    trackButtonHandler("checkAetherView");
                    ButtonHelper.showFeatureType(event, game, DisplayType.aetherstream);
                }
                case "checkCannonView" -> {
                    trackButtonHandler("checkCannonView");
                    ButtonHelper.showFeatureType(event, game, DisplayType.spacecannon);
                }
                case "checkTraitView" -> {
                    trackButtonHandler("checkTraitView");
                    ButtonHelper.showFeatureType(event, game, DisplayType.traits);
                }
                case "checkTechSkipView" -> {
                    trackButtonHandler("checkTechSkipView");
                    ButtonHelper.showFeatureType(event, game, DisplayType.techskips);
                }
                case "checkAttachmView" -> {
                    trackButtonHandler("checkAttachmView");
                    ButtonHelper.showFeatureType(event, game, DisplayType.attachments);
                }
                case "checkShiplessView" -> {
                    trackButtonHandler("checkShiplessView");
                    ButtonHelper.showFeatureType(event, game, DisplayType.shipless);
                }
                case "checkUnlocked" -> {
                    trackButtonHandler("checkUnlocked");
                    ButtonHelper.showFeatureType(event, game, DisplayType.unlocked);
                }
                // Don't add anymore cases - use @ButtonHandler
                default ->
                    MessageHelper.sendMessageToEventChannel(
                            event,
                            "Button " + ButtonHelper.getButtonRepresentation(event.getButton())
                                    + " pressed. This button does not do anything.");
            }
        }
    }

    @Deprecated
    private static void gain1tgFromCommander(
            ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message =
                player.getRepresentation() + " gained 1 trade good " + player.gainTG(1) + " from their commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    private static void gain1tgFromMuaatCommander(
            ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = player.getRepresentation() + " gained 1 trade good " + player.gainTG(1)
                + " from Magmus, the Muaat commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    private static void gain1tgFromLetnevCommander(
            ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = player.getRepresentation() + " gained 1 trade good " + player.gainTG(1)
                + " from Rear Admiral Farran, the Letnev commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    private static void gain1TG(
            ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {

        String label = event.getButton().getLabel();

        if (label.contains("inf") && label.contains("mech")) {
            String message = "Please resolve removing infantry manually, if applicable.";
            ReactionService.addReaction(event, game, player, message);
            return;
        }

        String message = "Gained 1 trade good " + player.gainTG(1, true) + ".";
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        ReactionService.addReaction(event, game, player, message);

        ButtonHelper.deleteMessage(event);

        if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
            MessageHelper.sendMessageToChannel(mainGameChannel, player.getFactionEmoji() + " " + message);
        }
    }

    private static List<Button> getRefreshInfoButtons(Game game) {
        if (game == null) return Buttons.REFRESH_INFO_BUTTONS;
        if (game.isTwilightsFallMode()) return Buttons.REFRESH_INFO_BUTTONS_TF;
        if (game.isThundersEdge()) return Buttons.REFRESH_INFO_BUTTONS_TE;
        return Buttons.REFRESH_INFO_BUTTONS;
    }

    private static void strategicAction(
            ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel mainGameChannel) {
        int scNum = Integer.parseInt(buttonID.replace("strategicAction_", ""));
        PlayStrategyCardService.playSC(event, scNum, game, mainGameChannel, player);
        ButtonHelper.deleteMessage(event);
    }

    private static void trackButtonHandler(String handlerId) {
        RollbarManager.put("button_handler_id", handlerId);
    }

    public static String getButtonProcessingStatistics() {
        var decimalFormatter = new DecimalFormat("#.##");
        double thresholdMissPercent = runtimeWarningService.getThresholdMissPercent();
        return "Button Processor Statistics: " + DateTimeHelper.getCurrentTimestamp()
                + "\n> Total button presses: "
                + runtimeWarningService.getRuntimeSubmissionCount()
                + "\n> Threshold misses: "
                + decimalFormatter.format(thresholdMissPercent) + "%"
                + "\n> Average preprocessing time: "
                + decimalFormatter.format(runtimeWarningService.getAveragePreprocessingTime()) + "ms"
                + "\n> Average processing time: "
                + decimalFormatter.format(runtimeWarningService.getAverageProcessingTime()) + "ms";
    }
}
