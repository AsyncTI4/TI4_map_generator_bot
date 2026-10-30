package ti4.ai.actioncards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CardChains.Source;
import ti4.ai.eval.BoardView;
import ti4.ai.strategy.CardFacade;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;

@UtilityClass
class TokenCards {

    static final String UNEXPECTED_ACTION = "unexpected";
    static final String SIGNAL_JAMMING = "jamming";
    private static final String LIFT_PREFIX = "removeCCFromBoard_unexpected_";
    private static final String JAM_PLAYER_PREFIX = "signalJammingStep2_";
    private static final String JAM_ORIGIN_PREFIX = "signalJammingStep3_";
    private static final String JAM_SYSTEM_PREFIX = "signalJammingStep4_";
    private static final String SEPARATOR = "_";
    private static final double JAMMED_PLANET_SHARE = 0.3;
    private static final double JAMMED_MECATOL = 1.0;

    static AiCard unexpectedAction() {
        return AiActionCards.component(
                UNEXPECTED_ACTION,
                TokenCards::unexpectedActionPlan,
                "resolveUnexpected",
                LIFT_PREFIX,
                CardChains.chain(CardChains.last(
                        LIFT_PREFIX,
                        Source.OWNED,
                        (context, suffix) -> CardFacade.tokenUnlockGain(context.game(), context.seat(), before(suffix)),
                        "lift the command token from")));
    }

    static AiCard signalJamming() {
        return AiActionCards.component(
                SIGNAL_JAMMING,
                TokenCards::signalJammingPlan,
                "resolveSignalJammingStep1",
                List.of(JAM_PLAYER_PREFIX, JAM_ORIGIN_PREFIX, JAM_SYSTEM_PREFIX),
                CardChains.chain(
                        CardChains.next(
                                JAM_PLAYER_PREFIX,
                                Source.MENTIONED,
                                (context, suffix) -> jamPlayerValue(context.game(), context.seat(), suffix),
                                "choose the player whose token to place:"),
                        CardChains.next(
                                JAM_ORIGIN_PREFIX,
                                Source.MENTIONED,
                                (context, suffix) -> jamOriginValue(context.game(), context.seat(), suffix),
                                "choose the origin system"),
                        CardChains.last(
                                JAM_SYSTEM_PREFIX,
                                Source.MENTIONED,
                                (context, suffix) -> jamSystemValue(context.game(), context.seat(), suffix),
                                "jam")));
    }

    static Optional<Plan> unexpectedActionPlan(Game game, Player seat) {
        List<String> positions = positionsWithOwnToken(game, seat);
        if (positions.isEmpty() || game.isFowMode()) return Optional.empty();
        String best = CardFacade.bestTokenToLift(game, seat, positions).orElse(positions.getFirst());
        double gain = Math.max(0, CardFacade.tokenUnlockGain(game, seat, best));
        return Optional.of(Plan.aimedAt(gain, best + SEPARATOR + seat.getFaction(), "from " + best + "."));
    }

    private static List<String> positionsWithOwnToken(Game game, Player seat) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.hasPlayerCC(seat))
                .map(Tile::getPosition)
                .sorted()
                .toList();
    }

    static Optional<Plan> signalJammingPlan(Game game, Player seat) {
        if (game.isFowMode()) return Optional.empty();
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .flatMap(other -> jamSystems(game, seat).stream().map(system -> jam(game, seat, other, system)))
                .max(Comparator.comparingDouble(Jam::value)
                        .thenComparing(Jam::player, Comparator.reverseOrder())
                        .thenComparing(Jam::system, Comparator.reverseOrder())
                        .thenComparing(Jam::origin, Comparator.reverseOrder()))
                .map(jam -> Plan.aimedAt(
                        jam.value(),
                        CardChains.target(
                                jam.player(),
                                jam.player() + SEPARATOR + jam.origin(),
                                jam.player() + SEPARATOR + jam.system()),
                        "on " + jam.system() + "."));
    }

    private record Jam(String player, String origin, String system, double value) {}

    private record JamSystem(String origin, String system) {}

    private static Jam jam(Game game, Player seat, Player other, JamSystem system) {
        double interest = interest(game, other, game.getTileByPosition(system.system()));
        return new Jam(
                other.getFaction(), system.origin(), system.system(), interest * TableStanding.of(game, seat, other));
    }

    private static List<JamSystem> jamSystems(Game game, Player seat) {
        return game.getTileMap().values().stream()
                .filter(tile -> BoardView.hasOwnShips(seat, tile))
                .flatMap(origin -> reach(game, seat, origin).stream()
                        .map(system -> new JamSystem(origin.getPosition(), system.getPosition())))
                .sorted(Comparator.comparing(JamSystem::system).thenComparing(JamSystem::origin))
                .toList();
    }

    private static List<Tile> reach(Game game, Player seat, Tile origin) {
        List<Tile> tiles = new ArrayList<>();
        tiles.add(origin);
        for (String position : FoWHelper.getAdjacentTilesAndNotThisTile(game, origin.getPosition(), seat, false)) {
            Tile tile = game.getTileByPosition(position);
            if (tile != null) tiles.add(tile);
        }
        return tiles.stream().filter(tile -> !tile.isHomeSystem(game)).toList();
    }

    private static double interest(Game game, Player other, Tile system) {
        boolean nearby = BoardView.hasOwnShips(other, system)
                || FoWHelper.getAdjacentTilesAndNotThisTile(game, system.getPosition(), other, false).stream()
                        .map(game::getTileByPosition)
                        .anyMatch(tile -> tile != null && BoardView.hasOwnShips(other, tile));
        if (!nearby || other.getTacticalCC() <= 0) return 0;
        double planets = BoardView.planets(system).stream()
                .filter(planet -> !other.getPlanets().contains(planet.getName()))
                .mapToDouble(BoardView::planetValue)
                .sum();
        boolean mecatol = system == game.getMecatolTile() && !other.getPlanets().contains("mr");
        return JAMMED_PLANET_SHARE * planets + (mecatol ? JAMMED_MECATOL : 0);
    }

    private static double jamPlayerValue(Game game, Player seat, String faction) {
        Player other = game.getPlayerFromColorOrFaction(faction);
        if (other == null || other == seat) return Double.NEGATIVE_INFINITY;
        return -other.getTotalVictoryPoints();
    }

    private static double jamOriginValue(Game game, Player seat, String suffix) {
        Tile tile = game.getTileByPosition(after(suffix));
        return tile != null && BoardView.hasOwnShips(seat, tile) ? 0 : Double.NEGATIVE_INFINITY;
    }

    private static double jamSystemValue(Game game, Player seat, String suffix) {
        Player other = game.getPlayerFromColorOrFaction(before(suffix));
        Tile tile = game.getTileByPosition(after(suffix));
        if (other == null || other == seat || tile == null || tile.isHomeSystem(game)) return Double.NEGATIVE_INFINITY;
        return interest(game, other, tile);
    }

    private static String before(String suffix) {
        return suffix.contains(SEPARATOR) ? suffix.substring(0, suffix.indexOf(SEPARATOR)) : suffix;
    }

    private static String after(String suffix) {
        return suffix.contains(SEPARATOR) ? suffix.substring(suffix.indexOf(SEPARATOR) + 1) : suffix;
    }
}
