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
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Action cards for the start of a combat round are offered through a hook that CombatRules calls at the moment the
// seat is about to roll, so the card is always played before the seat's own first roll of the round.
class CombatCardHookTest extends BaseTi4Test {

    private static final long AFTER_GRACE = NOW + CombatRules.ROLL_GRACE_MILLIS;

    private AiTestGame test;
    private String position;
    private final List<CombatMoment> offered = new ArrayList<>();
    private final CombatCardHook spy = (context, moment) -> {
        offered.add(moment);
        return Optional.of(new AiDecision.Wait(0, "a card"));
    };

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        Tile tile = test.place("26", position);
        test.units(tile, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(tile, "space", test.sol, UnitType.Cruiser, 1);
        test.aiIsActive("action");
        test.game.setActiveSystem(position);
    }

    private AiPrompt roll(String holder) {
        return prompt("roll-" + holder, PromptSource.COMBAT_THREAD, NOW, "combatRoll_" + position + "_" + holder);
    }

    @Test
    void isOfferedTheMomentWhenLevelAndTheOpponentHadItsPause() {
        AiDecision decision = CombatRules.next(test.contextAt(AFTER_GRACE, roll("space")), spy)
                .orElseThrow();

        assertThat(decision).isInstanceOf(AiDecision.Wait.class);
        assertThat(offered).containsExactly(new CombatMoment(position, "space", false, 0));
    }

    @Test
    void isNotOfferedWhileTheOpponentIsStillGivenItsPause() {
        assertThat(CombatRules.next(test.context(roll("space")), spy).orElseThrow())
                .isInstanceOf(AiDecision.Wait.class);

        assertThat(offered).isEmpty();
    }

    // Once the opponent has rolled, the round has started and its dice are known: too late for a start-of-round card.
    @Test
    void isNotOfferedOnceTheOpponentHasRolled() {
        test.game.setStoredValue("combatRoundTrackersol" + position + "space", "1");

        assertThat(pressedId(CombatRules.next(test.context(roll("space")), spy).orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");
        assertThat(offered).isEmpty();
    }

    @Test
    void carriesTheRoundNumberOnceBothSidesHaveRolled() {
        test.game.setStoredValue("combatRoundTrackersol" + position + "space", "2");
        test.game.setStoredValue("combatRoundTrackernekro" + position + "space", "2");

        CombatRules.next(test.contextAt(AFTER_GRACE, roll("space")), spy);

        assertThat(offered).containsExactly(new CombatMoment(position, "space", false, 2));
    }

    @Test
    void isNotOfferedForACombatOutsideTheActiveSystem() {
        test.game.setActiveSystem(AiTestGame.HOME);

        assertThat(pressedId(CombatRules.next(test.contextAt(AFTER_GRACE, roll("space")), spy)
                        .orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space");
        assertThat(offered).isEmpty();
    }

    // Anti-fighter barrage is a roll of its own: a Morale Boost played before it would be spent on it.
    @Test
    void waitsForTheSeatsOwnAntiFighterBarrage() {
        Tile tile = test.game.getTileByPosition(position);
        test.units(tile, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(tile, "space", test.sol, UnitType.Fighter, 2);
        AiPrompt afb = prompt(
                "afb",
                PromptSource.COMBAT_THREAD,
                NOW,
                "combatRoll_" + position + "_space_afb",
                "combatRoll_" + position + "_space");

        assertThat(pressedId(
                        CombatRules.next(test.contextAt(AFTER_GRACE, afb), spy).orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space_afb");
        assertThat(offered).isEmpty();
    }

    @Test
    void waitsForTheSeatsOwnSpaceCannon() {
        Tile tile = test.game.getTileByPosition(position);
        test.nekro.addPlanet("lodor");
        test.units(tile, "lodor", test.nekro, UnitType.Pds, 1);
        AiPrompt cannon = prompt(
                "cannon",
                PromptSource.PUBLIC,
                NOW,
                "combatRoll_" + position + "_space_spacecannonoffence",
                "combatRoll_" + position + "_space");

        assertThat(pressedId(CombatRules.next(test.contextAt(AFTER_GRACE, cannon), spy)
                        .orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_space_spacecannonoffence");
        assertThat(offered).isEmpty();
    }

    // The automation offer is only a button on the table: nobody has accepted it, so the round still has its window.
    @Test
    void isOfferedWhileTheGroundCombatOnlyHasAnUnacceptedAutomationOffer() {
        Tile tile = test.game.getTileByPosition(position);
        test.units(tile, "lodor", test.nekro, UnitType.Infantry, 2);
        test.units(tile, "lodor", test.sol, UnitType.Infantry, 1);
        AiPrompt automate = prompt(
                "automate",
                PromptSource.COMBAT_THREAD,
                NOW,
                "automateGroundCombat_nekro_sol_lodor_unconfirmed",
                "combatRoll_" + position + "_lodor");

        CombatRules.next(test.contextAt(AFTER_GRACE, automate), spy);

        assertThat(offered).containsExactly(new CombatMoment(position, "lodor", true, 0));
    }

    // Once the opponent has asked for the automation, the whole combat runs without a card window.
    @Test
    void isNotOfferedOnceTheOpponentHasAskedForTheAutomation() {
        Tile tile = test.game.getTileByPosition(position);
        test.units(tile, "lodor", test.nekro, UnitType.Infantry, 2);
        test.units(tile, "lodor", test.sol, UnitType.Infantry, 1);
        AiPrompt asked = prompt(
                "asked",
                PromptSource.COMBAT_THREAD,
                NOW,
                "FFCC_sol_automateGroundCombat_nekro_sol_lodor_confirmed",
                "combatRoll_" + position + "_lodor");

        assertThat(pressedId(CombatRules.next(test.contextAt(AFTER_GRACE, asked), spy)
                        .orElseThrow()))
                .isEqualTo("combatRoll_" + position + "_lodor");
        assertThat(offered).isEmpty();
    }
}
