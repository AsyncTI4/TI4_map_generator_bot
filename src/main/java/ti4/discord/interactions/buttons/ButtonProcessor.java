package ti4.discord.interactions.buttons;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.contest.replay.buttons.CombatDoubleOrBustButtonIds;
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
import ti4.helpers.AgendaWhensAftersHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.StatusHelper;
import ti4.helpers.TimedRunnable;
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
    private static final ButtonRuntimeMonitor runtimeMonitor = new ButtonRuntimeMonitor();

    public static void checkButtonHandlersSetup() {
        if (registry.getSize() == 0) {
            throw new IllegalStateException("No button handlers were registered");
        }
    }

    public static void queue(ButtonInteractionEvent event) {
        ButtonPressTimeline timeline = ButtonPressTimeline.received(event);
        runtimeMonitor.recordQueued();
        String gameName = GameNameService.getGameNameFromChannel(event);
        String componentId = event.getButton().getCustomId();
        ExecutionLockType lockType = registry.isSave(componentId) ? ExecutionLockType.WRITE : ExecutionLockType.READ;
        ExecutorServiceManager.runAsyncWithLock(
                eventToString(event, gameName),
                gameName,
                event.getMessageChannel(),
                () -> process(event, timeline),
                lockType);
    }

    private static String eventToString(ButtonInteractionEvent event, String gameName) {
        return "ButtonProcessor task for `" + event.getUser().getEffectiveName() + "`"
                + (gameName == null ? "" : " in `" + gameName + "`")
                + ": "
                + ButtonHelper.getButtonRepresentation(event.getButton());
    }

    private static void process(ButtonInteractionEvent event, ButtonPressTimeline timeline) {
        timeline.markStarted();
        runtimeMonitor.recordStarted();

        ButtonContext context = new ButtonContext(event);
        timeline.markCompleted(ButtonPressStage.CONTEXT);
        if (!context.isValid()) {
            runtimeMonitor.recordInvalid();
            return;
        }

        log(event);
        timeline.markCompleted(ButtonPressStage.LOG);

        try {
            CombatReplayService combatReplayService =
                    CombatContestSettings.isEnabledStatic() ? SpringContext.getBean(CombatReplayService.class) : null;
            if (combatReplayService != null) {
                CombatReplayService.PreInteractionSnapshot preInteractionSnapshot =
                        combatReplayService.capturePreInteractionSnapshot(context.getGame());
                CombatReplayService.setPreInteractionSnapshot(preInteractionSnapshot);
                timeline.markCompleted(ButtonPressStage.REPLAY_SNAPSHOT);
            }
            try {
                resolveButtonInteractionEvent(context);
                timeline.markCompleted(ButtonPressStage.RESOLVE);

                context.save();
                timeline.markCompleted(ButtonPressStage.SAVE);

                if (combatReplayService != null && context.getGame() != null) {
                    combatReplayService.onButtonInteractionSettled(context.getGame(), context.getPlayer(), event);
                    timeline.markCompleted(ButtonPressStage.REPLAY_SETTLE);
                }
            } finally {
                if (combatReplayService != null) {
                    CombatReplayService.clearPreInteractionSnapshot();
                }
            }
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(event, context), "Something went wrong with button interaction", e);
        } finally {
            RollbarManager.clear();
        }

        timeline.markFinished();
        if (!AsyncTI4DiscordBot.isUnstable()) {
            runtimeMonitor.submit(event, timeline);
        }
    }

    private static void log(ButtonInteractionEvent event) {
        // TODO: These timings are temporary to track down any spikes...
        int warningThresholdSeconds = 1;
        new TimedRunnable("ButtonProcessor BotLogger log", warningThresholdSeconds, () -> BotLogger.logButton(event))
                .run();

        new TimedRunnable("ButtonProcessor Rollbar setup", warningThresholdSeconds, () -> {
                    RollbarManager.putInteractionMetadata("button", event);
                    RollbarManager.put("button_id", event.getButton().getCustomId());
                    RollbarManager.put("game_name", GameNameService.getGameNameFromChannel(event));
                })
                .run();

        new TimedRunnable("ButtonProcessor user settings save", warningThresholdSeconds, () -> {
                    User user = event.getUser();
                    UserSettings userSettings = UserSettingsManager.get(user.getId());
                    int currentHourUTC = ZonedDateTime.now(ZoneId.of("UTC")).getHour();
                    userSettings.addActiveHour(currentHourUTC);
                    UserSettingsManager.save(userSettings);
                })
                .run();
    }

    private static boolean isCombatReplayButton(String buttonID) {
        return buttonID != null
                && (buttonID.startsWith(CombatSideBetButtonIds.PREFIX)
                        || buttonID.startsWith(CombatDoubleOrBustButtonIds.PREFIX)
                        || buttonID.startsWith("combatReplayDebug_"));
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
                    AgendaWhensAftersHelper.playWhen(event, game, player, mainGameChannel);
                }
                case "gain_1_tg" -> {
                    trackButtonHandler("gain_1_tg");
                    gain1TG(event, player, game, mainGameChannel);
                }
                case "gain1tgFromLetnevCommander" -> {
                    trackButtonHandler("gain1tgFromLetnevCommander");
                    gain1tgFromLetnevCommander(event, player, game);
                }
                case "gain1tgFromMuaatCommander" -> {
                    trackButtonHandler("gain1tgFromMuaatCommander");
                    gain1tgFromMuaatCommander(event, player, game);
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

    private static void gain1tgFromMuaatCommander(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentation() + " gained 1 trade good " + player.gainTG(1)
                + " from Magmus, the Muaat commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    private static void gain1tgFromLetnevCommander(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentation() + " gained 1 trade good " + player.gainTG(1)
                + " from Rear Admiral Farran, the Letnev commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
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
        return runtimeMonitor.formatStatistics(DateTimeHelper.getCurrentTimestamp());
    }
}
