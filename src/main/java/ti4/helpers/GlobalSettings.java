package ti4.helpers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

public class GlobalSettings {
    private static HashMap<String, Object> settings = new HashMap<>();

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
            writer.writeValue(new File("global_settings.json"), settings);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void loadSettings() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();

        try {
            settings = reader.readValue(new File("global_settings.json"));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            // THis _probably_ means there's no file, which isn't critical.
            // So this is intended to silently fail.
            //e.printStackTrace();
        }
    }

}
