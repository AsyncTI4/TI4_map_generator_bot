package ti4.game.helper;

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

    static String appendToList(String list, String item) {
        return list == null || list.isEmpty() ? list + item : list + "_" + item;
    }

    static String removeFromList(String list, String item) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        return list.contains(item + "_") ? list.replace(item + "_", "") : list.replace(item, "");
    }
}
