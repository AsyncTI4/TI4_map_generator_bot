package ti4.map;

import java.util.StringTokenizer;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;

public class MapPairKeyDeserializer extends KeyDeserializer {

    @Override
    public ImmutablePair <String, Integer> deserializeKey(String key, DeserializationContext ctxt) {
        StringTokenizer tokenizer = new StringTokenizer(key, ";");
        if (!tokenizer.hasMoreTokens()) return null;
        return new ImmutablePair<>(tokenizer.nextToken(), Integer.parseInt(tokenizer.nextToken()));
    }
}
