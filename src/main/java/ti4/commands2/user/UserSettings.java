package ti4.commands2.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Data;

@Data
public class UserSettings {

    private String userId;
    private List<String> preferredColourList = new ArrayList<>();
    private int personalPingInterval;
    private Map<String, String> storedValues = new HashMap<>();

    public UserSettings() {} // needed for ObjectMapper

    public UserSettings(String userId) {
        this.userId = userId;
    }

    public void putStoredValue(String settingKey, String settingValue) {
        storedValues.put(settingKey, settingValue);
    }

    public Optional<String> getStoredValue(String settingKey) {
        return Optional.ofNullable(storedValues.get(settingKey));
    }

    public String removeStoredValue(String settingKey) {
        return storedValues.remove(settingKey);
    }
}
