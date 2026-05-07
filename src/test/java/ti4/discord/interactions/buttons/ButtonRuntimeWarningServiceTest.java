package ti4.discord.interactions.buttons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class ButtonRuntimeWarningServiceTest extends BaseTi4Test {

    @Test
    void pausesWarningsWhenThresholdReached() throws Exception {
        ButtonRuntimeWarningService service = new ButtonRuntimeWarningService();
        setField(service, "runtimeWarningCount", 14);
        setField(service, "pauseWarningsUntil", Instant.EPOCH);
        setField(service, "lastWarningTime", Instant.now());

        long eventTimeMs = System.currentTimeMillis() - 3_000;
        service.submitNewRuntime(mockEvent(eventTimeMs, "threshold_button", "Threshold Test"), eventTimeMs + 3_000,
                eventTimeMs + 4_500, 100, 1_200, 200);

        assertEquals(0, getField(service, "runtimeWarningCount"));
        assertTrue(((Instant) getField(service, "pauseWarningsUntil")).isAfter(Instant.now()));
        assertTrue(((List<?>) getField(service, "thresholdWarningReasons")).isEmpty());
    }

    @Test
    void pauseMessageStartsAfterLoggerTimestampAndListsReasons() throws Exception {
        ButtonRuntimeWarningService service = new ButtonRuntimeWarningService();
        String eventTime = "`2026-05-07 13:00:09.037`";
        addReason(service, eventTime, "__**Test Button**__  `[test_button]`", "00m:01s:446ms");

        String message = (String) getMethod(service, "formatPauseWarningMessage").invoke(service);

        assertEquals(' ', message.charAt(0));
        assertTrue(message.startsWith(" **Buttons are processing slowly. Pausing warnings for 5 minutes.**"));
        assertTrue(message.contains("\n> **Reasons:**\n> - " + eventTime + " • __**Test Button**__  `[test_button]` • `00m:01s:446ms`"));
    }

    private static ButtonInteractionEvent mockEvent(long eventTimeMs, String buttonId, String buttonLabel) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        Button button = mock(Button.class);
        User user = mock(User.class);

        when(event.getInteraction().getTimeCreated())
                .thenReturn(OffsetDateTime.ofInstant(Instant.ofEpochMilli(eventTimeMs), ZoneOffset.UTC));
        when(event.getButton()).thenReturn(button);
        when(button.getCustomId()).thenReturn(buttonId);
        when(button.getLabel()).thenReturn(buttonLabel);
        when(button.getEmoji()).thenReturn(null);
        when(event.getUser()).thenReturn(user);
        when(user.getEffectiveName()).thenReturn("tester");
        when(event.getChannel().getName()).thenReturn("actions");
        when(event.getMessage().getJumpUrl()).thenReturn("https://example.invalid/message");

        return event;
    }

    private static void addReason(ButtonRuntimeWarningService service, String occurredAt, String buttonRepresentation,
            String totalRuntime) throws Exception {
        Class<?> reasonClass = Class.forName("ti4.discord.interactions.buttons.ButtonRuntimeWarningService$ThresholdWarningReason");
        Constructor<?> constructor = reasonClass.getDeclaredConstructor(String.class, String.class, String.class);
        constructor.setAccessible(true);
        Object reason = constructor.newInstance(occurredAt, buttonRepresentation, totalRuntime);

        @SuppressWarnings("unchecked")
        List<Object> reasons = (List<Object>) getField(service, "thresholdWarningReasons");
        reasons.add(reason);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Method getMethod(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }
}
