package ti4.spring.service.statistics.matchmaking;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

public enum MatchmakingMedal {
    AGENT("Agent", 20),
    COUNCILOR("Councilor", 40),
    COMMANDER("Commander", 60),
    CUSTODIAN("Custodian", 80),
    HERO("Hero", 100);

    public final int PERCENTILE;
    private final String name;

    MatchmakingMedal(String name, int percentile) {
        this.name = name;
        PERCENTILE = percentile;
    }

    public static MatchmakingMedal fromPercentile(BigDecimal percentile) {
        if (percentile.compareTo(BigDecimal.valueOf(AGENT.PERCENTILE)) < 0) {
            return AGENT;
        }
        return Arrays.stream(values())
                .filter(medal -> medal != AGENT)
                .filter(medal -> percentile.compareTo(BigDecimal.valueOf(medal.PERCENTILE)) <= 0)
                .findFirst()
                .orElse(HERO);
    }

    public static Optional<MatchmakingMedal> fromString(String value) {
        return Arrays.stream(values())
                .filter(medal ->
                        medal.name.equalsIgnoreCase(value) || medal.name().equalsIgnoreCase(value))
                .findFirst();
    }

    public String getAutoCompleteName() {
        return name;
    }
}
