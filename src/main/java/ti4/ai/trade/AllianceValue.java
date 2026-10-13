package ti4.ai.trade;

import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.GameHorizon;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.model.LeaderModel;

@UtilityClass
class AllianceValue {

    static final String HUBRIS = "hubris";
    private static final String COMMANDER = "commander";
    private static final double DEFAULT_COMMANDER = 1.0;
    private static final double LOCKED_SHARE = 0.5;
    private static final int LOCKED_HORIZON_ROUNDS = 3;
    private static final double RECEIVER_RESTRICTION = 0.2;
    private static final double GIVE_COST = 0.3;
    private static final double OWN_BACK = 0.5;
    private static final double NEIGHBOUR_GOODWILL = 0.2;
    private static final double MIN_GIVE_COST = 0.1;
    private static final Map<String, Double> COMMANDERS = Map.ofEntries(
            Map.entry("keleres", 3.0),
            Map.entry("jolnar", 2.5),
            Map.entry("hacan", 2.0),
            Map.entry("xxcha", 2.0),
            Map.entry("sol", 2.0),
            Map.entry("nekro", 2.0),
            Map.entry("sardakk", 1.5),
            Map.entry("yin", 1.5),
            Map.entry("muaat", 1.5),
            Map.entry("titans", 1.5),
            Map.entry("argent", 1.5),
            Map.entry("winnu", 1.5),
            Map.entry("mentak", 1.5),
            Map.entry("letnev", 1.0),
            Map.entry("l1z1x", 1.0),
            Map.entry("naalu", 1.0),
            Map.entry("cabal", 1.0),
            Map.entry("saar", 1.0),
            Map.entry("naaz", 1.0),
            Map.entry("yssaril", 1.0),
            Map.entry("nomad", 1.0),
            Map.entry("mahact", 1.0),
            Map.entry("ghost", 0.5),
            Map.entry("empyrean", 0.5),
            Map.entry("arborec", 0.5),
            Map.entry("redcreuss", 0.5));

    static double received(Game game, Player seat, Player owner) {
        if (owner == null) return NotesForTrade.OTHER_NOTE;
        if (seat.hasAbility(HUBRIS)) return 0;
        double restriction = TradeLegality.neighbours(game, seat, owner) ? RECEIVER_RESTRICTION : 0;
        return Math.max(0, commander(game, owner) - restriction);
    }

    static double givenCost(Game game, Player seat, Player receiver) {
        double goodwill = TradeLegality.neighbours(game, seat, receiver) ? NEIGHBOUR_GOODWILL : 0;
        return Math.max(MIN_GIVE_COST, GIVE_COST - goodwill);
    }

    static double ownBack() {
        return OWN_BACK;
    }

    static double commander(Game game, Player owner) {
        Optional<String> commander = commanderId(owner);
        if (commander.isEmpty()) return 0;
        double rating = COMMANDERS.getOrDefault(owner.getFaction(), DEFAULT_COMMANDER);
        if (owner.hasLeaderUnlocked(commander.get())) return rating;
        double horizon = Math.min(1.0, (double) GameHorizon.roundsLeft(game) / LOCKED_HORIZON_ROUNDS);
        return rating * LOCKED_SHARE * horizon;
    }

    private static Optional<String> commanderId(Player owner) {
        return owner.getLeaderIDs().stream()
                .filter(id -> {
                    LeaderModel model = Mapper.getLeader(id);
                    return model != null && COMMANDER.equals(model.getType());
                })
                .findFirst();
    }
}
