package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.ResearchPolicy;
import ti4.ai.tactical.TransitDiodesRules.Transfer;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Transit Diodes move up to 4 ground forces onto planets the seat controls, without carriers: the AI uses them for
// Mechanize the Military (a mech on each of 4 planets) and Occupy the Fringe (9 ground forces on a planet without a
// dock).
class TransitDiodesRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private Tile abyzFria;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
        home = test.nekroHome();
        abyzFria = test.place("38", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("abyz");
        test.nekro.addPlanet("fria");
    }

    // Three mechs on Mordai II: two are spare, and they go to Abyz and Fria. The last one stays.
    @Test
    void spreadsSpareMechsForMechanizeTheMilitary() {
        test.nekro.setSecret(TransitDiodesRules.MECHANIZE_THE_MILITARY);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 3);

        List<Transfer> plan = TransitDiodesRules.plan(test.game, test.nekro);

        assertThat(plan).extracting(Transfer::unit).containsOnly("mech");
        assertThat(plan).extracting(Transfer::planet).containsExactlyInAnyOrder("abyz", "fria");
        assertThat(plan).extracting(Transfer::holder).containsOnly("mordaiii");
    }

    // Abyz holds 5 infantry and has no dock; 4 more from Mordai II make 9.
    @Test
    void stacksInfantryForOccupyTheFringe() {
        test.nekro.setSecret(TransitDiodesRules.OCCUPY_THE_FRINGE);
        test.units(abyzFria, "abyz", test.nekro, UnitType.Infantry, 5);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 6);

        List<Transfer> plan = TransitDiodesRules.plan(test.game, test.nekro);

        assertThat(plan).hasSize(4).extracting(Transfer::planet).containsOnly("abyz");
        assertThat(plan.getFirst().handler()).isEqualTo("mercerMove_abyz_" + AiTestGame.HOME + "_mordaiii_infantry");
    }

    // Nothing worth moving: one mech per planet already, so no transfer.
    @Test
    void neverMovesAPlanetsOnlyMech() {
        test.nekro.setSecret(TransitDiodesRules.MECHANIZE_THE_MILITARY);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 1);

        assertThat(TransitDiodesRules.plan(test.game, test.nekro)).isEmpty();
    }

    // With two yellow technologies, Transit Diodes is worth more while a ground secret is held. Without them the
    // secret adds nothing: the AI does not research extra yellow technology to reach it.
    @Test
    void valuesTransitDiodesOnlyWhenItCanBeResearchedNow() {
        double before = ResearchPolicy.value(test.game, test.nekro, "td");
        test.nekro.setSecret(TransitDiodesRules.OCCUPY_THE_FRINGE);
        assertThat(ResearchPolicy.value(test.game, test.nekro, "td")).isEqualTo(before);

        test.nekro.addTech("st");
        test.nekro.addTech("gls");
        double researchable = ResearchPolicy.value(test.game, test.nekro, "td");
        test.nekro.removeSecret(test.nekro.getSecretsUnscored().get(TransitDiodesRules.OCCUPY_THE_FRINGE));

        assertThat(researchable).isGreaterThan(ResearchPolicy.value(test.game, test.nekro, "td") + 2);
    }

    // The start-of-turn button exhausts the card; then it picks Abyz, moves a mech there, picks Fria, moves the other
    // and closes the prompt.
    @Test
    void pressesThroughTheTransitDiodesButtons() {
        test.aiIsActive("action");
        test.nekro.addTech("td");
        test.nekro.setSecret(TransitDiodesRules.MECHANIZE_THE_MILITARY);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 3);
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_exhaustTech_td", "FFCC_nekro_tacticalAction");
        assertThat(pressedId(TransitDiodesRules.start(test.context(turn), List.of(turn))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_exhaustTech_td");
        List<Transfer> plan = TransitDiodesRules.plan(test.game, test.nekro);
        String first = plan.get(0).planet();
        String second = plan.get(1).planet();

        AiPrompt planets = prompt(
                "planets", PromptSource.PUBLIC, NOW + 1, "transitDiodes_abyz", "transitDiodes_fria", "deleteButtons");
        assertThat(pressedId(TransitDiodesRules.next(test.context(planets)).orElseThrow()))
                .isEqualTo("transitDiodes_" + first);
        AiPrompt moves =
                prompt("moves", PromptSource.PUBLIC, NOW + 2, plan.get(0).handler());
        assertThat(pressedId(
                        TransitDiodesRules.next(test.context(planets, moves)).orElseThrow()))
                .isEqualTo(plan.get(0).handler());
        assertThat(pressedId(TransitDiodesRules.next(test.context(planets)).orElseThrow()))
                .isEqualTo("transitDiodes_" + second);
        AiPrompt moreMoves =
                prompt("moves2", PromptSource.PUBLIC, NOW + 3, plan.get(1).handler());
        assertThat(pressedId(TransitDiodesRules.next(test.context(planets, moreMoves))
                        .orElseThrow()))
                .isEqualTo(plan.get(1).handler());
        assertThat(pressedId(TransitDiodesRules.next(test.context(planets)).orElseThrow()))
                .isEqualTo("deleteButtons");
        assertThat(TransitDiodesRules.start(test.context(turn), List.of(turn))).isEmpty();
    }
}
