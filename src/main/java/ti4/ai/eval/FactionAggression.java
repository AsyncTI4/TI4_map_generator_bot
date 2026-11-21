package ti4.ai.eval;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import ti4.game.Player;

public enum FactionAggression {
    AGGRESSIVE(1.2, "sardakk", "l1z1x", "letnev", "saar", "mentak", "muaat", "nekro", "cabal", "nomad"),
    PASSIVE(0.8, "hacan", "xxcha", "jolnar", "empyrean", "arborec", "ghost", "winnu", "naaz", "yssaril"),
    AVERAGE(1.0);

    private final double factor;
    private final Set<String> factions;

    FactionAggression(double factor, String... factions) {
        this.factor = factor;
        this.factions = new HashSet<>(Arrays.asList(factions));
    }

    public double factor() {
        return factor;
    }

    public Set<String> factions() {
        return factions;
    }

    public static FactionAggression of(Player player) {
        String faction = player.getFaction();
        return Arrays.stream(values())
                .filter(level -> level.factions.contains(faction))
                .findFirst()
                .orElse(AVERAGE);
    }
}
