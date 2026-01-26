package ti4.settings.users;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor // needed for ObjectMapper
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSettings {

    private String userId;
    private List<String> preferredColors;
    private int personalPingInterval;
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

    UserSettings(String userId) {
        this.userId = userId;
    }

    public List<String> getPreferredColors() {
        return Objects.requireNonNullElse(preferredColors, Collections.emptyList());
    }

    public void addAfkHour(String hour) {
        if (isBlank(afkHours)) {
            afkHours = hour;
        } else {
            afkHours += ";" + hour;
        }
    }

    public void addActiveHour(int utcHour) {
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

    public boolean enoughHeatData() {
        return amountOfHeatData() > 150;
    }

    public int amountOfHeatData() {
        if (isBlank(activeHours)) {
            activeHours = "0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0";
        }
        int count = 0;
        for (String hourStr : activeHours.split(";")) {
            int hour = Integer.parseInt(hourStr);
            count += hour;
        }
        return count;
    }

    public String summarizeActiveHours(String activity) {
        // Parse the input string
        if (isBlank(activity)) {
            activity = "0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0";
        }
        String[] hourStrings = activity.split(";");
        int[] checkins = new int[24];

        for (int i = 0; i < hourStrings.length; i++) {
            checkins[i] = Integer.parseInt(hourStrings[i].trim());
        }

        StringBuilder result = new StringBuilder();
        int rangeStart = -1;

        for (int hour = 0; hour < 24; hour++) {
            if (checkins[hour] > (amountOfHeatData() / 30)) {
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
                    if (rangeStart == hour - 1) {
                        // Single hour, not a range
                        result.append(rangeStart);
                    } else {
                        // Range of hours
                        result.append(rangeStart).append("-").append(hour - 1);
                    }
                    rangeStart = -1;
                }
            }
        }
        if (rangeStart != -1) {
            if (!result.isEmpty()) {
                result.append(", ");
            }
            if (rangeStart == 23) {
                result.append(23);
            } else {
                result.append(rangeStart).append("-").append(23);
            }
        }

        return result.isEmpty() ? "No active hours" : result.toString();
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
