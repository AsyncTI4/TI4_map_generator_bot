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

    private static final int LIKELY_ACTIVE_LOCAL_START = 15;
    private static final int LIKELY_ACTIVE_LOCAL_END = 23;

    public record Window(Set<Integer> hours, int playersActive, int playersWithData) {}

    public enum Source {
        ACTIVITY("active hours"),
        AFK_SETTINGS("hours from AFK settings"),
        TIME_ZONE("hours from time zone"),
        NONE("no hour data");

        public final String label;

        Source(String label) {
            this.label = label;
        }
    }

    public record Profile(Set<Integer> hours, Source source) {

        public String describe() {
            return source == Source.NONE ? source.label : hours.size() + " " + source.label;
        }
    }

    public static Map<String, Set<Integer>> activeHours(Collection<String> userIds) {
        Map<String, Set<Integer>> hours = new LinkedHashMap<>();
        profiles(userIds).forEach((userId, profile) -> hours.put(userId, profile.hours()));
        return hours;
    }

    public static Map<String, Profile> profiles(Collection<String> userIds) {
        Map<String, Profile> profiles = new LinkedHashMap<>();
        Map<String, UserSettings> settings = UserSettingsManager.getAll(userIds);
        for (String userId : userIds) {
            profiles.put(userId, profile(settings.get(userId)));
        }
        return profiles;
    }

    static Profile profile(UserSettings settings) {
        if (settings == null) return new Profile(Set.of(), Source.NONE);
        Set<Integer> activity = settings.getActiveHoursAsIntegers();
        if (!activity.isEmpty()) return new Profile(activity, Source.ACTIVITY);
        Set<Integer> notAfk = hoursOutside(settings.getAfkHours());
        if (!notAfk.isEmpty()) return new Profile(notAfk, Source.AFK_SETTINGS);
        if (settings.getUtcOffsetMinutes() != null) {
            return new Profile(likelyActiveUtcHours(settings.getUtcOffsetMinutes()), Source.TIME_ZONE);
        }
        return new Profile(Set.of(), Source.NONE);
    }

    static Set<Integer> hoursOutside(String afkHours) {
        if (afkHours == null || afkHours.isBlank()) return Set.of();
        Set<Integer> afk = new LinkedHashSet<>();
        for (String hour : afkHours.split(";")) {
            String trimmed = hour.trim();
            if (trimmed.matches("\\d{1,2}")) afk.add(Integer.parseInt(trimmed));
        }
        Set<Integer> available = new LinkedHashSet<>();
        for (int hour = 0; hour < 24; hour++) {
            if (!afk.contains(hour)) available.add(hour);
        }
        return available.size() == 24 ? Set.of() : available;
    }

    static Set<Integer> likelyActiveUtcHours(int utcOffsetMinutes) {
        Set<Integer> hours = new LinkedHashSet<>();
        for (int local = LIKELY_ACTIVE_LOCAL_START; local < LIKELY_ACTIVE_LOCAL_END; local++) {
            int utcMinutes = local * 60 - utcOffsetMinutes;
            hours.add(Math.floorMod(Math.floorDiv(utcMinutes, 60), 24));
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
