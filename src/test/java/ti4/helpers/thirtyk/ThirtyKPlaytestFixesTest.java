package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.handlers.actioncards.thirtyk.ThirtyKBoardAcButtonHandler;
import ti4.discord.interactions.buttons.handlers.actioncards.thirtyk.ThirtyKEconomyAcButtonHandler;
import ti4.discord.interactions.buttons.handlers.actioncards.thirtyk.ThirtyKPoliticsAcButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.NamedCombatModifierModel;
import ti4.service.combat.CombatRollType;
import ti4.testUtils.BaseTi4Test;

// Regression cases for the 30k/JMSC cards that failed manual playtesting.
class ThirtyKPlaytestFixesTest extends BaseTi4Test {

    private Game game;
    private Player sol;
    private Player xxcha;
    private Player hacan;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("thirtyk-playtest-fixes");
        game.setPhaseOfGame("action");
        game.setAcDeckID("action_cards_30k");
        game.setTile(new Tile("01", "000"));
        game.setTile(new Tile("19", "101"));
        game.setTile(new Tile("25", "501"));
        // Join order puts the coexisting xxcha before the planet's owner, hacan.
        sol = addPlayer("sol-id", "sol", "blue");
        xxcha = addPlayer("xxcha-id", "xxcha", "red");
        hacan = addPlayer("hacan-id", "hacan", "green");
        sol.setHomeSystemPosition("000");
        sol.addPlanet("jord");
    }

    @Test
    void corruptManifestationFightsThePlanetOwnerBeforeCoexistingPlayers() {
        hacan.addPlanet("wellon");
        addUnit(hacan, "wellon", UnitType.Infantry, 1);
        addUnit(xxcha, "wellon", UnitType.Infantry, 1);
        addUnit(sol, "wellon", UnitType.Infantry, 1);

        assertThat(ThirtyKBoardAcButtonHandler.corruptDefenders(game, sol, planet("wellon")))
                .extracting(Player::getFaction)
                .containsExactly("hacan", "xxcha", "sol");
    }

    // The neutral infantry are hostile to everyone, including the player who summoned them.
    @Test
    void corruptManifestationFightsTheCardPlayerOnTheirOwnPlanet() {
        sol.addPlanet("wellon");
        addUnit(sol, "wellon", UnitType.Infantry, 2);

        assertThat(ThirtyKBoardAcButtonHandler.corruptDefenders(game, sol, planet("wellon")))
                .extracting(Player::getFaction)
                .containsExactly("sol");
    }

    @Test
    void corruptManifestationIgnoresPlayersWithOnlyStructures() {
        hacan.addPlanet("wellon");
        addUnit(hacan, "wellon", UnitType.Pds, 1);

        assertThat(ThirtyKBoardAcButtonHandler.corruptDefenders(game, sol, planet("wellon")))
                .isEmpty();
    }

    @Test
    void warProfiteeringOffersTheCardPlayerAndTheNeutral() {
        Player neutral = game.getNeutral();
        addUnit(sol, "space", "101", UnitType.Cruiser);
        addUnit(neutral, "space", "101", UnitType.Destroyer);
        game.setActiveSystem("101");

        assertThat(customIds(ThirtyKEconomyAcButtonHandler.warProfiteeringButtons(game, sol)))
                .anyMatch(id -> id.contains(sol.getColor() + "_101"))
                .anyMatch(id -> id.contains(neutral.getColor() + "_101"));
    }

    // Combat started with /combat leaves no active system; the only contested system is used instead.
    @Test
    void warProfiteeringFindsTheBattleWithoutAnActiveSystem() {
        addUnit(sol, "space", "101", UnitType.Cruiser);
        addUnit(hacan, "space", "101", UnitType.Destroyer);

        assertThat(customIds(ThirtyKEconomyAcButtonHandler.warProfiteeringButtons(game, sol)))
                .hasSize(2)
                .allMatch(id -> id.endsWith("_101"));
    }

    @Test
    void finalStandAddsDiceOnlyWithExactlyOneInfantryAndNoOtherGroundForces() {
        Planet wellon = planet("wellon");
        sol.addPlanet("wellon");
        addUnit(sol, "wellon", UnitType.Infantry, 1);
        ThirtyKAcState.setFinalStand(game, sol, "wellon");

        List<NamedCombatModifierModel> rolls =
                ThirtyKActionCardHooks.finalStandExtraRolls(game, sol, wellon, CombatRollType.combatround);
        assertThat(rolls).hasSize(1);
        assertThat(rolls.getFirst().getModifier().getValue()).isEqualTo(wellon.getInfluence());

        addUnit(sol, "wellon", UnitType.Mech, 1);
        assertThat(ThirtyKActionCardHooks.finalStandExtraRolls(game, sol, wellon, CombatRollType.combatround))
                .isEmpty();
    }

    // Final Stand lasts for one ground combat; a new combat on the same planet starts without it.
    @Test
    void finalStandEndsWhenTheNextGroundCombatStarts() {
        Planet wellon = planet("wellon");
        sol.addPlanet("wellon");
        addUnit(sol, "wellon", UnitType.Infantry, 1);
        ThirtyKAcState.setFinalStand(game, sol, "wellon");

        ThirtyKActionCardHooks.onGroundCombatStart(game, wellon);

        assertThat(ThirtyKActionCardHooks.finalStandExtraRolls(game, sol, wellon, CombatRollType.combatround))
                .isEmpty();
    }

    // Non-30k games and planets the card was not played on never get extra dice.
    @Test
    void finalStandStaysOutOfOtherGamesAndPlanets() {
        Planet wellon = planet("wellon");
        sol.addPlanet("wellon");
        addUnit(sol, "wellon", UnitType.Infantry, 1);

        assertThat(ThirtyKActionCardHooks.finalStandExtraRolls(game, sol, wellon, CombatRollType.combatround))
                .isEmpty();

        Game plainGame = new Game();
        assertThat(ThirtyKActionCardHooks.finalStandExtraRolls(plainGame, sol, wellon, CombatRollType.combatround))
                .isEmpty();
        assertThat(plainGame.getStoredValueMap()).isEmpty();
    }

    @Test
    void cumulativeSupportSkipsOccupiedAndFarPlanets() {
        assertThat(ThirtyKBoardAcButtonHandler.cumulativeInfantry(game, sol, "wellon"))
                .isEqualTo(1);
        assertThat(ThirtyKBoardAcButtonHandler.cumulativeInfantry(game, sol, "quann"))
                .isZero();

        addUnit(hacan, "wellon", UnitType.Infantry, 1);
        assertThat(ThirtyKBoardAcButtonHandler.cumulativeInfantry(game, sol, "wellon"))
                .isZero();
    }

    @Test
    void devisivePlotCountsTokensInReinforcements() {
        sol.setTacticalCC(3);
        sol.setFleetCC(3);
        sol.setStrategicCC(2);
        assertThat(ThirtyKPoliticsAcButtonHandler.tokensInReinforcements(game, sol))
                .isEqualTo(sol.getCommandTokenLimit() - 8);

        sol.setTacticalCC(sol.getCommandTokenLimit() - 5);
        assertThat(ThirtyKPoliticsAcButtonHandler.tokensInReinforcements(game, sol))
                .isZero();
    }

    private Player addPlayer(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        player.setUnitsOwned(new HashSet<>(player.getFactionSetupInfo().getUnits()));
        return player;
    }

    private void addUnit(Player player, String planetName, UnitType type, int count) {
        game.getTileFromPlanet(planetName).addUnit(planetName, Units.getUnitKey(type, player.getColor()), count);
    }

    private void addUnit(Player player, String holder, String position, UnitType type) {
        game.getTileByPosition(position).addUnit(holder, Units.getUnitKey(type, player.getColor()), 1);
    }

    private Planet planet(String planetName) {
        return game.getUnitHolderFromPlanet(planetName);
    }

    private static List<String> customIds(List<Button> buttons) {
        return buttons.stream().map(Button::getCustomId).toList();
    }
}
