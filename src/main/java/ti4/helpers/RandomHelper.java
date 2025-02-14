package ti4.helpers;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RandomHelper {

    public static boolean isOneInX(int x) {
        return ThreadLocalRandom.current().nextInt(x) == 0;
    }

    public static Object pickRandomFromList(List<Object> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}
