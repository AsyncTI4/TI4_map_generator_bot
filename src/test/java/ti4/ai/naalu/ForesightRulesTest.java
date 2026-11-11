package ti4.ai.naalu;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Foresight: Sol moves its fleet into Lodor, beside the seat's home system, where the seat (the test game's "nekro"
// seat, given the ability) has ships. Before the first roll the seat may spend a strategy token to slip its ships
// into an adjacent system with no other player's ships, here its own home system.
class ForesightRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile lodor;
    private String position;
    private String foresight;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        test.nekroHome();
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        lodor = test.place("26", position);
        test.nekro.addAbility(ForesightRules.ABILITY);
        test.nekro.setStrategicCC(3);
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(5);
        test.isActive(test.sol, "action");
        test.game.setActiveSystem(position);
        foresight = "FFCC_nekro_retreat_" + position + "_foresight";
    }

    private AiPrompt combatStart() {
        return prompt("start", PromptSource.COMBAT_THREAD, NOW, foresight, "combatRoll_" + position + "_space");
    }

    private Optional<AiDecision> next(AiPrompt... prompts) {
        return ForesightRules.next(test.context(prompts));
    }

    private String pressed(AiPrompt... prompts) {
        return next(prompts).map(AiTestGame::pressedId).orElse("");
    }

    private void losingFight() {
        test.units(lodor, "space", test.nekro, UnitType.Cruiser, 2);
        test.units(lodor, "space", test.nekro, UnitType.Destroyer, 2);
        test.units(lodor, "space", test.sol, UnitType.Dreadnought, 5);
    }

    @Test
    void escapesAFightItWouldLose() {
        losingFight();

        assertThat(pressed(combatStart())).isEqualTo(foresight);
    }

    @Test
    void staysInAFightItWouldWin() {
        test.units(lodor, "space", test.nekro, UnitType.Dreadnought, 4);
        test.units(lodor, "space", test.sol, UnitType.Destroyer, 1);

        assertThat(pressed(combatStart())).isEmpty();
    }

    // The decision is taken once, before the first roll: a declined Foresight is never reopened.
    @Test
    void decidesOnlyOnce() {
        test.units(lodor, "space", test.nekro, UnitType.Dreadnought, 4);
        test.units(lodor, "space", test.sol, UnitType.Destroyer, 1);
        assertThat(pressed(combatStart())).isEmpty();
        test.units(lodor, "space", test.sol, UnitType.Dreadnought, 6);

        assertThat(pressed(combatStart())).isEmpty();
    }

    // A declined Foresight against Sol's small fleet says nothing about a later attacker: when Letnev moves in with a
    // fleet the seat cannot beat in the same round, the seat decides afresh and escapes.
    @Test
    void decidesAgainForADifferentAttackerInTheSameRound() {
        test.units(lodor, "space", test.nekro, UnitType.Cruiser, 2);
        test.units(lodor, "space", test.sol, UnitType.Destroyer, 1);
        assertThat(pressed(combatStart())).isEmpty();
        test.game.getTileByPosition(position).removeAllUnits(test.sol.getColor());
        ti4.game.Player letnev = test.addSeat("200000000000000002", "letnev", "red");
        test.isActive(letnev, "action");
        test.units(lodor, "space", letnev, UnitType.Dreadnought, 5);

        assertThat(pressed(combatStart())).isEqualTo(foresight);
    }

    @Test
    void notOnceTheCombatHasStarted() {
        losingFight();
        test.game.setStoredValue("combatRoundTrackernekro" + position + "space", "1");

        assertThat(pressed(combatStart())).isEmpty();
    }

    // Foresight answers another player's movement; the seat never uses it in its own tactical action.
    @Test
    void notAsTheActivePlayer() {
        losingFight();
        test.aiIsActive("action");

        assertThat(pressed(combatStart())).isEmpty();
    }

    @Test
    void needsAStrategyToken() {
        losingFight();
        test.nekro.setStrategicCC(0);

        assertThat(pressed(combatStart())).isEmpty();
    }

    @Test
    void needsTheAbility() {
        losingFight();
        test.nekro.getAbilities().remove(ForesightRules.ABILITY);

        assertThat(pressed(combatStart())).isEmpty();
    }

    @Test
    void notWithoutASafeSystemToMoveTo() {
        losingFight();
        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        test.units(home, "space", test.sol, UnitType.Destroyer, 1);
        for (String neighbour : MapPositionMapper.getAdjacentTilePositions(position)) {
            Tile tile = test.game.getTileByPosition(neighbour);
            if (tile != null && tile != home) test.units(tile, "space", test.sol, UnitType.Destroyer, 1);
        }

        assertThat(pressed(combatStart())).isEmpty();
    }

    // Once Foresight is pressed, the bot asks where to go; the seat picks its own home system.
    @Test
    void movesToTheSafestAdjacentSystem() {
        losingFight();
        assertThat(pressed(combatStart())).isEqualTo(foresight);
        String other = test.place("27", otherNeighbour()).getPosition();
        String toHome = "FFCC_nekro_retreatUnitsFrom_" + position + "_" + AiTestGame.HOME + "_skilled";
        String toOther = "FFCC_nekro_retreatUnitsFrom_" + position + "_" + other + "_skilled";
        AiPrompt destinations = prompt("where", PromptSource.COMBAT_THREAD, NOW, toOther, toHome);

        assertThat(pressed(destinations)).isEqualTo(toHome);
    }

    // A Skilled Retreat offers the same buttons; without a Foresight of its own the rule leaves them alone.
    @Test
    void leavesOtherRetreatsToTheirOwnRules() {
        losingFight();
        String toHome = "FFCC_nekro_retreatUnitsFrom_" + position + "_" + AiTestGame.HOME + "_skilled";

        assertThat(pressed(prompt("where", PromptSource.COMBAT_THREAD, NOW, toHome)))
                .isEmpty();
    }

    @Test
    void escapeCompareSavedShipsWithWhatItCosts() {
        assertThat(new ForesightRules.Escape(6, 0, 0.75, 0).worthIt(0.1)).isTrue();
        assertThat(new ForesightRules.Escape(1, 0, 0.75, 1).worthIt(0.1)).isFalse();
        assertThat(new ForesightRules.Escape(6, 0, 0.75, 0).worthIt(0.6)).isFalse();
        assertThat(new ForesightRules.Escape(6, 5.5, 0.75, 0).worthIt(0.4)).isFalse();
    }

    private String otherNeighbour() {
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate))
                .filter(candidate -> test.game.getTileByPosition(candidate) == null)
                .findFirst()
                .orElseThrow();
    }
}
