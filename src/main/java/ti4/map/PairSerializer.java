package ti4.map;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import net.dv8tion.jda.internal.utils.tuple.Pair;

public final class PairSerializer extends JsonSerializer<Pair> {

    @Override
    public void serialize(Pair value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeObjectField("left", value.getLeft());
        gen.writeObjectField("right", value.getRight());
        gen.writeEndObject();
    }
}
