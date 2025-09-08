package ti4.map.pojo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class MapPairKeySerializer extends JsonSerializer<Pair<String, Integer>> {

    @Override
    public void serialize(Pair<String, Integer> value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        String serializedPair = value.getLeft() + ";" + value.getRight();
        gen.writeFieldName(serializedPair);
    }
}
