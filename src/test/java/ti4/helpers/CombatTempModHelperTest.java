package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.combat.CombatRollType;
import ti4.testUtils.BaseTi4Test;

// Bunker is played by the defender at the start of the invasion, so its -4 has to reach the attacker's bombardment
// before the defender has rolled anything themselves.
class CombatTempModHelperTest extends BaseTi4Test {

    private Player defender;
    private Tile tile;
    private UnitHolder planet;

    @BeforeEach
    void setUp() {
        Game game = new Game();
        defender = game.addPlayer("defender-id", "defender");
        defender.setFaction("xxcha");
        defender.setColor("green");
        tile = new Tile("26", "201");
        planet = tile.getUnitHolderFromPlanet("lodor");
    }

    private void queue(String type, String id) {
        TemporaryCombatModifierModel mod =
                CombatTempModHelper.getPossibleTempModifier(type, id, defender.getNumberOfTurns());
        assertThat(mod).isNotNull();
        defender.addNewTempCombatMod(mod);
    }

    @Test
    void queuedBunkerAppliesToTheAttackersBombardment() {
        queue(Constants.AC, "bunker");

        CombatTempModHelper.activateQueuedOpponentMods(defender, tile.getTileModel(), planet);

        assertThat(defender.getNewTempCombatModifiers()).isEmpty();
        assertThat(CombatTempModHelper.buildCurrentRoundTempNamedModifiers(
                        defender, tile.getTileModel(), planet, true, CombatRollType.bombardment))
                .hasSize(1);
    }

    @Test
    void bunkerStaysActiveForEveryPlanetOfTheTacticalAction() {
        queue(Constants.AC, "bunker");
        CombatTempModHelper.activateQueuedOpponentMods(defender, tile.getTileModel(), planet);

        UnitHolder otherPlanet = tile.getUnitHolderFromPlanet("quann");
        if (otherPlanet == null) otherPlanet = planet;
        assertThat(CombatTempModHelper.buildCurrentRoundTempNamedModifiers(
                        defender, tile.getTileModel(), otherPlanet, true, CombatRollType.bombardment))
                .hasSize(1);
    }

    @Test
    void modsQueuedForTheOwnersOwnRollsAreLeftQueued() {
        queue("leader", "vaylerianhero");

        CombatTempModHelper.activateQueuedOpponentMods(defender, tile.getTileModel(), planet);

        assertThat(defender.getNewTempCombatModifiers()).hasSize(1);
        assertThat(defender.getTempCombatModifiers()).isEmpty();
    }

    @Test
    void modsQueuedInAnEarlierTurnAreNotActivated() {
        TemporaryCombatModifierModel stale =
                CombatTempModHelper.getPossibleTempModifier(Constants.AC, "bunker", defender.getNumberOfTurns() + 1);
        defender.addNewTempCombatMod(stale);

        CombatTempModHelper.activateQueuedOpponentMods(defender, tile.getTileModel(), planet);

        assertThat(defender.getTempCombatModifiers()).isEmpty();
    }
}
