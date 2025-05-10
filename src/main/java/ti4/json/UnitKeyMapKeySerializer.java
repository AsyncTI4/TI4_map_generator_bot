package ti4.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import ti4.helpers.Units.UnitKey;

/**
 * JSON map keys can only be strings. So when UnitKey objects are used as Java map keys, we have to
 * use the literal JSON string as the map key.
 */
public class UnitKeyMapKeySerializer extends JsonSerializer<UnitKey> {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void serialize(UnitKey value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeFieldName(mapper.writeValueAsString(value));
    }
}
