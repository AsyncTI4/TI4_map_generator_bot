package ti4.map.helper;

import java.util.Map;

public interface StoredValueHelper {

    enum GameStoreKeys {
        TfAbilitiesPurged("purgedAbilities");

        final String value;

        GameStoreKeys(String value) {
            this.value = value;
        }
    }

    enum PlayerStoreKeys {
        TfAbilitiesPurged("purgedAbilities");

        final String value;

        PlayerStoreKeys(String value) {
            this.value = value;
        }
    }

    Map<String, String> getStoredValueMap();

    String getStoredValue(String key);

    String removeStoredValue(String key);

    void setStoredValue(String key, String value);
}
