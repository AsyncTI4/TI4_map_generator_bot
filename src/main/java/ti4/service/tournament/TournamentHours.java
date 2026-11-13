package ti4.service.tournament;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
public class TournamentHours {

    public record Window(Set<Integer> hours, int playersActive, int playersWithData) {}

    public static Map<String, Set<Integer>> activeHours(Collection<String> userIds) {
        Map<String, Set<Integer>> hours = new LinkedHashMap<>();
        Map<String, UserSettings> settings = UserSettingsManager.getAll(userIds);
        for (String userId : userIds) {
            UserSettings userSettings = settings.get(userId);
            hours.put(userId, userSettings == null ? Set.of() : userSettings.getActiveHoursAsIntegers());
        }
        return hours;
    }

    public static Window bestWindow(List<Set<Integer>> memberHours) {
        List<Set<Integer>> withData =
                memberHours.stream().filter(hours -> !hours.isEmpty()).toList();
        int[] activePerHour = new int[24];
        for (Set<Integer> hours : withData) {
            for (int hour : hours) {
                if (hour >= 0 && hour < 24) activePerHour[hour]++;
            }
        }
        int most = 0;
        for (int count : activePerHour) {
            most = Math.max(most, count);
        }
        Set<Integer> window = new LinkedHashSet<>();
        if (most > 0) {
            for (int hour = 0; hour < 24; hour++) {
                if (activePerHour[hour] == most) window.add(hour);
            }
        }
        return new Window(window, most, withData.size());
    }

    public static String describe(Window window, int groupSize) {
        String missing = groupSize > window.playersWithData()
                ? " · " + (groupSize - window.playersWithData()) + " without hour data"
                : "";
        if (window.hours().isEmpty()) return "No active-hour data" + missing;
        String label = window.playersActive() == groupSize
                ? "Everyone active"
                : window.playersActive() + "/" + groupSize + " active";
        return label + ": " + UserSettings.summarizeHours(window.hours()) + missing;
    }
}
