package ti4.json;

import ti4.helpers.Units.UnitKey;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;

public class UnitKeyMapKeySerializer extends ValueSerializer<UnitKey> {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Override
    public void serialize(UnitKey value, JsonGenerator gen, SerializationContext serializers) {
        gen.writeName(MAPPER.writeValueAsString(value));
    }
}
