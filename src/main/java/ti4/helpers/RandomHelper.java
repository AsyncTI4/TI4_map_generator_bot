package ti4.helpers;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RandomHelper {

    private static final ThreadLocal<Random> SEEDED = new ThreadLocal<>();

    public static Random current() {
        Random seeded = SEEDED.get();
        return seeded != null ? seeded : ThreadLocalRandom.current();
    }

    public static void seedThisThread(long seed) {
        SEEDED.set(new Random(seed));
    }

    public static void useForThisThread(Random random) {
        SEEDED.set(random);
    }

    public static void unseedThisThread() {
        SEEDED.remove();
    }

    public static boolean isOneInX(int x) {
        return current().nextInt(x) == 0;
    }

    public static <T> T pickRandomFromList(List<T> list) {
        return list.get(current().nextInt(list.size()));
    }
}
