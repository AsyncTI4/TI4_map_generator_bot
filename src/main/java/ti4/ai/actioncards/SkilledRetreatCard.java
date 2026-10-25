package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CardChains.Source;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.PlanetStake;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatMoment;
import ti4.ai.tactical.RetreatRules;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
class SkilledRetreatCard {

    static final List<String> ALIASES = List.of("s_retreat1", "s_retreat2", "s_retreat3", "s_retreat4");
    static final double SECRET_WORRY_LOSS = 0.5;
    private static final String RESOLVE_HANDLER = "retreat_";
    private static final String DESTINATION_PREFIX = "retreatUnitsFrom_";
    private static final String SKILLED_SUFFIX = "_skilled";
    private static final String SEPARATOR = "_";

    record Retreat(Tile tile, Player opponent, double lossChance) {}

    static List<AiCard> all() {
        return ALIASES.stream()
                .map(alias -> AiActionCards.combatRound(alias, RESOLVE_HANDLER, destinationStep()))
                .toList();
    }

    static double value(Game game, Player seat, CombatMoment moment) {
        return retreat(game, seat, moment)
                .filter(retreat -> 1 - retreat.lossChance() < RetreatRules.retreatBelow(game, seat, retreat.tile()))
                .map(retreat -> savedFleet(seat, retreat) - stakeLeftBehind(game, seat, retreat))
                .orElse(0.0);
    }

    static boolean deniesASecret(Game game, Player seat, CombatMoment moment) {
        return retreat(game, seat, moment)
                .filter(retreat -> retreat.lossChance() >= SECRET_WORRY_LOSS)
                .filter(retreat -> opponentMayScoreASecret(game, seat, retreat.tile(), retreat.opponent()))
                .isPresent();
    }

    private static Optional<Retreat> retreat(Game game, Player seat, CombatMoment moment) {
        if (moment.ground()) return Optional.empty();
        Tile tile = game.getTileByPosition(moment.position());
        if (tile == null
                || tile == seat.getHomeSystemTile()
                || BoardView.nonFighterShips(BoardView.space(tile), seat) == 0
                || !hasUsableDestination(game, seat, tile)) {
            return Optional.empty();
        }
        Optional<Player> opponent = game.getRealPlayers().stream()
                .filter(other -> other != seat && BoardView.hasOwnShips(other, tile))
                .findFirst();
        if (opponent.isEmpty()) return Optional.empty();
        return CombatForecast.lossChance(game, seat, moment.position(), BoardView.SPACE).stream()
                .mapToObj(loss -> new Retreat(tile, opponent.get(), loss))
                .findFirst();
    }

    private static boolean hasUsableDestination(Game game, Player seat, Tile tile) {
        return RetreatRules.skilledRetreatDestinations(game, seat, tile).stream()
                .anyMatch(destination -> !RetreatRules.locksImportantSystem(game, seat, destination));
    }

    private static double savedFleet(Player seat, Retreat retreat) {
        return BoardView.shipCost(BoardView.space(retreat.tile()), seat) * retreat.lossChance();
    }

    private static double stakeLeftBehind(Game game, Player seat, Retreat retreat) {
        double stake = 0;
        for (Planet planet : BoardView.planets(retreat.tile())) {
            if (!seat.getPlanets().contains(planet.getName())) continue;
            stake += PlanetStake.of(game, seat, planet) + PlanetStake.structures(planet, seat);
        }
        return stake * (1 - retreat.lossChance());
    }

    private static boolean opponentMayScoreASecret(Game game, Player seat, Tile tile, Player opponent) {
        if (opponent.isNeutral() || opponent.getSecretsUnscored().isEmpty()) return false;
        return tile.isAnomaly(game, opponent)
                || (tile.isHomeSystem(game) && tile != opponent.getHomeSystemTile())
                || leads(game, seat)
                || BoardView.countInTile(tile, opponent, UnitType.Flagship) > 0
                || BoardView.countInTile(tile, seat, UnitType.Flagship) > 0
                || BoardView.countInTile(tile, seat, UnitType.Warsun) > 0
                || holdsANoteOf(game, opponent, seat);
    }

    private static boolean leads(Game game, Player seat) {
        int points = seat.getTotalVictoryPoints();
        return points > 0 && points >= GameHorizon.highestOtherPoints(game, seat);
    }

    private static boolean holdsANoteOf(Game game, Player holder, Player owner) {
        return holder.getPromissoryNotesInPlayArea().stream().anyMatch(note -> game.getPNOwner(note) == owner);
    }

    private static TargetStep destinationStep() {
        return CardChains.chain(CardChains.last(
                DESTINATION_PREFIX,
                Source.OWNED,
                (context, suffix) -> destinationValue(context.game(), context.seat(), suffix),
                "retreat with Skilled Retreat to"));
    }

    private static double destinationValue(Game game, Player seat, String suffix) {
        String[] parts = suffix.split(SEPARATOR);
        if (!suffix.endsWith(SKILLED_SUFFIX) || parts.length < 2) return Double.NEGATIVE_INFINITY;
        return RetreatRules.skilledDestinationValue(game, seat, parts[1]);
    }
}
