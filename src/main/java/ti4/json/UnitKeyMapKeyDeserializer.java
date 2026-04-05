package ti4.json;

import ti4.helpers.Units.UnitKey;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.KeyDeserializer;

public class UnitKeyMapKeyDeserializer extends KeyDeserializer {

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) {
        return JsonMapperManager.basic().readValue(key, UnitKey.class);
    }
}
