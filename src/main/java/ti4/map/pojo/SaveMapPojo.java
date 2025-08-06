package ti4.map.pojo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import ti4.json.ObjectMapperFactory;
import ti4.map.Game;
import ti4.map.GameProperties;
import ti4.map.Player;
import ti4.message.BotLogger;

@UtilityClass
public class SaveMapPojo { //

    private static final ObjectMapper objectMapper = ObjectMapperFactory.build();

    public List<String> playerSave(Player player) {
        return saveProps(player, PlayerProperties.class);
    }

    public Player playerLoad(List<String> propLines) throws Exception {
        return loadProps(propLines, new Player("", "", null), PlayerProperties.class);
    }

    public List<String> gameSave(Game game) {
        return saveProps(game, GameProperties.class);
    }

    public Game gameLoad(List<String> propLines) throws Exception {
        return loadProps(propLines, new Game(), GameProperties.class);
    }

    private <T, C extends T> List<String> saveProps(C obj, Class<T> clazz) {
        T props = obj;
        List<String> saveValues = new ArrayList<>();
        for (Field f : clazz.getDeclaredFields()) {
            String saveVal = getSaveStringForField(f, props);
            if (saveVal != null) saveValues.add(saveVal);
        }
        return saveValues;
    }

    private <T, C extends T> C loadProps(List<String> propLines, C obj, Class<T> clazz) throws Exception {
        for (String prop : propLines) {
            int firstSpace = prop.indexOf(' ');
            String fieldName = prop.substring(0, firstSpace);
            String value = prop.substring(firstSpace + 1);
            setFieldToValue(obj, clazz, fieldName, value);
        }
        return obj;
    }

    private <T> void setFieldToValue(T object, Class<T> clazz, String fieldName, String stringValue) throws NoSuchFieldException {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);

            Object value = objectMapper.readValue(stringValue, field.getClass());
            field.set(object, value);
        } catch (NoSuchFieldException e) {
            throw e;
        } catch (Exception e) {}
    }

    private <T> String getSaveStringForField(Field field, T props) {
        try {
            field.setAccessible(true);
            String name = field.getName();
            Object val = field.get(props); // throws

            String strVal = getOutputFromField(val, name);
            return name + " " + strVal;
        } catch (Exception e) {
            BotLogger.error("Error saving pojo", e);
        }
        return null;
    }

    private String getOutputFromField(Object value, String fieldName) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            BotLogger.error("Jackson failed for field: " + fieldName);
            return "";
        }
    }
}
