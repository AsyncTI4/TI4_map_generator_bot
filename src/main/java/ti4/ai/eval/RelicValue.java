package ti4.ai.eval;

import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;

@UtilityClass
public class RelicValue {

    public static final double BASE = 3.5;
    private static final String SHARD = "shard";
    private static final String TOMB_OF_EMPHIDIA = "attachment_tombofemphidia.png";
    private static final String WAR_SUN = "ws";
    private static final String NAAZ_ROKHA = "naaz";
    private static final double EARLY_ROUND_PENALTY = 0.5;
    private static final int MAW_LAST_ROUND = 4;
    private static final double SHARD_PENALTY_PER_LEGENDARY = 2;
    private static final double NANOFORGE_SHARD_PENALTY = 4;
    private static final double EMPHIDIA_TOMB_BONUS = 2;
    private static final double THALNOS_WAR_SUN_BONUS = 1;

    private static final Map<String, ToDoubleBiFunction<Game, Player>> VALUES = Map.ofEntries(
            Map.entry("dominusorb", (game, seat) -> 4),
            Map.entry("mawofworlds", (game, seat) -> game.getRound() > MAW_LAST_ROUND ? 0 : 4),
            Map.entry("emelpar", (game, seat) -> 6 - game.getRound()),
            Map.entry(SHARD, RelicValue::shard),
            Map.entry("stellarconverter", (game, seat) -> 1),
            Map.entry("codex", (game, seat) -> 7),
            Map.entry("emphidia", RelicValue::emphidia),
            Map.entry("thalnos", RelicValue::thalnos),
            Map.entry("obsidian", (game, seat) -> 8),
            Map.entry("prophetstears", (game, seat) -> decaying(3, game)),
            Map.entry("dynamiscore", (game, seat) -> decaying(5, game)),
            Map.entry("titanprototype", (game, seat) -> decaying(5, game)),
            Map.entry("nanoforge", RelicValue::nanoforge),
            Map.entry("circletofthevoid", (game, seat) -> decaying(5, game)),
            Map.entry("bookoflatvinia", (game, seat) -> 8),
            Map.entry("neuraloop", (game, seat) -> decaying(2, game)),
            Map.entry("metalivoidarmaments", (game, seat) -> 4),
            Map.entry("quantumcore", (game, seat) -> decaying(2, game)),
            Map.entry("thesilverflame", (game, seat) -> 0),
            Map.entry("lightrailordnance", (game, seat) -> 2),
            Map.entry("metalivoidshielding", (game, seat) -> 4),
            Map.entry("thetriad", RelicValue::triad),
            Map.entry("heartofixth", (game, seat) -> 0));

    public static double of(Game game, Player seat, String relicId) {
        ToDoubleBiFunction<Game, Player> value = VALUES.get(relicId);
        return value == null ? 0 : Math.max(0, value.applyAsDouble(game, seat));
    }

    public static double draw(Game game, Player seat) {
        List<String> deck = game.getAllRelics();
        List<String> cards = deck == null ? List.copyOf(VALUES.keySet()) : deck;
        if (cards.isEmpty()) return 0;
        return cards.stream()
                .mapToDouble(card -> of(game, seat, card))
                .average()
                .orElse(0);
    }

    private static double decaying(double base, Game game) {
        return base - EARLY_ROUND_PENALTY * game.getRound();
    }

    private static double shard(Game game, Player seat) {
        return 6 - SHARD_PENALTY_PER_LEGENDARY * legendaryPlanets(game, seat);
    }

    private static double nanoforge(Game game, Player seat) {
        double value = decaying(5, game);
        return seat.getRelics().contains(SHARD) ? value - NANOFORGE_SHARD_PENALTY : value;
    }

    private static double emphidia(Game game, Player seat) {
        double value = decaying(5, game);
        return tombOnTheBoard(game) ? value + EMPHIDIA_TOMB_BONUS : value;
    }

    private static double thalnos(Game game, Player seat) {
        boolean warSuns = ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, WAR_SUN) > 0
                || NAAZ_ROKHA.equals(seat.getFaction());
        return warSuns ? THALNOS_WAR_SUN_BONUS : 0;
    }

    private static double triad(Game game, Player seat) {
        long kinds = IntStream.of(seat.getCrf(), seat.getIrf(), seat.getHrf(), seat.getUrf())
                .filter(count -> count > 0)
                .count();
        return decaying(4, game) + kinds;
    }

    private static long legendaryPlanets(Game game, Player seat) {
        return seat.getPlanets().stream()
                .map(name -> game.getPlanetsInfo().get(name))
                .filter(planet -> planet != null && planet.isLegendary())
                .count();
    }

    private static boolean tombOnTheBoard(Game game) {
        return game.getPlanetsInfo().values().stream()
                .map(Planet::getTokenList)
                .anyMatch(tokens -> tokens.contains(TOMB_OF_EMPHIDIA));
    }
}
