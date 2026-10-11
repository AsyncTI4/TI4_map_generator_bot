package ti4.game.helper;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CardIdHelper {

    public static String stripExtra(String id) {
        return id.replace("extra1", "").replace("extra2", "");
    }

    public static int unusedId(Collection<Integer> takenIds, int bound) {
        int identifier = ThreadLocalRandom.current().nextInt(bound);
        while (takenIds.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(bound);
        }
        return identifier;
    }

    public static int unusedId(Collection<Integer> takenIds) {
        return unusedId(takenIds, 1000);
    }

    public static int putWithUnusedId(Map<String, Integer> cards, String id) {
        int identifier = unusedId(cards.values());
        cards.put(id, identifier);
        return identifier;
    }

    public static String keyForId(Map<String, Integer> cards, Integer identifier) {
        for (Map.Entry<String, Integer> entry : cards.entrySet()) {
            if (Objects.equals(entry.getValue(), identifier)) {
                return entry.getKey();
            }
        }
        return "";
    }

    public static Map.Entry<String, Integer> entryForKey(Map<String, Integer> cards, String key) {
        for (Map.Entry<String, Integer> entry : cards.entrySet()) {
            if (entry.getKey().equals(key)) {
                return entry;
            }
        }
        return null;
    }
}
