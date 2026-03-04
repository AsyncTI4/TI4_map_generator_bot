package ti4.json;

import ti4.helpers.Units.UnitKey;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class UnitKeyMapKeySerializer extends ValueSerializer<UnitKey> {

    @Override
    public void serialize(UnitKey value, JsonGenerator gen, SerializationContext serializers) {
        gen.writeName(JsonMapperManager.basic().writeValueAsString(value));
    }
}
