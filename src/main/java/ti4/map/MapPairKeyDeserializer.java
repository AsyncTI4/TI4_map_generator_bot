package ti4.map;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class MapPairKeyDeserializer extends KeyDeserializer {

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
        System.out.println(key);
        return key;
    }

    // @Override
    // public Pair<String, Integer> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
    //     System.out.println(p.getValueAsString());
    //     return new ImmutablePair<String, Integer>("hello", 5);
    // }



}
