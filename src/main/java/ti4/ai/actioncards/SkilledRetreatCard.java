package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CardChains.Source;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.PlanetStake;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.strategy.CommandTokenValue;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatMoment;
import ti4.ai.tactical.RetreatRules;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
class SkilledRetreatCard {

    static final List<String> ALIASES = List.of("s_retreat1", "s_retreat2", "s_retreat3", "s_retreat4");
    static final double SECRET_WORRY_LOSS = 0.5;
    private static final double SECRET_POINTS = 1;
    private static final String RESOLVE_HANDLER = "retreat_";
    private static final String DESTINATION_PREFIX = "retreatUnitsFrom_";
    private static final String SKILLED_SUFFIX = "_skilled";
    private static final String SEPARATOR = "_";

    record Retreat(Tile tile, Player opponent, double lossChance, List<TacticalPlan> remembered) {}

    static List<AiCard> all() {
        return ALIASES.stream()
                .map(alias -> AiActionCards.combatRound(alias, RESOLVE_HANDLER, destinationStep()))
                .toList();
    }

    static double value(AiTurnContext context, CombatMoment moment) {
        Game game = context.game();
        Player seat = context.seat();
        return retreat(context, moment)
                .filter(retreat -> 1 - retreat.lossChance() < RetreatRules.retreatBelow(game, seat, retreat.tile()))
                .map(retreat -> savings(game, seat, retreat) - leastDisruption(game, seat, retreat))
                .orElse(0.0);
    }

    static boolean deniesASecret(AiTurnContext context, CombatMoment moment) {
        Game game = context.game();
        Player seat = context.seat();
        return retreat(context, moment)
                .filter(retreat -> retreat.lossChance() >= SECRET_WORRY_LOSS)
                .filter(retreat -> opponentMayScoreASecret(game, seat, retreat.tile(), retreat.opponent()))
                .filter(retreat ->
                        savings(game, seat, retreat) + ScoringStakes.rivalStake(game, retreat.opponent(), SECRET_POINTS)
                                > leastDisruption(game, seat, retreat))
                .isPresent();
    }

    private static Optional<Retreat> retreat(AiTurnContext context, CombatMoment moment) {
        Game game = context.game();
        Player seat = context.seat();
        if (moment.ground()) return Optional.empty();
        Tile tile = game.getTileByPosition(moment.position());
        if (tile == null
                || tile == seat.getHomeSystemTile()
                || BoardView.nonFighterShips(BoardView.space(tile), seat) == 0
                || RetreatRules.skilledRetreatDestinations(game, seat, tile).isEmpty()) {
            return Optional.empty();
        }
        Optional<Player> opponent = game.getRealPlayers().stream()
                .filter(other -> other != seat && BoardView.hasOwnShips(other, tile))
                .findFirst();
        if (opponent.isEmpty()) return Optional.empty();
        List<TacticalPlan> remembered = rememberedElsewhere(context, moment.position());
        return CombatForecast.lossChance(game, seat, moment.position(), BoardView.SPACE).stream()
                .mapToObj(loss -> new Retreat(tile, opponent.get(), loss, remembered))
                .findFirst();
    }

    private static List<TacticalPlan> rememberedElsewhere(AiTurnContext context, String position) {
        return TacticalRules.rememberedPlan(context)
                .filter(plan -> !plan.target().equals(position))
                .stream()
                .toList();
    }

    private static double leastDisruption(Game game, Player seat, Retreat retreat) {
        return RetreatRules.skilledRetreatDestinations(game, seat, retreat.tile()).stream()
                .mapToDouble(destination -> CommandTokenValue.lockLoss(game, seat, destination, retreat.remembered()))
                .min()
                .orElse(0);
    }

    private static double savings(Game game, Player seat, Retreat retreat) {
        return savedFleet(seat, retreat) - stakeLeftBehind(game, seat, retreat);
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
                SkilledRetreatCard::destinationValue,
                "retreat with Skilled Retreat to"));
    }

    private static double destinationValue(AiTurnContext context, String suffix) {
        String[] parts = suffix.split(SEPARATOR);
        if (!suffix.endsWith(SKILLED_SUFFIX) || parts.length < 2) return Double.NEGATIVE_INFINITY;
        return RetreatRules.skilledDestinationValue(
                context.game(), context.seat(), parts[1], rememberedElsewhere(context, parts[0]));
    }
}
