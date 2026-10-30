package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// A retreat is announced at the start of a combat round and made at its end. The AI announces one when it is
// unlikely to win, still fights that round, then retreats before the next roll to the safest legal system.
class RetreatRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile battle;
    private String position;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        battle = test.place("26", position);
        test.units(battle, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(battle, "space", test.nekro, UnitType.Cruiser, 1);
        test.units(battle, "space", test.sol, UnitType.Dreadnought, 3);
        test.aiIsActive("action");
        test.game.setActivePlayerID(test.sol.getUserID());
        test.game.setActiveSystem(position);
    }

    // A destroyer and a cruiser cannot beat three dreadnoughts. It announces the retreat, fights the round, then
    // retreats home rather than to an empty system.
    @Test
    void retreatsHomeFromAFightItCannotWin() {
        AiPrompt combat = combatButtons();

        assertThat(pressedId(next(combat).orElseThrow())).isEqualTo("announceARetreat");
        assertThat(next(combat)).isEmpty();

        test.game.setStoredValue("combatRoundTrackernekro" + position + "space", "1");
        test.game.setStoredValue("combatRoundTrackersol" + position + "space", "1");
        assertThat(pressedId(next(combat).orElseThrow())).isEqualTo("retreat_" + position);

        AiPrompt destinations = prompt(
                "destinations",
                PromptSource.COMBAT_THREAD,
                NOW + 10,
                "FFCC_nekro_retreatUnitsFrom_" + position + "_" + AiTestGame.HOME,
                "FFCC_nekro_retreatUnitsFrom_" + position + "_000");
        assertThat(pressedId(next(destinations).orElseThrow()))
                .isEqualTo("FFCC_nekro_retreatUnitsFrom_" + position + "_" + AiTestGame.HOME);
    }

    // With the better fleet it stays and fights.
    @Test
    void staysInAFightItShouldWin() {
        test.units(battle, "space", test.nekro, UnitType.Dreadnought, 4);

        assertThat(next(combatButtons())).isEmpty();
    }

    // It never abandons its home system, even in a fight it would retreat from anywhere else.
    @Test
    void neverRetreatsFromHome() {
        assertThat(RetreatRules.shouldRetreat(test.game, test.nekro, battle)).isTrue();

        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "space", test.sol, UnitType.Dreadnought, 3);
        assertThat(RetreatRules.shouldRetreat(test.game, test.nekro, home)).isFalse();
    }

    // An empty system is worth little, so it retreats below 35% odds. Holding Lodor raises the stake, and an unspent
    // Lodor more than a spent one, so it sticks it out longer before retreating (never below 20%).
    @Test
    void sticksOutLongerForASystemThatMatters() {
        assertThat(RetreatRules.retreatBelow(test.game, test.nekro, battle))
                .isEqualTo(RetreatRules.SPARE_SYSTEM_RETREAT);

        test.nekro.addPlanet("lodor");
        test.nekro.exhaustPlanet("lodor");
        double spent = RetreatRules.retreatBelow(test.game, test.nekro, battle);
        test.nekro.refreshPlanet("lodor");
        double ready = RetreatRules.retreatBelow(test.game, test.nekro, battle);

        assertThat(spent).isLessThan(RetreatRules.SPARE_SYSTEM_RETREAT);
        assertThat(ready).isLessThan(spent).isGreaterThanOrEqualTo(RetreatRules.CRITICAL_SYSTEM_RETREAT);
    }

    // Structures count too: a space dock on Lodor makes the system worth holding even longer.
    @Test
    void sticksOutLongerToProtectAStructure() {
        test.nekro.addPlanet("lodor");
        test.nekro.exhaustPlanet("lodor");
        double bare = RetreatRules.retreatBelow(test.game, test.nekro, battle);
        test.units(battle, "lodor", test.nekro, UnitType.Spacedock, 1);

        assertThat(RetreatRules.retreatBelow(test.game, test.nekro, battle))
                .isLessThan(bare)
                .isGreaterThanOrEqualTo(RetreatRules.CRITICAL_SYSTEM_RETREAT);
    }

    // After the fleet retreats home, Sol's four infantry in orbit will invade Lodor. A lone infantry cannot hold it,
    // so the carrier that retreated takes it along; three infantry against one invader stay to hold the planet.
    @Test
    void carriesAwayAGarrisonThatWouldFall() {
        test.nekro.addPlanet("lodor");
        test.nekro.exhaustPlanet("lodor");
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Carrier, 1);
        test.units(battle, "space", test.sol, UnitType.Infantry, 4);
        test.units(battle, "lodor", test.nekro, UnitType.Infantry, 1);

        assertThat(pressedId(next(groundChoice(1)).orElseThrow()))
                .isEqualTo("FFCC_nekro_retreatGroundUnits_" + position + "_" + AiTestGame.HOME + "_1_infantry_lodor");

        test.units(battle, "lodor", test.nekro, UnitType.Infantry, 2);
        battle.removeUnit("space", Units.getUnitKey(UnitType.Infantry, "blue"), 3);
        assertThat(pressedId(next(groundChoice(2)).orElseThrow())).isEqualTo("FFCC_nekro_deleteButtons");
    }

    private AiPrompt groundChoice(int most) {
        List<String> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int count = 1; count <= most; count++) {
            ids.add("FFCC_nekro_retreatGroundUnits_" + position + "_" + AiTestGame.HOME + "_" + count
                    + "_infantry_lodor");
            labels.add("Retreat " + count + " Infantry on Lodor");
        }
        ids.add("FFCC_nekro_deleteButtons");
        labels.add("Done Retreating troops");
        return prompt("ground", PromptSource.COMBAT_THREAD, NOW + 20, ids, labels);
    }

    private AiPrompt combatButtons() {
        return prompt(
                "combat",
                PromptSource.COMBAT_THREAD,
                NOW,
                "combatRoll_" + position + "_space",
                "announceARetreat",
                "retreat_" + position);
    }

    private Optional<AiDecision> next(AiPrompt prompt) {
        return RetreatRules.next(test.context(prompt), Prompts.newestFirst(List.of(prompt)));
    }
}
