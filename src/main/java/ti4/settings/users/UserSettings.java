package ti4.settings.users;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSettings {

    private String userId;
    private Set<String> preferredColors;
    private int personalPingInterval;
    private boolean prefersDistanceBasedTacticalActions;
    private String afkHours;
    private LocalDateTime lockedFromCreatingGamesUntil;

    UserSettings() {} // needed for ObjectMapper

    UserSettings(String userId) {
        this.userId = userId;
    }

    public Set<String> getPreferredColors() {
        return Objects.requireNonNullElse(preferredColors, Collections.emptySet());
    }

    public void addAfkHour(String hour) {
        if (isBlank(afkHours)) {
            afkHours = hour;
        } else {
            afkHours += ";" + hour;
        }
    }

    public LocalDateTime getLockedFromCreatingGamesUntil() {
        if (lockedFromCreatingGamesUntil == null) return null;
        if (lockedFromCreatingGamesUntil.isBefore(LocalDateTime.now())) return null;
        return lockedFromCreatingGamesUntil;
    }

    public boolean isLockedFromCreatingGames() {
        return lockedFromCreatingGamesUntil != null && lockedFromCreatingGamesUntil.isAfter(LocalDateTime.now());
    }
}
