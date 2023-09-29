package ti4.helpers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import ti4.message.BotLogger;

import java.util.Map;
import java.util.Map.Entry;

public class GlobalSettings {

    //Adding an enum here will make it show up as an AutoComplete option in the /admin setting setting_name parameter, and will allow you to get the setting easier
    public enum ImplementedSettings {
        DEBUG, //When true, additional show additional debug messages
        UPLOAD_DATA_TO_WEB_SERVER, //Whether to send map and data to the web server
        MAX_THREAD_COUNT, //How many threads can be open before force closing old ones
        THREAD_AUTOCLOSE_COUNT, //How many threads to close when above max thread count
        FILE_IMAGE_CACHE_MAX_SIZE,
        FILE_IMAGE_CACHE_EXPIRE_TIME_MINUTES,
        URL_IMAGE_CACHE_SIZE,
        URL_IMAGE_CACHE_EXPIRE_TIME_MINUTES,
        LOG_CACHE_STATS_INTERVAL_MINUTES;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    private static Map<String, Object> settings = new HashMap<>();

    public static <T> T getSetting(String attr, Class<T> clazz, T def) {
        if (!settings.containsKey(attr))
            return def;
        return clazz.cast(settings.get(attr));
    }

    public static <T> void setSetting(String attr, T val) {
        settings.put(attr, val);
    }

    public static void saveSettings() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        try {
            writer.writeValue(getFile(), settings);
        } catch (IOException e) {
            BotLogger.log("Error saving Global Settings", e);
            e.printStackTrace();
        }
    }

    public static void loadSettings() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            settings = mapper.readValue(Files.readString(getFile().toPath()), HashMap.class);
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
        for (Entry<String, Object> entries : getSettings().entrySet().stream().sorted(Entry.comparingByKey()).toList()) {
            sb.append(entries.getKey()).append(": ").append(entries.getValue()).append("\n");
        }
        sb.append("```");
        return sb.toString();
    }

    private static File getFile() {
        return new File(Storage.getStoragePath() + "/global_settings.json");
    }
}
