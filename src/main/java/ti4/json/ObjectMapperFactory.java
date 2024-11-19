package ti4.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import ti4.helpers.Units.UnitKey;

final public class ObjectMapperFactory {
    private ObjectMapperFactory() {
    }

    /**
     * Builds the standard Jackson ObjectMapper to be used by the entire application.
     * 
     * TODO(Aaron): All instances of ObjectMapper must come from this factory.
     */
    public static ObjectMapper build() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Beacuse UnitKey objects are used as keys within Java maps, we need special
        // serialization logic for them as JSON map keys can only be strings.
        // So we must make the "key" a JSON string which we then unwrap when deserializing.
        SimpleModule simpleMod = new SimpleModule();
        simpleMod.addKeySerializer(UnitKey.class, new UnitKeyMapKeySerializer());
        simpleMod.addKeyDeserializer(UnitKey.class, new UnitKeyMapKeyDeserializer());
        objectMapper.registerModule(simpleMod);

        return objectMapper;
    }
}
