package ti4.service.objectives.fog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.service.info.ListPlayerInfoService;
import ti4.testUtils.BaseTi4Test;

class FogSecretsTest extends BaseTi4Test {

    private static final String POK_FOG_DECK = "secret_objectives_pok_fog";
    private Game game;
    private Player sol;
    private Player hacan;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setFowMode(true);
        game.resetSecretObjectiveDeck(Mapper.getDeck(POK_FOG_DECK));
        game.setRound(3);
        game.setTile(new Tile("18", "000"));
        for (int ring = 1; ring <= 3; ring++) {
            for (int i = 1; i <= ring * 6; i++) {
                String position = ring + String.format("%02d", i);
                game.setTile(new Tile("46", position));
            }
        }
        sol = addPlayer("sol-id", "sol", "blue", "301");
        hacan = addPlayer("hacan-id", "hacan", "green", "310");
    }

    @Test
    void mapScaledTargetsRoundUpWithAMinimumOfTwo() {
        assertThat(FogSecrets.mapScaledTarget(0, 8)).isEqualTo(1);
        assertThat(FogSecrets.mapScaledTarget(1, 8)).isEqualTo(1);
        assertThat(FogSecrets.mapScaledTarget(3, 8)).isEqualTo(2);
        assertThat(FogSecrets.mapScaledTarget(16, 8)).isEqualTo(2);
        assertThat(FogSecrets.mapScaledTarget(17, 8)).isEqualTo(3);
        assertThat(FogSecrets.mapScaledTarget(5, 2)).isEqualTo(3);
    }

    @Test
    void foundCountsRememberedSystems() {
        assertThat(FogSecrets.progress(FogSecrets.FIND_MECATOL, game, sol)).isZero();

        sol.addFogTile("18", "000", null);
        sol.addFogTile("46", "310", null);

        assertThat(FogSecrets.progress(FogSecrets.FIND_MECATOL, game, sol)).isEqualTo(1);
        assertThat(FogSecrets.progress(FogSecrets.FIND_THRONES, game, sol)).isEqualTo(1);
    }

    @Test
    void ghostFleetCountsOnlyShipsNoOtherPlayerCanSee() {
        addShips(sol, "304", UnitType.Cruiser, 5);
        addShips(hacan, "310", UnitType.Cruiser, 1);

        assertThat(FogSecrets.progress(FogSecrets.GHOST_FLEET, game, sol)).isEqualTo(5);

        // A hacan ship next door puts 304 in view; a second, unseen fleet still counts.
        addShips(hacan, "305", UnitType.Cruiser, 1);
        assertThat(FogSecrets.progress(FogSecrets.GHOST_FLEET, game, sol)).isZero();
        addShips(sol, "313", UnitType.Cruiser, 5);
        assertThat(FogSecrets.progress(FogSecrets.GHOST_FLEET, game, sol)).isEqualTo(5);
    }

    @Test
    void roundLimitedSecretsStayOneShortBeforeRoundThree() {
        addShips(sol, "304", UnitType.Cruiser, 5);
        game.setRound(2);

        assertThat(FogSecrets.progress(FogSecrets.GHOST_FLEET, game, sol)).isEqualTo(4);
        assertThat(FogSecrets.progress(FogSecrets.SHROUD_THRONE, game, sol)).isZero();

        game.setRound(3);
        assertThat(FogSecrets.progress(FogSecrets.SHROUD_THRONE, game, sol)).isEqualTo(1);
    }

    @Test
    void shroudTheThroneFailsOnceSomeoneWatchesTheHomeSystem() {
        assertThat(FogSecrets.progress(FogSecrets.SHROUD_THRONE, game, sol)).isEqualTo(1);

        addShips(hacan, "302", UnitType.Destroyer, 1);
        assertThat(FogSecrets.progress(FogSecrets.SHROUD_THRONE, game, sol)).isZero();
    }

    @Test
    void wideNetNeedsSystemsThatDoNotTouch() {
        for (String position : List.of("301", "304", "307", "310", "313")) {
            addShips(sol, position, UnitType.Destroyer, 1);
        }
        assertThat(FogSecrets.spreadSystems(game, sol, 5)).isEqualTo(5);
    }

    @Test
    void wideNetCountsANeighbouringClusterOnlyOnce() {
        for (String position : List.of("101", "102", "103")) {
            addShips(sol, position, UnitType.Destroyer, 1);
        }
        assertThat(FogSecrets.spreadSystems(game, sol, 5)).isEqualTo(2);
    }

    @Test
    void statusFogSecretsFeedTheGenericObjectiveProgress() {
        sol.addFogTile("18", "000", null);

        // Mecatol Rex plus its six neighbours.
        assertThat(ListPlayerInfoService.getObjectiveThreshold(FogSecrets.FIND_MECATOL, game))
                .isEqualTo(7);
        assertThat(ListPlayerInfoService.getPlayerProgressOnObjective(FogSecrets.FIND_MECATOL, game, sol))
                .isEqualTo(1);
    }

    @Test
    void combinedDeckIsThePokDeckPlusEveryFogSecret() {
        List<String> fogSecrets = Mapper.getDeck(FogSecrets.DECK).getCardIDs();
        List<String> pok = Mapper.getDeck("secret_objectives_pok").getCardIDs();

        assertThat(fogSecrets).hasSize(32).allMatch(FogSecrets::isFogSecret);
        assertThat(Mapper.getDeck(POK_FOG_DECK).getCardIDs())
                .hasSize(72)
                .containsAll(pok)
                .containsAll(fogSecrets);
        assertThat(FogSecrets.usesFogDeck(game)).isTrue();
    }

    @Test
    void nothingRunsWithoutTheFogDeckOrOutsideFog() {
        sol.setSecret(FogSecretHooks.STRIKE_FROM_FOG);
        game.setSoDeckID("secret_objectives_pok");
        FogSecretHooks.onSystemActivated(game, sol);
        FogSecretHooks.afterCombatHits(game, hacan, game.getTileByPosition("306"), null, "spacecombat");
        assertThat(game.getStoredValueMap().keySet()).noneMatch(key -> key.startsWith("fogSecret"));

        game.setFowMode(false);
        assertThat(ListPlayerInfoService.getObjectiveThreshold(FogSecrets.FIND_MECATOL, game))
                .isZero();
        assertThat(FogSecrets.isFogSecret(game, FogSecrets.FIND_MECATOL)).isFalse();
    }

    @Test
    void agendaVotesKeepTheirOrderPerOutcome() {
        Player jolnar = addPlayer("jolnar-id", "jolnar", "red", "307");
        game.setCurrentAgendaVote("For", "blue_3;green_2;red_4");
        game.setCurrentAgendaVote("Against", "green_6");

        assertThat(FogSecretHooks.votesByOutcome(game).get("for").keySet()).containsExactly(sol, hacan, jolnar);
        assertThat(FogSecretHooks.totalsByOutcome(game)).containsEntry("for", 9).containsEntry("against", 6);
    }

    @Test
    void kingmakersAreVotersWithoutWhomTheOutcomeLoses() {
        addPlayer("jolnar-id", "jolnar", "red", "307");
        game.setCurrentAgendaVote("For", "blue_6;green_1");
        game.setCurrentAgendaVote("Against", "red_4");
        assertThat(FogSecretHooks.kingmakers(game, "for")).containsExactly(sol);

        // A tie broken by the speaker has no kingmaker.
        game.setCurrentAgendaVote("For", "blue_3;green_1");
        assertThat(FogSecretHooks.kingmakers(game, "for")).isEmpty();
    }

    @Test
    void consensusNeedsThreeVotersAndNoOpposition() {
        Player jolnar = addPlayer("jolnar-id", "jolnar", "red", "307");
        game.setCurrentAgendaVote("For", "blue_3;green_2");
        assertThat(FogSecretHooks.isConsensus(game, "for")).isFalse();

        game.setCurrentAgendaVote("For", "blue_3;green_2;red_1");
        assertThat(FogSecretHooks.isConsensus(game, "for")).isTrue();

        game.setCurrentAgendaVote("Against", jolnar.getColor() + "_1");
        assertThat(FogSecretHooks.isConsensus(game, "for")).isFalse();
    }

    @Test
    void strikeFromTheFogQualifiesWhenTheDefenderNeverSawTheOrigin() {
        sol.setSecret(FogSecretHooks.STRIKE_FROM_FOG);
        Tile battlefield = game.getTileByPosition("306");
        addShips(hacan, "306", UnitType.Cruiser, 1);
        addShips(sol, "304", UnitType.Cruiser, 2);

        // Hacan's only ship sits in 306, so at activation it sees 305-307 but not 304.
        FogSecretHooks.onSystemActivated(game, sol);
        FogSecretHooks.onUnitsMovedIn(game, sol, battlefield, movedFrom(sol, "304"));
        battlefield.removeAllUnits(hacan.getColor());
        addShips(sol, "306", UnitType.Cruiser, 2);

        assertThat(FogSecretHooks.struckFromTheFog(game, sol, hacan)).isTrue();
        // With its last ship gone, hacan can no longer see the battlefield: Blind Them qualifies too.
        assertThat(FogSight.visible(game, hacan)).doesNotContain("306");
    }

    @Test
    void strikeFromTheFogFailsWhenTheDefenderSawTheApproach() {
        sol.setSecret(FogSecretHooks.STRIKE_FROM_FOG);
        Tile battlefield = game.getTileByPosition("306");
        addShips(hacan, "306", UnitType.Cruiser, 1);
        addShips(sol, "305", UnitType.Cruiser, 2);

        FogSecretHooks.onSystemActivated(game, sol);
        FogSecretHooks.onUnitsMovedIn(game, sol, battlefield, movedFrom(sol, "305"));

        assertThat(FogSecretHooks.struckFromTheFog(game, sol, hacan)).isFalse();
    }

    @Test
    void turnEndClearsTheActivationSnapshot() {
        sol.setSecret(FogSecretHooks.STRIKE_FROM_FOG);
        FogSecretHooks.onSystemActivated(game, sol);
        assertThat(game.getStoredValue(FogSecretHooks.FOUND_KEY + "sol")).isNotEmpty();

        FogSecretHooks.onTurnEnd(game, null);

        assertThat(game.getStoredValue(FogSecretHooks.FOUND_KEY + "sol")).isEmpty();
        assertThat(game.getStoredValue(FogSecretHooks.SIGHT_KEY + "hacan")).isEmpty();
    }

    private Player addPlayer(String userId, String faction, String color, String home) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        player.setHomeSystemPosition(home);
        for (String unit : List.of("cruiser", "destroyer", "carrier", "dreadnought")) {
            player.addOwnedUnitByID(unit);
        }
        return player;
    }

    private static Map<String, Map<UnitKey, List<Integer>>> movedFrom(Player player, String position) {
        return Map.of(position + "-space", Map.of(Units.getUnitKey(UnitType.Cruiser, player.getColorID()), List.of(2)));
    }

    private void addShips(Player player, String position, UnitType type, int count) {
        game.getTileByPosition(position).addUnit("space", Units.getUnitKey(type, player.getColorID()), count);
    }
}
