package ti4.map;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import net.dv8tion.jda.internal.utils.tuple.MutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public final class PairDeserializer extends JsonDeserializer<Pair> {

    @Override
    public Pair deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        p.nextValue();
        Integer key= p.getValueAsInt();
        p.nextToken();
        p.nextToken();
        Integer value= p.getValueAsInt();
        p.nextToken();
        return MutablePair.of(key, value);
    }
}
