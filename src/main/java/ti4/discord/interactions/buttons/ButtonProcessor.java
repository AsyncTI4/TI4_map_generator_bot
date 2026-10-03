package ti4.discord.interactions.buttons;

import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
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
import ti4.helpers.TimedRunnable;
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
    private static final ButtonRuntimeWarningService runtimeWarningService = new ButtonRuntimeWarningService();

    public static void checkButtonHandlersSetup() {
        if (registry.getSize() == 0) {
            throw new IllegalStateException("No button handlers were registered");
        }
    }

    public static void queue(ButtonInteractionEvent event) {
        String gameName = GameNameService.getGameNameFromChannel(event);
        HandlerRegistry.Route<ButtonContext> route =
                registry.resolve(event.getButton().getCustomId());
        ExecutionLockType lockType = route.shouldSave() ? ExecutionLockType.WRITE : ExecutionLockType.READ;
        ExecutorServiceManager.runAsyncWithLock(
                eventToString(event, gameName),
                gameName,
                event.getMessageChannel(),
                () -> process(event, route),
                lockType);
    }

    private static String eventToString(ButtonInteractionEvent event, String gameName) {
        return "ButtonProcessor task for `" + event.getUser().getEffectiveName() + "`"
                + (gameName == null ? "" : " in `" + gameName + "`")
                + ": "
                + ButtonHelper.getButtonRepresentation(event.getButton());
    }

    private static void process(ButtonInteractionEvent event, HandlerRegistry.Route<ButtonContext> route) {
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
                CombatReplayService.setPreInteractionSnapshot(preInteractionSnapshot);
            }
            try {
                beforeTime = System.currentTimeMillis();
                resolveButtonInteractionEvent(context, route);
                resolveRuntime = System.currentTimeMillis() - beforeTime;

                beforeTime = System.currentTimeMillis();
                context.save();
                saveRuntime = System.currentTimeMillis() - beforeTime;

                if (combatReplayService != null && context.getGame() != null) {
                    combatReplayService.onButtonInteractionSettled(context.getGame(), context.getPlayer(), event);
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

    private static void resolveButtonInteractionEvent(
            ButtonContext context, HandlerRegistry.Route<ButtonContext> route) {
        ButtonInteractionEvent event = context.getEvent();

        // Skip combat replay buttons when the feature is disabled
        if (!CombatContestSettings.isEnabledStatic() && isCombatReplayButton(context.getButtonID())) return;
        if (route.dispatch(context)) return;

        MessageHelper.sendMessageToEventChannel(
                event,
                "Button " + ButtonHelper.getButtonRepresentation(event.getButton())
                        + " pressed. This button does not do anything.");
    }

    public static String getButtonProcessingStatistics() {
        var decimalFormatter = new DecimalFormat("#.##");
        double thresholdMissPercent = runtimeWarningService.getThresholdMissPercent();
        return "Button Processor Statistics: " + DateTimeHelper.getCurrentTimestamp()
                + "\n> Total button presses: "
                + runtimeWarningService.getRuntimeSubmissionCount()
                + "\n> Threshold misses: "
                + decimalFormatter.format(thresholdMissPercent) + "% ("
                + runtimeWarningService.getRuntimeThresholdMissCount() + ")"
                + "\n> Average preprocessing time: "
                + decimalFormatter.format(runtimeWarningService.getAveragePreprocessingTime()) + "ms"
                + "\n> Average processing time: "
                + decimalFormatter.format(runtimeWarningService.getAverageProcessingTime()) + "ms";
    }
}
