package ti4.ai.agenda;

import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.ValueScale;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class AgendaValueMath {

    static final String FOR = "for";
    static final String AGAINST = "against";
    private static final double ORDINARY_RIVAL_STAKE = 1.5;
    private static final double MIN_THREAT = 0.5;
    private static final double MAX_THREAT = 3.0;
    private static final int LAW_ROUNDS_CAP = 5;

    static boolean isFor(@Nullable String outcome) {
        return FOR.equalsIgnoreCase(outcome);
    }

    static boolean isAgainst(@Nullable String outcome) {
        return AGAINST.equalsIgnoreCase(outcome);
    }

    static List<Player> liveRivals(Game game, Player voter) {
        return game.getRealPlayers().stream()
                .filter(player -> player != voter && !player.isEliminated())
                .toList();
    }

    static List<Player> livePlayers(Game game) {
        return game.getRealPlayers().stream()
                .filter(player -> !player.isEliminated())
                .toList();
    }

    static double threat(Game game, Player rival) {
        return Math.clamp(ScoringStakes.rivalStake(game, rival, 1) / ORDINARY_RIVAL_STAKE, MIN_THREAT, MAX_THREAT);
    }

    static double relativeToRivals(Game game, Player voter, ToDoubleFunction<Player> gain) {
        double own = gain.applyAsDouble(voter);
        List<Player> rivals = liveRivals(game, voter);
        if (rivals.isEmpty()) return own;
        double rivalGain = rivals.stream()
                .mapToDouble(rival -> gain.applyAsDouble(rival) * threat(game, rival))
                .sum();
        return own - rivalGain / rivals.size();
    }

    static double rivalGainCost(Game game, Player voter, Player rival, double gain) {
        return gain * threat(game, rival) / Math.max(1, liveRivals(game, voter).size());
    }

    @Nullable
    static Player electedPlayer(Game game, @Nullable String outcome) {
        if (outcome == null || outcome.isBlank()) return null;
        Player elected = game.getPlayerFromColorOrFaction(outcome);
        return elected != null && game.getRealPlayers().contains(elected) ? elected : null;
    }

    static double electedGain(Game game, Player voter, String outcome, ToDoubleFunction<Player> worthToHolder) {
        Player elected = electedPlayer(game, outcome);
        if (elected == null) return 0;
        double worth = worthToHolder.applyAsDouble(elected);
        if (elected == voter) return worth;
        return -rivalGainCost(game, voter, elected, worth);
    }

    static double pointsTo(Game game, Player voter, Player gainer, double points) {
        if (gainer == voter) return ScoringStakes.scoreValue(game, voter, points);
        return -ScoringStakes.rivalStake(game, gainer, points);
    }

    static int lawRounds(Game game) {
        return Math.min(GameHorizon.roundsLeft(game), LAW_ROUNDS_CAP);
    }

    static List<Planet> ownedPlanets(Game game, Player player) {
        return player.getPlanets().stream()
                .map(name -> game.getPlanetsInfo().get(name))
                .filter(Objects::nonNull)
                .toList();
    }

    static double spendValue(Planet planet) {
        return Math.max(ValueScale.RESOURCE * planet.getResources(), ValueScale.INFLUENCE * planet.getInfluence());
    }

    static int unitsOnBoard(Game game, Player player, UnitType type) {
        return game.getTileMap().values().stream()
                .mapToInt(tile -> BoardView.countInTile(tile, player, type))
                .sum();
    }

    static double handValue(Game game, Player holder, Player voter) {
        if (holder != voter) return holder.getAcCount() * ActionCardPrior.AVERAGE_CARD;
        return holder.getActionCards().keySet().stream()
                .mapToDouble(alias -> ActionCardValue.keepValue(game, holder, alias))
                .sum();
    }

    static boolean controlsHomePlanet(Player player) {
        Tile home = player.getHomeSystemTile();
        if (home == null) return false;
        return home.getPlanetUnitHolders().stream()
                .anyMatch(planet -> player.getPlanets().contains(planet.getName()));
    }

    static double unitCost(@Nullable UnitHolder holder, Player player) {
        if (holder == null) return 0;
        double cost = 0;
        for (UnitKey key : holder.getUnitKeysForPlayer(player)) {
            UnitModel model = player.getUnitFromUnitKey(key);
            if (model != null) cost += model.getCost() * holder.getUnitCount(key);
        }
        return cost;
    }
}
