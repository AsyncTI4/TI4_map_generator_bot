package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ListHelper {
    /**
     * Get a random item from the list, or null if the list is null or empty.
     * @param <T>
     * @param list
     * @return
     */
    public <T> T randomPick(List<T> list) {
        if (list == null || list.isEmpty()) return null;
        int randomIndex = ThreadLocalRandom.current().nextInt(list.size());
        return list.get(randomIndex);
    }

    /**
     * Slightly shorter way to get a list of a range of integers, inclusive.
     * @param startInclusive
     * @param endInclusive
     * @return
     */
    public List<Integer> listOfIntegers(int startInclusive, int endInclusive) {
        return IntStream.rangeClosed(startInclusive, endInclusive).boxed().toList();
    }

    /**
     * Remove up to 'limit' first-occurring items from the list that match the predicate.
     * @param <T>
     * @param list
     * @param predicate
     * @param limit
     * @return The list of the removed items.
     */
    public <T> List<T> removeByPredicate(List<T> list, Predicate<T> predicate, int limit) {
        if (list == null || list.isEmpty() || predicate == null || limit <= 0) return List.of();
        List<T> toRemove = new ArrayList<>();
        for (T item : list) {
            if (predicate.test(item)) {
              toRemove.add(item);
              if (toRemove.size() >= limit) {
                break;
              }
            }
        }
        list.removeAll(toRemove);
        return toRemove;
    }
}
