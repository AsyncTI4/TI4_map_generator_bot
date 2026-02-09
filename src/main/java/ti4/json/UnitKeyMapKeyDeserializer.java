package ti4.json;

import ti4.helpers.Units.UnitKey;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.KeyDeserializer;
import tools.jackson.databind.json.JsonMapper;

public class UnitKeyMapKeyDeserializer extends KeyDeserializer {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) {
        return MAPPER.readValue(key, UnitKey.class);
    }
}
