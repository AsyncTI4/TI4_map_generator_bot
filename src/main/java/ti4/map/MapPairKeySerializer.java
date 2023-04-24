package ti4.map;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;

import net.dv8tion.jda.internal.utils.tuple.Pair;

public class MapPairKeySerializer extends JsonSerializer<Pair<String, Integer>> {

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public void serialize(Pair<String, Integer> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        String serializedPair = value.getLeft() + ";" + value.getRight();
        gen.writeFieldName(serializedPair);
    }
    
}
