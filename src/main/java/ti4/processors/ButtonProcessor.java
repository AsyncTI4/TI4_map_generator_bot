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
        ExecutorManager.runAsync(eventToString(event, gameName), gameName, event.getMessageChannel(), () -> process(event));
    }

    private static String eventToString(ButtonInteractionEvent event, String gameName) {
        return "ButtonProcessor task for `" + event.getUser().getEffectiveName() + "`" +
            (gameName == null ? "" : " in `" + gameName + "`") +
            ": `" + ButtonHelper.getButtonRepresentation(event.getButton()) + "`";
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
                if (!handleKnownButtons(context)) {
                    ButtonInteractionEvent event2 = context.getEvent();
                    MessageHelper.sendMessageToEventChannel(event2,
                        "Button " + ButtonHelper.getButtonRepresentation(event2.getButton()) +
                            " pressed. This button does not do anything.");
                }
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


    public static String getButtonProcessingStatistics() {
        var decimalFormatter = new DecimalFormat("#.##");
        return "Button Processor Statistics: " + DateTimeHelper.getCurrentTimestamp() + "\n" +
            "> Total button presses: " + runtimeWarningService.getTotalRuntimeSubmissionCount() + ".\n" +
            "> Total threshold misses: " + runtimeWarningService.getTotalRuntimeThresholdMissCount() + ".\n" +
            "> Average preprocessing time: " + decimalFormatter.format(runtimeWarningService.getAveragePreprocessingTime()) + "ms.\n" +
            "> Average processing time: " + decimalFormatter.format(runtimeWarningService.getAverageProcessingTime()) + "ms.";
    }
}
