package ti4.ai.trade;

import java.util.OptionalInt;
import java.util.OptionalLong;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiMemory;

@UtilityClass
class TradeMemory {

    private static final int MAX_INT_DIGITS = 9;
    private static final int MAX_LONG_DIGITS = 18;

    static void rewrite(AiMemory memory, String key, String value) {
        memory.remove(key);
        memory.put(key, value);
    }

    static int count(AiMemory memory, String key) {
        return parseInt(memory.get(key).orElse("")).orElse(0);
    }

    static void increment(AiMemory memory, String key) {
        rewrite(memory, key, String.valueOf(count(memory, key) + 1));
    }

    static OptionalLong time(AiMemory memory, String key) {
        return parseLong(memory.get(key).orElse(""));
    }

    static OptionalInt parseInt(String value) {
        return StringUtils.isNumeric(value) && value.length() <= MAX_INT_DIGITS
                ? OptionalInt.of(Integer.parseInt(value))
                : OptionalInt.empty();
    }

    static OptionalLong parseLong(String value) {
        return StringUtils.isNumeric(value) && value.length() <= MAX_LONG_DIGITS
                ? OptionalLong.of(Long.parseLong(value))
                : OptionalLong.empty();
    }
}
