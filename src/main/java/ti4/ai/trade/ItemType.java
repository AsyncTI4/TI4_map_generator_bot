package ti4.ai.trade;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

enum ItemType {
    TRADE_GOODS("TGs"),
    COMMODITIES("Comms"),
    SEND_DEBT("SendDebt"),
    CLEAR_DEBT("ClearDebt"),
    PROMISSORY("PNs"),
    FRAGMENTS("Frags"),
    ACTION_CARDS("ACs"),
    PLANETS("Planets"),
    DETAILS("details"),
    UNSUPPORTED("");

    private static final Set<ItemType> COUNTABLE = EnumSet.of(TRADE_GOODS, COMMODITIES, SEND_DEBT, CLEAR_DEBT);
    private static final Set<ItemType> DEBT = EnumSet.of(SEND_DEBT, CLEAR_DEBT);
    private static final Set<ItemType> NAMED = EnumSet.of(PROMISSORY, ACTION_CARDS, PLANETS);

    private final String token;

    ItemType(String token) {
        this.token = token;
    }

    String token() {
        return token;
    }

    static ItemType of(String token) {
        return Arrays.stream(values())
                .filter(type -> type != UNSUPPORTED && type.token.equals(token))
                .findFirst()
                .orElse(UNSUPPORTED);
    }

    boolean countable() {
        return COUNTABLE.contains(this);
    }

    boolean named() {
        return NAMED.contains(this);
    }

    boolean isDebt() {
        return DEBT.contains(this);
    }
}
