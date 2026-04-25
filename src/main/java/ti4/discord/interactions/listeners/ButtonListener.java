package ti4.discord.interactions.listeners;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.function.Consumers;
import ti4.contest.replay.buttons.CombatSideBetButtonIds;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.ButtonProcessor;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DateTimeHelper;
import ti4.logging.BotLogger;
import ti4.spring.service.deploy.ActiveLeaseService;

class ButtonListener extends ListenerAdapter {

    private static final Set<String> BUTTONS_TO_THINK_ABOUT = Set.of("showGameAgain");

    private static ButtonListener instance;

    public static ButtonListener getInstance() {
        if (instance == null) instance = new ButtonListener();
        return instance;
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        EventLatencyChecker.check(event);
        if (!ActiveLeaseService.shouldHandleCurrentProcessInteraction()) {
            return;
        }
        if (!JdaService.isReadyToReceiveCommands()) {
            event.reply("You pressed: " + ButtonHelper.getButtonRepresentation(event.getButton(), false)
                            + "\nPlease try again in a few minutes. The bot is rebooting.")
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        // Only defer if button does not spawn a Modal
        if (!isModalSpawner(event)) {
            if (shouldShowBotIsThinking(event)) {
                event.deferReply(true).queue(Consumers.nop(), BotLogger::catchRestError);
            } else {
                event.deferEdit().queue(Consumers.nop(), BotLogger::catchRestError);
            }
        }

        ButtonProcessor.queue(event);
    }

    /**
     * @return whether a button should show the bot is thinking - need to add the following at end of execution:
     * `    if (event instanceof ButtonInteractionEvent buttonEvent) {
     * buttonEvent.getHook().deleteOriginal().queue(Consumers.nop(), BotLogger::catchRestError);
     * }`
     */
    private static boolean shouldShowBotIsThinking(ButtonInteractionEvent event) {
        String buttonId = event.getButton().getCustomId();
        return BUTTONS_TO_THINK_ABOUT.contains(buttonId)
                || (buttonId != null && buttonId.startsWith(CombatSideBetButtonIds.PREFIX));
    }

    /**
     * @return whether the button spawns a Modal - modals must be a raw undeferred reply
     */
    private static boolean isModalSpawner(ButtonInteractionEvent event) {
        return event.getButton().getCustomId().contains("~MDL");
    }

    private static final class EventLatencyChecker {

        private static final long THRESHOLD_MS = 1000;
        private static final Duration WINDOW = Duration.ofMinutes(5);
        private static final int EVENT_COUNT_THRESHOLD = 10;

        private static final ConcurrentLinkedDeque<Long> slowEvents = new ConcurrentLinkedDeque<>();

        // prevents repeated alerts
        private static final AtomicLong lastWarningTimeMs = new AtomicLong(0);

        static void check(GenericInteractionCreateEvent event) {
            long eventTimeMs = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
            long latencyMs = System.currentTimeMillis() - eventTimeMs;

            if (latencyMs <= THRESHOLD_MS) {
                return;
            }

            long now = System.currentTimeMillis();

            slowEvents.addLast(now);

            // trim old entries outside 5-minute window
            Long ts;
            long windowMs = WINDOW.toMillis();

            while ((ts = slowEvents.peekFirst()) != null && now - ts > windowMs) {
                slowEvents.pollFirst();
            }

            if (slowEvents.size() < EVENT_COUNT_THRESHOLD) {
                return;
            }

            long lastWarn = lastWarningTimeMs.get();
            if (now - lastWarn < windowMs) {
                return;
            }

            lastWarningTimeMs.set(now);

            slowEvents.clear();

            BotLogger.error("⚠ **High Discord/JDA latency detected: " + EVENT_COUNT_THRESHOLD
                    + "+ slow events in the last " + WINDOW.toMinutes() + "  minutes.**");
        }
    }
}
