package ti4.settings;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import ti4.helpers.Storage;
import ti4.message.logging.BotLogger;

public class GlobalSettings {

    // Adding an enum here will make it show up as an AutoComplete option in the /admin setting setting_name parameter,
    // and will allow you to get the setting easier
    public enum ImplementedSettings {
        DEBUG, // When true, additional show additional debug messages
        UPLOAD_DATA_TO_WEB_SERVER, // Whether to send map and data to the web server
        MAX_THREAD_COUNT, // How many threads can be open before force closing old ones
        THREAD_AUTOCLOSE_COUNT, // How many threads to close when above max thread count
        FILE_IMAGE_CACHE_MAX_SIZE, //
        FILE_IMAGE_CACHE_EXPIRE_TIME_MINUTES, //
        URL_IMAGE_CACHE_MAX_SIZE, //
        URL_IMAGE_CACHE_EXPIRE_TIME_MINUTES, //
        GUILD_ID_FOR_NEW_GAME_CATEGORIES, // Which guild to create new game categories in (DEPRECATED)
        MAX_GAMES_PER_CATEGORY, // Max # of games when creating a category
        ALLOW_GAME_CREATION,
        READY_TO_RECEIVE_COMMANDS, // Whether the bot is ready to receive commands
        BOT_LOG_WEBHOOK_URL; // Webhook URL to send rogue bot log messages to

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        public int getAsInt(int defaultValue) {
            return getSetting(toString(), Integer.class, defaultValue);
        }

        public boolean getAsBoolean(boolean defaultValue) {
            return getSetting(toString(), Boolean.class, defaultValue);
        }

        public String getAsString(String defaultValue) {
            return getSetting(toString(), String.class, defaultValue);
        }
    }

    private static Map<String, Object> settings = new HashMap<>();

    public static <T> T getSetting(String attr, Class<T> clazz, T defaultValue) {
        if (!settings.containsKey(attr)) return defaultValue;
        return clazz.cast(settings.get(attr));
    }

    public static <T> void setSetting(ImplementedSettings setting, T value) {
        setSetting(setting.toString(), value);
    }

    public static <T> void setSetting(String attr, T value) {
        settings.put(attr, value);
        saveSettings();
    }

    public static boolean settingExists(ImplementedSettings setting) {
        return settings.containsKey(setting.toString());
    }

    private static void saveSettings() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        try {
            writer.writeValue(getFile(), settings);
        } catch (IOException e) {
            BotLogger.error("Error saving Global Settings", e);
        }
    }

    public static void loadSettings() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JavaType settingsType = mapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class);
            settings = mapper.readValue(Files.readString(getFile().toPath()), settingsType);
        } catch (IOException e) {
            // THis _probably_ means there's no file, which isn't critical.
            // So this is intended to silently fail.
            // e.printStackTrace();
        }
    }

    private static Map<String, Object> getSettings() {
        return settings;
    }

    public static String getSettingsRepresentation() {
        StringBuilder sb = new StringBuilder("### Global Settings:\n```");
        for (Entry<String, Object> entries :
                settings.entrySet().stream().sorted(Entry.comparingByKey()).toList()) {
            sb.append(entries.getKey()).append(": ").append(entries.getValue()).append("\n");
        }
        sb.append("```");
        return sb.toString();
    }

    private static File getFile() {
        return new File(Storage.getStoragePath() + "/global_settings.json");
    }
}
