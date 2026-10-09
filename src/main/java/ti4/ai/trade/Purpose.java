package ti4.ai.trade;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

enum Purpose {
    SETTLEMENT("Trade settlement"),
    SETTLEMENT_FEE("Trade settlement"),
    EVEN_WASH("Trade settlement"),
    WASH("wash"),
    SELL("sale"),
    DESPERATION("purchase"),
    DEBT_PAYMENT("debt payment"),
    DEBT_COLLECTION("debt collection"),
    COUNTER("counter-offer"),
    CLEANUP("offer"),
    ADOPTED("offer");

    private static final Set<Purpose> SETTLEMENTS = EnumSet.of(SETTLEMENT, SETTLEMENT_FEE, EVEN_WASH);
    private static final Set<Purpose> PROPOSALS = EnumSet.of(WASH, SELL, DESPERATION);
    private static final Set<Purpose> LONG_HUMAN_REPLY = EnumSet.of(SETTLEMENT, SETTLEMENT_FEE, DEBT_COLLECTION);

    private final String label;

    Purpose(String label) {
        this.label = label;
    }

    static Optional<Purpose> named(String name) {
        return Arrays.stream(values())
                .filter(purpose -> purpose.name().equals(name))
                .findFirst();
    }

    String label() {
        return label;
    }

    boolean settles() {
        return SETTLEMENTS.contains(this);
    }

    boolean proposes() {
        return PROPOSALS.contains(this);
    }

    boolean waitsLongForHumans() {
        return LONG_HUMAN_REPLY.contains(this);
    }
}
