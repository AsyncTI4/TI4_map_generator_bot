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

public class GlobalSettings {
    private static HashMap<String, Object> settings = new HashMap<>();

    private static synchronized File getFile() {
        return new File(Storage.getStoragePath() + "/global_settings.json");
    }

    public static <T> T getSetting(String attr, Class<T> clazz) {
        return clazz.cast(settings.get(attr));
    }

    public static <T> T getSetting(String attr, Class<T> clazz, T def) {
        if(!settings.containsKey(attr))
            return def;
        return clazz.cast(settings.get(attr));
    }
    public static <T> void setSetting(String attr, T val) {
        settings.put(attr, (Object) val);
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
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
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

}
