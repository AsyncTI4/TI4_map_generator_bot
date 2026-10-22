package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ti4.game.persistence.TestGameHarness;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.ColorModel;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class PlayerNeighbouringPlayersTest extends BaseTi4Test {

    // A mix of ordinary systems, all four base wormhole systems, red tiles and hyperlanes.
    private static final List<String> TILE_POOL = List.of(
            "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36",
            "37", "38", "39", "40", "41", "42", "43", "44", "45", "46", "47", "48", "49", "50", "83a", "84a", "85a",
            "86a", "87a", "88a", "89a", "90a", "91a");

    @ParameterizedTest
    @MethodSource("savedGames")
    void matchesLegacyImplementationOnSavedGames(String sourceGame) {
        try (TestGameHarness harness = TestGameHarness.fromSourceGame(sourceGame)) {
            assertMatchesLegacy(harness.load());
        }
    }

    static Stream<String> savedGames() {
        return Stream.of("pbd15036", "game-with-border-anomalies", "game-with-displaced-units");
    }

    @ParameterizedTest
    @MethodSource("syntheticGames")
    void matchesLegacyImplementationOnSyntheticGames(
            int playerCount, int rings, long seed, boolean quantumEntanglement, boolean travelBan) {
        Game game = syntheticGame(playerCount, rings, seed, quantumEntanglement, travelBan);
        assertMatchesLegacy(game);
    }

    static Stream<Arguments> syntheticGames() {
        return Stream.of(
                Arguments.of(6, 3, 1L, false, false),
                Arguments.of(8, 4, 2L, true, false),
                Arguments.of(12, 5, 3L, false, false),
                Arguments.of(12, 5, 4L, true, true),
                Arguments.of(16, 6, 5L, false, true),
                // 30+ real players skips the symmetrizing pass entirely.
                Arguments.of(32, 8, 6L, true, false),
                Arguments.of(29, 8, 7L, false, false),
                // Sparse placement leaves some players with no neighbours at all.
                Arguments.of(10, 8, 8L, false, false));
    }

    @Test
    void neighbourSetsAreInRealPlayerOrder() {
        Game game = syntheticGame(12, 5, 11L, false, false);
        for (Player player : game.getRealPlayers()) {
            List<Player> expectedOrder = game.getRealPlayers().stream()
                    .filter(player.getNeighbouringPlayers(true)::contains)
                    .toList();
            assertThat(new ArrayList<>(player.getNeighbouringPlayers(true))).containsExactlyElementsOf(expectedOrder);
        }
    }

    @Test
    void neighbourshipIsSymmetricWithEquivalence() {
        Game game = syntheticGame(16, 6, 12L, true, false);
        for (Player player : game.getRealPlayers()) {
            for (Player neighbour : player.getNeighbouringPlayers(true)) {
                assertThat(neighbour.isNeighboursWith(player)).isTrue();
            }
        }
    }

    private static void assertMatchesLegacy(Game game) {
        List<Player> everyone = new ArrayList<>(game.getPlayers().values());
        assertThat(everyone).isNotEmpty();
        for (Player player : everyone) {
            for (boolean checkEquiv : new boolean[] {false, true}) {
                assertThat(player.getNeighbouringPlayers(checkEquiv))
                        .as("%s checkEquiv=%s", player.getFaction(), checkEquiv)
                        .containsExactlyInAnyOrderElementsOf(legacyNeighbouringPlayers(player, checkEquiv));
            }
            assertThat(player.getNeighbourCount())
                    .isEqualTo(legacyNeighbouringPlayers(player, true).size());
        }
    }

    // Verbatim copy of Player.getNeighbouringPlayers before it was rewritten, kept as the oracle.
    static Set<Player> legacyNeighbouringPlayers(Player self, boolean checkEquiv) {
        Game game = self.getGame();
        Set<Player> adjacentPlayers = new HashSet<>();
        Set<Player> realPlayers = new HashSet<>(
                game.getPlayers().values().stream().filter(Player::isRealPlayer).toList());

        Set<Tile> playersTiles = new HashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerIsInSystem(game, tile, self, true)) {
                playersTiles.add(tile);
            }
        }

        for (Tile tile : playersTiles) {
            adjacentPlayers.addAll(FoWHelper.getAdjacentPlayers(game, tile.getPosition(), false));
            if (realPlayers.size() == adjacentPlayers.size()) break;
        }
        adjacentPlayers.remove(self);
        if (checkEquiv && realPlayers.size() < 30) {
            for (Player p2 : realPlayers) {
                if (!adjacentPlayers.contains(p2)
                        && legacyNeighbouringPlayers(p2, false).contains(self)) {
                    adjacentPlayers.add(p2);
                }
            }
        }
        return adjacentPlayers;
    }

    static Game syntheticGame(int playerCount, int rings, long seed, boolean quantumEntanglement, boolean travelBan) {
        Random random = new Random(seed);
        Game game = new Game();
        game.newGameSetup();
        game.setName("neighbours-" + seed);

        List<String> positions = boardPositions(rings);
        game.setTile(new Tile("18", "000"));
        for (String position : positions) {
            if (random.nextInt(10) == 0) continue;
            game.setTile(new Tile(TILE_POOL.get(random.nextInt(TILE_POOL.size())), position));
        }

        List<String> factions = Mapper.getFactionIDs().stream()
                .filter(faction -> !"ghost".equals(faction) && !"neutral".equals(faction))
                .sorted()
                .toList();
        List<String> colors = Mapper.getColors().stream()
                .map(ColorModel::getName)
                .filter(color -> !"neutral".equals(color))
                .sorted()
                .toList();

        for (int i = 0; i < playerCount; i++) {
            Player player = game.addPlayer("user" + i, "Player " + i);
            String faction = quantumEntanglement && i == 0 ? "ghost" : factions.get(i);
            player.setFaction(game, faction);
            player.setColor(colors.get(i));
            placeAssets(game, player, random);
        }

        // An eliminated player keeps a faction but has nothing on the board.
        Player eliminated = game.addPlayer("eliminated", "Eliminated");
        eliminated.setFaction(game, factions.get(playerCount));
        eliminated.setColor(colors.get(playerCount));

        // A dummy has assets on the board but is not a real player.
        Player dummy = game.addPlayer("dummy", "Dummy");
        dummy.setFaction(game, factions.get(playerCount + 1));
        dummy.setColor(colors.get(playerCount + 1));
        dummy.setDummy(true);
        placeAssets(game, dummy, random);

        // A spectator seat with no faction or color at all.
        game.addPlayer("spectator", "Spectator");

        if (travelBan) {
            game.addLaw("travel_ban", null);
        }
        return game;
    }

    private static List<String> boardPositions(int rings) {
        List<String> positions = new ArrayList<>();
        for (int ring = 1; ring <= rings; ring++) {
            for (int index = 1; index <= ring * 6; index++) {
                String position = String.valueOf(ring * 100 + index);
                if (MapPositionMapper.isTilePositionValid(position)) {
                    positions.add(position);
                }
            }
        }
        return positions;
    }

    private static void placeAssets(Game game, Player player, Random random) {
        List<Tile> tiles = new ArrayList<>(game.getTileMap().values());
        int footprint = 1 + random.nextInt(3);
        for (int i = 0; i < footprint; i++) {
            Tile tile = tiles.get(random.nextInt(tiles.size()));
            if (random.nextBoolean() && !tile.getPlanetUnitHolders().isEmpty()) {
                Planet planet = tile.getPlanetUnitHolders()
                        .get(random.nextInt(tile.getPlanetUnitHolders().size()));
                player.addPlanet(planet.getName());
            } else {
                tile.addUnit("space", Units.getUnitKey(UnitType.Cruiser, player.getColorID()), 1);
            }
        }
    }
}
