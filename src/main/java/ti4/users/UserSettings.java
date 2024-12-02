package ti4.users;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Data;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Data
public class UserSettings {

    private String userId;
    private List<String> preferredColourList;
    private int personalPingInterval;
    private boolean prefersDistanceBasedTacticalActions;
    private String afkHours;
    private Map<String, String> storedValues;

    public UserSettings() {} // needed for ObjectMapper

    public UserSettings(String userId) {
        this.userId = userId;
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
