package ti4.ai.tactical;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.brain.TickMemo;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.eval.FactionStrength;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.model.ActionCardModel;
import ti4.model.Mapper;

@UtilityClass
public class TacticalStrength {

    static final double UNSEEN_CARD = 0.2;
    static final double HARSH_CARD = 0.5;
    static final double ASSAULT_CANNON = 1;
    static final double X89 = 2;
    static final double PDS_TWO = 1;
    static final double MIN_ANGER_WEIGHT = 0.25;
    static final double MAX_ANGER_WEIGHT = 2;
    private static final double SMOOTHING = 1;
    private static final String ASSAULT_CANNON_TECH = "asc";
    private static final Set<String> X89_TECHS = Set.of("x89", "x89c4");
    private static final String PDS_TWO_TECH = "pds2";
    private static final Set<UnitType> GROUND = Set.of(UnitType.Infantry, UnitType.Mech);
    private static final Set<String> MEAN_CARDS = Set.of(
            "Spy",
            "Seize Artifact",
            "Uprising",
            "Signal Jamming",
            "Reactor Meltdown",
            "Plague",
            "Cripple Defenses",
            "Unstable Planet",
            "Insubordination",
            "Assassinate Representative");
    private static final List<String> COMBAT_WINDOWS =
            List.of("combat", "invasion", "space cannon", "assign hits", "sustain damage");
    private static final String MEMO = "tactical.strength";

    public static double own(Game game, Player seat) {
        return remembered(game, seat, DecidingSeat.knowsHandOf(seat));
    }

    public static double seenBy(Game game, Player other) {
        return remembered(game, other, false);
    }

    public static double angerWeight(Game game, Player seat, Player other) {
        if (seat == other || !AiExperiments.enabled(seat, Experiment.STRENGTH_WEIGHTED_ANGER)) return 1;
        double ratio = (seenBy(game, other) + SMOOTHING) / (own(game, seat) + SMOOTHING);
        return Math.clamp(ratio, MIN_ANGER_WEIGHT, MAX_ANGER_WEIGHT);
    }

    private static double remembered(Game game, Player player, boolean seesHand) {
        Map<String, Double> memo = TickMemo.mapFor(game, MEMO);
        return memo.computeIfAbsent(player.getFaction() + "|" + seesHand, key -> rate(game, player, seesHand));
    }

    static double rate(Game game, Player player, boolean seesHand) {
        double cards = seesHand ? HARSH_CARD * harshCards(player) : UNSEEN_CARD * player.getAcCount();
        return plasticCost(game, player) + cards + technology(player) + FactionStrength.bonus(player);
    }

    private static long harshCards(Player player) {
        return player.getActionCards().keySet().stream()
                .filter(TacticalStrength::harsh)
                .count();
    }

    private static boolean harsh(String alias) {
        ActionCardModel card = Mapper.getActionCard(alias);
        if (card == null) return false;
        if (MEAN_CARDS.contains(card.getName())) return true;
        String window = card.getWindow() == null ? "" : card.getWindow().toLowerCase(Locale.ROOT);
        return COMBAT_WINDOWS.stream().anyMatch(window::contains);
    }

    private static double plasticCost(Game game, Player player) {
        double total = 0;
        for (Tile tile : game.getTileMap().values()) {
            for (Map.Entry<UnitType, Integer> ship :
                    BoardView.ships(BoardView.space(tile), player).entrySet()) {
                total += IncomingForces.cost(player, ship.getKey()) * ship.getValue();
            }
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitType type : GROUND) {
                    total += IncomingForces.cost(player, type) * BoardView.count(holder, player, type);
                }
            }
        }
        return total;
    }

    private static double technology(Player player) {
        double bonus = 0;
        if (player.hasTech(ASSAULT_CANNON_TECH)) bonus += ASSAULT_CANNON;
        if (X89_TECHS.stream().anyMatch(player::hasTech)) bonus += X89;
        if (player.hasTech(PDS_TWO_TECH)) bonus += PDS_TWO;
        return bonus;
    }
}
