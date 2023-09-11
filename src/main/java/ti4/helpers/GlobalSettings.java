package ti4.helpers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.util.Map;

public class GlobalSettings {

    //Adding an enum here will make it show up as an AutoComplete option in the /admin setting setting_name parameter, and will allow you to get the setting easier
    public enum ImplementedSettings {
        MAX_THREAD_COUNT, //How many threads can be open before force closing old ones
        THREAD_AUTOCLOSE_COUNT, //How many threads to close when above max thread count
        ;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    private static Map<String, Object> settings = new HashMap<>();

    private static File getFile() {
        return new File(Storage.getStoragePath() + "/global_settings.json");
    }

    public static <T> T getSetting(String attr, Class<T> clazz) {
        return clazz.cast(settings.get(attr));
    }

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
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void loadSettings() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() {
        };

        try {
            settings = reader.readValue(Files.readString(getFile().toPath()), HashMap.class);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            // THis _probably_ means there's no file, which isn't critical.
            // So this is intended to silently fail.
            e.printStackTrace();
        }
    }

    public static Map<String, Object> getSettings() {
        return settings;
    }

}
