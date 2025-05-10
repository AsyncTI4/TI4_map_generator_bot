package ti4.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

        // Because UnitKey objects are used as keys within Java maps, we need special
        // serialization logic for them as JSON map keys can only be strings.
        // So we must make the "key" a JSON string which we then unwrap when deserializing.
        SimpleModule simpleMod = new SimpleModule()
            .addKeySerializer(UnitKey.class, new UnitKeyMapKeySerializer())
            .addKeyDeserializer(UnitKey.class, new UnitKeyMapKeyDeserializer());

        return objectMapper
            .registerModule(simpleMod)
            .registerModule(new JavaTimeModule());
    }
}
