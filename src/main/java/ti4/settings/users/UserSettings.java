package ti4.settings.users;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor // needed for JsonMapper
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSettings {

    private String userId;
    private List<String> preferredColors;
    private int personalPingInterval;
    private int gameLimit;
    private boolean prefersDistanceBasedTacticalActions;
    private String afkHours;
    private boolean hasIndicatedStatPreferences;
    private LocalDateTime lockedFromCreatingGamesUntil;
    private boolean pingOnNextTurn;
    private boolean showTransactables;
    private String activeHours;
    private boolean hasAnsweredSurvey;
    private boolean prefersSarweenMsg = true;
    private boolean prefersPillageMsg = true;
    private String voltronStyle = "eyes";
    private boolean prefersAutoDebtClearance = true;
    private boolean activityTracking = true;
    private boolean prefersPassOnWhensAfters;
    private boolean prefersPrePassOnSC = true;
    private Boolean prefersWrongButtonEphemeral;
    private int autoNoSaboInterval;
    private String whisperPref = "No Preference";
    private String supportPref = "No Preference";
    private String sandbagPref = "No Preference";
    private String winmakingPref = "No Preference";
    private String takebackPref = "No Preference";
    private String metaPref = "No Preference";
    private String trackRecord = "";
    private List<String> matchmakingExpansions;
    private List<String> matchmakingPlayerCounts;
    private List<String> matchmakingVictoryPointGoals;
    private List<String> matchmakingRestrictions;
    private String matchmakingMaxQueueTime;
    private List<String> matchmakingAvoidList;

    UserSettings(String userId) {
        this.userId = userId;
    }

    public List<String> getPreferredColors() {
        return Objects.requireNonNullElse(preferredColors, Collections.emptyList());
    }

    public List<String> getMatchmakingExpansions() {
        return Objects.requireNonNullElse(matchmakingExpansions, Collections.emptyList());
    }

    public List<String> getMatchmakingPlayerCounts() {
        return Objects.requireNonNullElse(matchmakingPlayerCounts, Collections.emptyList());
    }

    public List<String> getMatchmakingVictoryPointGoals() {
        return Objects.requireNonNullElse(matchmakingVictoryPointGoals, Collections.emptyList());
    }

    public List<String> getMatchmakingRestrictions() {
        return Objects.requireNonNullElse(matchmakingRestrictions, Collections.emptyList());
    }

    public List<String> getMatchmakingAvoidList() {
        return Objects.requireNonNullElse(matchmakingAvoidList, Collections.emptyList());
    }

    public void addAfkHour(String hour) {
        if (isBlank(afkHours)) {
            afkHours = hour;
        } else {
            afkHours += ";" + hour;
        }
    }

    public Set<Integer> getActiveHoursAsIntegers() {
        return getHotHours(activeHours);
    }

    public void addActiveHour(int utcHour) {
        if (!activityTracking) {
            return;
        }
        if (isBlank(activeHours)) {
            activeHours = "0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0";
        }
        int x = 0;
        StringBuilder newActiveHours = new StringBuilder();
        for (String hourStr : activeHours.split(";")) {
            int hour = Integer.parseInt(hourStr);
            if (x == utcHour) {
                hour++;
            }
            newActiveHours.append(hour).append(";");
            x++;
        }
        activeHours = newActiveHours.substring(0, newActiveHours.length() - 1);
    }

    public String summarizeActiveHours(String activity) {
        Set<Integer> hotHours = getHotHours(activity);
        if (hotHours.isEmpty()) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        int rangeStart = -1;
        long midnight = 1767225600L; // midnight Jan 1st 2026 UTC
        for (int hour = 0; hour < 24; hour++) {
            if (hotHours.contains(hour)) {
                // Start or continue a range
                if (rangeStart == -1) {
                    rangeStart = hour;
                }
            } else {
                // End of a range
                if (rangeStart != -1) {
                    if (!result.isEmpty()) {
                        result.append(", ");
                    }
                    result.append("<t:")
                            .append(midnight + 60 * 60 * rangeStart)
                            .append(":t>-<t:")
                            .append(midnight + 60 * 60 * hour)
                            .append(":t>");
                    rangeStart = -1;
                }
            }
        }
        if (rangeStart != -1) {
            if (!result.isEmpty()) {
                result.append(", ");
            }
            result.append("<t:")
                    .append(midnight + 60 * 60 * rangeStart)
                    .append(":t>-<t:")
                    .append(midnight + 60 * 60 * 24)
                    .append(":t>");
        }

        return result.isEmpty() ? null : result.toString();
    }

    public String summarizeActiveHoursEmoji(String activity) {
        Set<Integer> hotHours = getHotHours(activity);
        if (hotHours.isEmpty()) {
            return "Not enough data.";
        }

        StringBuilder result = new StringBuilder();
        for (int hour = 0; hour < 24; hour++) {
            if (hotHours.contains(hour)) {
                result.append("🟩");
            } else {
                result.append("🟥");
            }
        }

        return result.isEmpty() ? null : result.toString();
    }

    private static Set<Integer> getHotHours(String activity) {
        int[] checkinsByHour = parseActiveHourCheckins(activity);
        int heat = Arrays.stream(checkinsByHour).sum();
        if (heat < 150) {
            return Collections.emptySet();
        }
        int threshold = heat / 30;
        Set<Integer> hotHours = new LinkedHashSet<>();
        for (int hour = 0; hour < checkinsByHour.length; hour++) {
            if (checkinsByHour[hour] > threshold) {
                hotHours.add(hour);
            }
        }
        return hotHours;
    }

    private static int[] parseActiveHourCheckins(String activity) {
        if (isBlank(activity)) {
            return new int[24];
        }
        String[] hourStrings = activity.split(";");
        int[] checkinsByHour = new int[24];
        int hourLimit = Math.min(hourStrings.length, checkinsByHour.length);
        for (int i = 0; i < hourLimit; i++) {
            checkinsByHour[i] = Integer.parseInt(hourStrings[i].trim());
        }
        return checkinsByHour;
    }

    @JsonGetter("myDateTime")
    public LocalDateTime getLockedFromCreatingGamesUntil() {
        if (lockedFromCreatingGamesUntil == null || lockedFromCreatingGamesUntil.isBefore(LocalDateTime.now())) {
            return null;
        }
        return lockedFromCreatingGamesUntil;
    }

    public boolean isLockedFromCreatingGames() {
        return lockedFromCreatingGamesUntil != null && lockedFromCreatingGamesUntil.isAfter(LocalDateTime.now());
    }
}
