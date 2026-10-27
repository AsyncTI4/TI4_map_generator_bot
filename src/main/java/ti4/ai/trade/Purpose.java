package ti4.ai.trade;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

enum Purpose {
    SETTLEMENT("Trade settlement"),
    SETTLEMENT_FEE("Trade settlement"),
    EVEN_WASH("Trade settlement"),
    FACILITATED_WASH("facilitated wash"),
    FACILITATION_SETTLE("facilitation settlement"),
    WASH("wash"),
    SELL("sale"),
    NOTE_DEAL("promissory note deal"),
    FRAGMENT_DEAL("relic fragment deal"),
    DESPERATION("purchase"),
    AGENT_TRADE("Carth of Golden Sands deal"),
    TECHNOLOGY_REQUEST("payment to play Technology soon"),
    TECHNOLOGY_HOLD("payment to hold Technology until later"),
    DEBT_PAYMENT("debt payment"),
    DEBT_COLLECTION("debt collection"),
    EXTORTION("extortion demand"),
    COUNTER("counter-offer"),
    CLEANUP("offer"),
    ADOPTED("offer");

    private static final Set<Purpose> SETTLEMENTS =
            EnumSet.of(SETTLEMENT, SETTLEMENT_FEE, EVEN_WASH, FACILITATED_WASH, FACILITATION_SETTLE);
    private static final Set<Purpose> PROPOSALS =
            EnumSet.of(WASH, SELL, NOTE_DEAL, FRAGMENT_DEAL, DESPERATION, TECHNOLOGY_REQUEST, TECHNOLOGY_HOLD);
    private static final Set<Purpose> FAVORS = EnumSet.of(TECHNOLOGY_REQUEST, TECHNOLOGY_HOLD);
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

    boolean asksAFavor() {
        return FAVORS.contains(this);
    }

    boolean waitsLongForHumans() {
        return LONG_HUMAN_REPLY.contains(this);
    }
}
