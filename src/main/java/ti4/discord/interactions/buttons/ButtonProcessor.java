package ti4.discord.interactions.buttons;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
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
import ti4.helpers.ButtonHelper;
import ti4.helpers.DateTimeHelper;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.logging.RollbarManager;
import ti4.message.MessageHelper;
import ti4.service.game.GameNameService;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.context.SpringContext;

@UtilityClass
public class ButtonProcessor {

    private static final HandlerRegistry<ButtonContext> registry =
            AnnotationHandler.buildHandlerRegistry(ButtonContext.class, ButtonHandler.class);
    private static final ButtonRuntimeMonitor runtimeMonitor = new ButtonRuntimeMonitor();
    private static final String DISABLED_HANDLER_ID = "(combat replay disabled)";
    private static final String UNROUTED_HANDLER_ID = "(unrouted)";

    public static void checkButtonHandlersSetup() {
        if (registry.getSize() == 0) {
            throw new IllegalStateException("No button handlers were registered");
        }
    }

    public static void queue(ButtonInteractionEvent event) {
        ButtonPressTimeline timeline = ButtonPressTimeline.received(event);
        runtimeMonitor.recordQueued();
        String gameName = GameNameService.getGameNameFromChannel(event);
        HandlerRegistry.Route<ButtonContext> route =
                registry.resolve(event.getButton().getCustomId());
        ExecutionLockType lockType = route.shouldSave() ? ExecutionLockType.WRITE : ExecutionLockType.READ;
        ExecutorServiceManager.runAsyncWithLock(
                eventToString(event, gameName),
                gameName,
                event.getMessageChannel(),
                () -> process(event, route, timeline),
                lockType);
    }

    private static String eventToString(ButtonInteractionEvent event, String gameName) {
        return "ButtonProcessor task for `" + event.getUser().getEffectiveName() + "`"
                + (gameName == null ? "" : " in `" + gameName + "`")
                + ": "
                + ButtonHelper.getButtonRepresentation(event.getButton());
    }

    private static void process(
            ButtonInteractionEvent event, HandlerRegistry.Route<ButtonContext> route, ButtonPressTimeline timeline) {
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
                String handlerId = resolveButtonInteractionEvent(context, route);
                timeline.markResolved(handlerId);

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
        BotLogger.logButton(event);
        // TODO: Check whether Rollbar is still configured and read; if not, drop this per-press metadata.
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
                && (buttonID.startsWith(CombatSideBetButtonIds.PREFIX)
                        || buttonID.startsWith(CombatDoubleOrBustButtonIds.PREFIX)
                        || buttonID.startsWith("combatReplayDebug_"));
    }

    private static String resolveButtonInteractionEvent(
            ButtonContext context, HandlerRegistry.Route<ButtonContext> route) {
        ButtonInteractionEvent event = context.getEvent();

        // Skip combat replay buttons when the feature is disabled
        if (!CombatContestSettings.isEnabledStatic() && isCombatReplayButton(context.getButtonID()))
            return DISABLED_HANDLER_ID;
        if (route.dispatch(context)) return route.key();

        context.setShouldSave(false);
        BotLogger.error(
                new LogOrigin(event, context),
                "Unrouted button: `" + context.getButtonID() + "`. This could just be a stale button.");
        MessageHelper.sendMessageToEventChannel(event, "We couldn't resolve what to do with this button.");
        return UNROUTED_HANDLER_ID;
    }

    public static String getButtonProcessingStatistics() {
        return runtimeMonitor.formatStatistics(DateTimeHelper.getCurrentTimestamp());
    }
}
