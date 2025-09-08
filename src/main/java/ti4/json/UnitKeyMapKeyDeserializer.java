package ti4.json;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import ti4.helpers.Units.UnitKey;

/**
 * UnitKey objects are converted to their literal JSON string representation when they are used as
 * a map key. This reverts them to their original Java object form by deserializing the string.
 */
class UnitKeyMapKeyDeserializer extends KeyDeserializer {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
        return mapper.readValue(key, UnitKey.class);
    }
}
