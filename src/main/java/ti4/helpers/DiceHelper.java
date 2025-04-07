package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import ti4.service.emoji.DiceEmojis;

public class DiceHelper {

    public static class Die {
        private final int threshold;
        private final int result;

        public Die(int threshold) {
            this.threshold = threshold;
            result = ThreadLocalRandom.current().nextInt(1, 11);
        }

        public int getResult() {
            return result;
        }

        public int getThreshold() {
            return threshold;
        }

        public String getGreenDieIfSuccessOrRedDieIfFailure() {
            if (isSuccess()) {
                return DiceEmojis.getGreenDieEmoji(result);
            } else {
                return DiceEmojis.getRedDieEmoji(result);
            }
        }

        public String getRedDieIfSuccessOrGrayDieIfFailure() {
            if (isSuccess())
                return DiceEmojis.getRedDieEmoji(result);
            else
                return DiceEmojis.getGrayDieEmoji(result);
        }

        public String printResult() {
            if (isSuccess()) {
                return String.format("**%d**", result);
            }
            return String.format("%d", result);
        }

        public boolean isSuccess() {
            return result >= threshold;
        }
    }

    public static Die rollDie(int threshold) {
        return new Die(threshold);
    }

    public static List<Die> rollDice(int threshold, int numDice) {
        List<Die> output = new ArrayList<>();
        for (int i = 0; i < numDice; ++i) {
            output.add(rollDie(threshold));
        }
        return output;
    }

    public static String formatDiceResults(List<Die> dice) {
        List<String> resultStrings = dice.stream().map(Die::printResult).toList();
        return String.format("[%s] = %d hits", String.join(", ", resultStrings), countSuccesses(dice));
    }

    public static int countSuccesses(List<Die> dice) {
        return (int) dice.stream().filter(Die::isSuccess).count();
    }

    public static String formatDiceOutput(List<Die> dice) {
        HashMap<Integer, List<Die>> mapByThreshold = new HashMap<>();
        for (Die d : dice) {
            List<Die> l = mapByThreshold.get(d.getThreshold());
            if (l == null) {
                l = new ArrayList<>();
            }
            l.add(d);
            mapByThreshold.put(d.getThreshold(), l);
        }

        List<Integer> smallestToLargest = mapByThreshold.keySet().stream().sorted().toList();
        StringBuilder sb = new StringBuilder();
        for (Integer threshold : smallestToLargest) {
            List<Die> results = mapByThreshold.get(threshold);
            if (results != null && !results.isEmpty()) {
                // "` 3` dice on a ` 5`: [4, **5**, **6**] = 2 hits"
                sb.append(String.format("%d dice vs %d: %s", results.size(), threshold, formatDiceResults(results)));
                sb.append("\n");
            }
        }
        sb.append(String.format("Total: %d hits", countSuccesses(dice)));
        return sb.toString();
    }
}
