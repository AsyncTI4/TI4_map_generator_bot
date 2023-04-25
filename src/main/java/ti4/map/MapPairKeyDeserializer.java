package ti4.map;

import java.io.IOException;
import java.util.StringTokenizer;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class MapPairKeyDeserializer extends KeyDeserializer {

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public ImmutablePair <String, Integer> deserializeKey(String key, DeserializationContext ctxt) throws IOException {

        // TypeReference<Pair<String, Integer>> typeRef = new TypeReference<Pair<String, Integer>>() {
        // };
        System.out.println(key);
        StringTokenizer tokenizer = new StringTokenizer(key, ";");
        if (!tokenizer.hasMoreTokens()) return null;
        ImmutablePair <String, Integer> pair = new ImmutablePair<String, Integer>(tokenizer.nextToken(), Integer.parseInt(tokenizer.nextToken()));
        return pair;

        // return mapper.readValue(key, typeRef);
    }

    // @Override
    // public Pair<String, Integer> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
    //     System.out.println(p.getValueAsString());
    //     return new ImmutablePair<String, Integer>("hello", 5);
    // }



}
