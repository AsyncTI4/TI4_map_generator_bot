package ti4.users;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private Map<String, String> storedValues;

    public UserSettings() {} // needed for ObjectMapper

    public UserSettings(String userId) {
        this.userId = userId;
    }

    public Set<String> getPreferredColors() {
        return Objects.requireNonNullElse(preferredColors, Collections.emptySet());
    }

    public void addPreferredColor(String color) {
        if (preferredColors == null) {
            preferredColors = new HashSet<>();
        }
        preferredColors.add(color);
    }

    public void putStoredValue(String settingKey, String settingValue) {
        if (storedValues == null) {
            storedValues = new HashMap<>();
        }
        storedValues.put(settingKey, settingValue);
    }

    public Optional<String> getStoredValue(String settingKey) {
        if (storedValues == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storedValues.get(settingKey));
    }

    public String removeStoredValue(String settingKey) {
        if (storedValues == null) {
            return null;
        }
        return storedValues.remove(settingKey);
    }

    public void addAfkHour(String hour) {
        if (isBlank(afkHours)) {
            afkHours = hour;
        } else {
            afkHours += ";" + hour;
        }
    }
}
