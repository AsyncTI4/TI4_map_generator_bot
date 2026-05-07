package ti4.discord.interactions.buttons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.DateTimeHelper;
import ti4.logging.BotLogger;
import ti4.service.statistics.SREStats;

class ButtonRuntimeWarningServiceTest {

    @Test
    void thresholdPauseWarningIncludesOnlySlowTrackedReasons() {
        ButtonRuntimeWarningService service = new ButtonRuntimeWarningService();
        setField(service, "runtimeWarningCount", 14);

        ButtonInteractionEvent fastEvent = mockEvent("Fast", "fast-id");
        ButtonInteractionEvent firstEvent = mockEvent("Alpha", "alpha-id");
        ButtonInteractionEvent secondEvent = mockEvent("Beta", "beta-id");

        try (MockedStatic<AsyncTI4DiscordBot> unstable = mockStatic(AsyncTI4DiscordBot.class);
                MockedStatic<DateTimeHelper> time = mockStatic(DateTimeHelper.class, CALLS_REAL_METHODS);
                MockedStatic<SREStats> sreStats = mockStatic(SREStats.class);
                MockedStatic<BotLogger> logger = mockStatic(BotLogger.class)) {
            unstable.when(AsyncTI4DiscordBot::isUnstable).thenReturn(false);
            time.when(() -> DateTimeHelper.getLongDateTimeFromDiscordSnowflake(any()))
                    .thenReturn(1_000L, 2_000L, 3_000L);
            time.when(() -> DateTimeHelper.getTimestampFromMillisecondsEpoch(2_000L))
                    .thenReturn("`at-2000`");
            time.when(() -> DateTimeHelper.getTimestampFromMillisecondsEpoch(3_000L))
                    .thenReturn("`at-3000`");
            time.when(() -> DateTimeHelper.getTimeRepresentationToMilliseconds(anyLong()))
                    .thenCallRealMethod();

            service.submitNewRuntime(fastEvent, 1_100L, 1_200L, 50L, 75L, 25L);
            service.submitNewRuntime(firstEvent, 2_500L, 4_000L, 50L, 1_400L, 100L);
            service.submitNewRuntime(secondEvent, 3_500L, 6_000L, 75L, 2_400L, 100L);

            logger.verify(() ->
                    BotLogger.error(org.mockito.ArgumentMatchers.argThat(message -> message.contains("**Reasons:**")
                            && !message.contains("__**Fast**__  `[fast-id]`")
                            && message.contains("`at-2000` • __**Alpha**__  `[alpha-id]` • `00m:03s:000ms`")
                            && message.contains("`at-3000` • __**Beta**__  `[beta-id]` • `00m:04s:000ms`"))));
        }
    }

    @Test
    void warningReasonsResetAfterWindowExpires() {
        ButtonRuntimeWarningService service = new ButtonRuntimeWarningService();
        ButtonInteractionEvent firstEvent = mockEvent("Alpha", "alpha-id");
        ButtonInteractionEvent secondEvent = mockEvent("Beta", "beta-id");

        try (MockedStatic<AsyncTI4DiscordBot> unstable = mockStatic(AsyncTI4DiscordBot.class);
                MockedStatic<DateTimeHelper> time = mockStatic(DateTimeHelper.class, CALLS_REAL_METHODS);
                MockedStatic<SREStats> sreStats = mockStatic(SREStats.class);
                MockedStatic<BotLogger> logger = mockStatic(BotLogger.class)) {
            unstable.when(AsyncTI4DiscordBot::isUnstable).thenReturn(false);
            time.when(() -> DateTimeHelper.getLongDateTimeFromDiscordSnowflake(any()))
                    .thenReturn(1_000L, 2_000L);
            time.when(() -> DateTimeHelper.getTimestampFromMillisecondsEpoch(1_000L))
                    .thenReturn("`at-1000`");
            time.when(() -> DateTimeHelper.getTimestampFromMillisecondsEpoch(2_000L))
                    .thenReturn("`at-2000`");
            time.when(() -> DateTimeHelper.getTimeRepresentationToMilliseconds(anyLong()))
                    .thenCallRealMethod();

            service.submitNewRuntime(firstEvent, 2_500L, 4_000L, 50L, 1_400L, 100L);
            setField(service, "lastWarningTime", Instant.now().minusSeconds(61));
            setField(service, "runtimeWarningCount", 15);
            service.submitNewRuntime(secondEvent, 3_500L, 6_000L, 75L, 2_400L, 100L);

            assertThat(getReasons(service))
                    .extracting(ButtonRuntimeWarningServiceTest::buttonRepresentation)
                    .containsExactly("__**Beta**__  `[beta-id]`");
            logger.verify(() -> BotLogger.error(any(String.class)), never());
        }
    }

    private static ButtonInteractionEvent mockEvent(String label, String buttonId) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        Button button = mock(Button.class);
        when(button.getCustomId()).thenReturn(buttonId);
        when(button.getLabel()).thenReturn(label);
        when(button.getEmoji()).thenReturn(null);
        when(event.getButton()).thenReturn(button);
        return event;
    }

    private static List<?> getReasons(ButtonRuntimeWarningService service) {
        Object reasons = getField(service, "thresholdWarningReasons");
        assertThat(reasons).isInstanceOf(List.class);
        return (List<?>) reasons;
    }

    private static Object buttonRepresentation(Object reason) {
        return getField(reason, "buttonRepresentation");
    }

    private static Object getField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setField(ButtonRuntimeWarningService service, String fieldName, Object value) {
        try {
            Field field = ButtonRuntimeWarningService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
