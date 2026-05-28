package ti4.settings.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class UserSettingsTest {

    @Test
    void getActiveHoursAsIntegersReturnsHotHourIndexes() {
        UserSettings settings = new UserSettings();
        settings.setActiveHours(buildActivity(5, 4, 45, 7, 25));

        assertThat(settings.getActiveHoursAsIntegers()).containsExactly(4, 7);
    }

    @Test
    void summarizeActiveHoursUsesSameHotHourLogic() {
        UserSettings settings = new UserSettings();
        String activity = buildActivity(5, 4, 45, 7, 25);

        assertThat(settings.summarizeActiveHours(activity))
                .isEqualTo("<t:1767240000:t>-<t:1767243600:t>, <t:1767250800:t>-<t:1767254400:t>");
        assertThat(settings.summarizeActiveHoursEmoji(activity))
                .isEqualTo("🟥🟥🟥🟥🟩🟥🟥🟩🟥🟥🟥🟥🟥🟥🟥🟥🟥🟥🟥🟥🟥🟥🟥🟥");
    }

    @Test
    void hotHoursAreEmptyWhenNotEnoughData() {
        UserSettings settings = new UserSettings();
        settings.setActiveHours(buildActivity(1));

        assertThat(settings.getActiveHoursAsIntegers()).isEmpty();
        assertThat(settings.summarizeActiveHours(settings.getActiveHours())).isNull();
        assertThat(settings.summarizeActiveHoursEmoji(settings.getActiveHours()))
                .isEqualTo("Not enough data.");
    }

    @Test
    void matchmakingPaceDefaultsToNoPace() {
        UserSettings settings = new UserSettings();

        assertThat(settings.getMatchmakingPace()).isEqualTo("No Pace");
    }

    @Test
    void matchmakingPaceReturnsSetValue() {
        UserSettings settings = new UserSettings();
        settings.setMatchmakingPace("Fast (30 days)");

        assertThat(settings.getMatchmakingPace()).isEqualTo("Fast (30 days)");
    }

    private static String buildActivity(int base, int... updates) {
        int[] hours = new int[24];
        Arrays.fill(hours, base);
        for (int i = 0; i < updates.length; i += 2) {
            hours[updates[i]] = updates[i + 1];
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < hours.length; i++) {
            if (i > 0) {
                result.append(';');
            }
            result.append(hours[i]);
        }
        return result.toString();
    }
}
