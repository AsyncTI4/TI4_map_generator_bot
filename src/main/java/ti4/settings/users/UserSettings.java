package ti4.settings.users;

import static org.apache.commons.lang3.StringUtils.*;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Data;

@Data
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

    private boolean hasAnsweredSurvey;
    private boolean prefersSarweenMsg = true;
    private boolean prefersPillageMsg = true;
    private boolean prefersPassOnWhensAfters = false;
    private boolean prefersPrePassOnSC = true;
    private int autoNoSaboInterval = 0;
    private String whisperPref = "No Preference";
    private String supportPref = "No Preference";
    private String winmakingPref = "No Preference";
    private String takebackPref = "No Preference";
    private String metaPref = "No Preference";
    private String trackRecord = "";

    UserSettings() {} // needed for ObjectMapper

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
