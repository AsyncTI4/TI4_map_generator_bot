package ti4.helpers.adjacency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;

// Map states for the parity and transpose tests. Each one switches on a group of adjacency rules on top of a
// fixture map, so every rule's from() and into() get exercised on real tiles.
enum AdjacencyScenarios {
    PLAIN_HYPERLANES("pbd15036", game -> {}),
    PLAIN_BORDERS("game-with-border-anomalies", game -> {}),
    MAP_FEATURES("game-with-border-anomalies", AdjacencyScenarios::addMapFeatures),
    HYPERLANE_OVERRIDES("pbd15036", AdjacencyScenarios::addMapFeatures),
    ACTIVE_PLAYER_TECHS("game-with-border-anomalies", AdjacencyScenarios::activatePlayerWithAdjacencyTechs),
    ACTIVE_GATE_FOLDING_BREAKTHROUGH(
            "game-with-border-anomalies", game -> activateWithoutMecatolHolding(game, "lgf_br")),
    ACTIVE_TWILIGHTS_FALL_GATE_FOLDING(
            "game-with-border-anomalies", game -> activateWithoutMecatolHolding(game, "tf-lazaxgatefolding")),
    ACTIVE_SUNDERED("pbd15036", AdjacencyScenarios::activateSunderedPlayer),
    GHOST_WITH_CRIMSON("game-with-border-anomalies", AdjacencyScenarios::addCrimsonAndEpsilonWormholes),
    OBSIDIAN_MIRROR("game-with-border-anomalies", AdjacencyScenarios::addMovingOblivionFlagship),
    DEEP_FLAGSHIP("game-with-border-anomalies", AdjacencyScenarios::addDeepFlagship);

    final String fixture;
    private final Consumer<Game> setup;

    AdjacencyScenarios(String fixture, Consumer<Game> setup) {
        this.fixture = fixture;
        this.setup = setup;
    }

    // The fixtures are normal games; switching one-way adjacency on keeps every purpose direction-aware, which is
    // what the legacy implementation did and what the transpose check needs to be meaningful.
    void apply(Game game) {
        game.setOneWayAdjacency(true);
        setup.accept(game);
    }

    static List<String> positions(Game game) {
        return game.getTileMap().keySet().stream().sorted().toList();
    }

    static List<Player> playersIncludingNone(Game game) {
        List<Player> players = new ArrayList<>();
        players.add(null);
        players.addAll(game.getRealPlayers());
        return players;
    }

    private static List<Tile> tilesWithPlanets(Game game) {
        return sortedTiles(game)
                .filter(tile -> !tile.getPlanetUnitHolders().isEmpty())
                .toList();
    }

    private static List<Tile> emptyTiles(Game game) {
        return sortedTiles(game)
                .filter(tile -> tile.getPlanetUnitHolders().isEmpty())
                .filter(tile ->
                        tile.getTileModel() == null || !tile.getTileModel().isHyperlane())
                .toList();
    }

    private static Stream<Tile> sortedTiles(Game game) {
        return game.getTileMap().values().stream().sorted(Comparator.comparing(Tile::getPosition));
    }

    // One-way and two-way custom links, an override with a gravity wave on its edge, one-way and closed borders,
    // a severed wormhole and every ingress/egress/breach token.
    private static void addMapFeatures(Game game) {
        List<Tile> planets = tilesWithPlanets(game);
        Tile first = planets.getFirst();
        Tile last = planets.getLast();
        Tile middle = planets.get(planets.size() / 2);
        game.addCustomAdjacentTiles(middle.getPosition(), List.of(first.getPosition()));
        game.addCustomAdjacentTiles(first.getPosition(), List.of(last.getPosition(), middle.getPosition()));
        game.addAdjacentTileOverride(
                planets.get(1).getPosition(), 0, planets.get(planets.size() - 2).getPosition());
        game.addBorderAnomaly(planets.get(1).getPosition(), 0, "gravity_wave");
        game.addBorderAnomaly(planets.get(7).getPosition(), 2, "gravity_wave");
        game.addBorderAnomaly(planets.get(8).getPosition(), 4, "spatial_tear");

        sortedTiles(game)
                .filter(tile -> !Mapper.getWormholes(tile.getTileID()).isEmpty())
                .findFirst()
                .ifPresent(tile -> tile.getSpaceUnitHolder().addToken(Constants.TOKEN_SEVERED));
        planets.get(2).getSpaceUnitHolder().addToken(Constants.TOKEN_INGRESS);
        planets.get(3).getSpaceUnitHolder().addToken(Constants.TOKEN_EGRESS);
        planets.get(4).getSpaceUnitHolder().addToken(Constants.TOKEN_EGRESS);
        planets.get(5).getSpaceUnitHolder().addToken(Constants.TOKEN_BREACH_ACTIVE);
        planets.get(6).getSpaceUnitHolder().addToken(Constants.TOKEN_BREACH_ACTIVE);
    }

    // The active player holds every movement tech and commander that adds adjacency, with a system activated.
    // They must not control Mecatol, or Lazax Gate Folding stays off.
    private static void activatePlayerWithAdjacencyTechs(Game game) {
        Player player = playerWithoutMecatol(game);
        activate(game, player);
        List.of("fsd", "lgf", "tf-starbasewebway", "benatauct").forEach(player::addTech);
        List.of("ghoticommander", "nataucommander", "celdauricommander").forEach(leader -> unlock(player, leader));
        emptyTiles(game).stream()
                .findFirst()
                .ifPresent(tile ->
                        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, player.getColorID()), 1));
        addSpaceDocks(game, player);
    }

    // Each gate folding tech gets its own scenario: they all give Mecatol alpha and beta and would hide each other.
    private static void activateWithoutMecatolHolding(Game game, String tech) {
        Player player = playerWithoutMecatol(game);
        activate(game, player);
        player.addTech(tech);
    }

    private static Player playerWithoutMecatol(Game game) {
        return game.getRealPlayers().stream()
                .filter(candidate -> !candidate.getPlanets().contains("mrte"))
                .filter(candidate -> !candidate.getPlanets().contains("mr"))
                .findFirst()
                .orElseThrow();
    }

    private static void activateSunderedPlayer(Game game) {
        Player player = game.getRealPlayers().getLast();
        activate(game, player);
        player.addAbility("sundered");
        player.addAbility("quantum_entanglement");
    }

    // An Oblivion flagship in an empty system plus one moving out of another, so both flagship sources count.
    private static void addMovingOblivionFlagship(Game game) {
        Player player = game.getRealPlayers().getFirst();
        player.addOwnedUnitByID("oblivion_flagship");
        List<Tile> empty = emptyTiles(game);
        empty.getFirst().addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Flagship, player.getColorID()), 1);
        activate(game, player);
        game.setActiveSystem(empty.getLast().getPosition());
    }

    private static void addDeepFlagship(Game game) {
        Player player = game.getRealPlayers().getFirst();
        player.addOwnedUnitByID("deep_flagship");
        emptyTiles(game)
                .getLast()
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Flagship, player.getColorID()), 1);
    }

    // With Crimson in the game the Ghosts lose their epsilon wormholes, so two epsilon tiles must stop linking for
    // them.
    private static void addCrimsonAndEpsilonWormholes(Game game) {
        Player crimson = game.addPlayer("crimson-test", "crimson-test");
        crimson.setFaction(game, "crimson");
        crimson.setColor("red");
        List<Tile> withoutWormholes = tilesWithPlanets(game).stream()
                .filter(tile -> tile.getWormholes(game).isEmpty())
                .toList();
        withoutWormholes.getFirst().getSpaceUnitHolder().addToken("token_creussepsilon.png");
        withoutWormholes.getLast().getSpaceUnitHolder().addToken("token_creussepsilon.png");
    }

    private static void activate(Game game, Player player) {
        game.setActivePlayerID(player.getUserID());
        game.setActiveSystem(tilesWithPlanets(game).getFirst().getPosition());
    }

    private static void unlock(Player player, String leaderId) {
        player.addLeader(leaderId);
        player.getLeader(leaderId).ifPresent(leader -> leader.setLocked(false));
    }

    private static void addSpaceDocks(Game game, Player player) {
        List<Tile> planets = tilesWithPlanets(game);
        for (Tile tile : List.of(planets.get(1), planets.get(planets.size() - 1))) {
            String planet = tile.getPlanetUnitHolders().getFirst().getName();
            tile.addUnit(planet, Units.getUnitKey(UnitType.Spacedock, player.getColorID()), 1);
        }
    }
}
