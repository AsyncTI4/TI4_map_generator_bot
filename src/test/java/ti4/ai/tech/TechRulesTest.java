package ti4.ai.tech;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

class TechRulesTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.place("24", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("meharxull");
        test.aiIsActive("action");
    }

    // Mehar Xull has a technology specialty and was spent this turn. Before ending its turn the seat opens its
    // end-of-turn abilities, exhausts Bio-Stims and readies Mehar Xull rather than a technology.
    @Test
    void readiesASpentTechSkipPlanetWithBioStimsBeforeEndingTheTurn() {
        test.nekro.addTech("bs");
        test.nekro.exhaustPlanet("meharxull");
        AiPrompt end = prompt("end", PromptSource.PUBLIC, NOW, "FFCC_nekro_endOfTurnAbilities");
        AiPrompt abilities =
                prompt("abilities", PromptSource.PUBLIC, NOW, "FFCC_nekro_turnEnd", "FFCC_nekro_exhaustTech_bs");
        AiPrompt ready =
                prompt("ready", PromptSource.PUBLIC, NOW, "biostimsReady_tech_st", "biostimsReady_planet_meharxull");

        assertThat(pressedId(endOfTurn(end).orElseThrow())).isEqualTo("FFCC_nekro_endOfTurnAbilities");
        assertThat(pressedId(endOfTurn(abilities).orElseThrow())).isEqualTo("FFCC_nekro_exhaustTech_bs");
        assertThat(pressedId(endOfTurn(ready).orElseThrow())).isEqualTo("biostimsReady_planet_meharxull");
        assertThat(endOfTurn(abilities)).isEmpty();
    }

    // With nothing worth readying, Bio-Stims stays ready and the turn simply ends.
    @Test
    void leavesBioStimsAloneWithoutASpentTechSkipPlanet() {
        test.nekro.addTech("bs");
        AiPrompt end = prompt("end", PromptSource.PUBLIC, NOW, "FFCC_nekro_endOfTurnAbilities");

        assertThat(endOfTurn(end)).isEmpty();
    }

    // Magen Defense Grid's infantry placement is mandatory when another player activates a system with its
    // structures, so the seat always presses it.
    @Test
    void placesInfantryWithMagenDefenseGrid() {
        AiPrompt magen = prompt("magen", PromptSource.PUBLIC, NOW, "FFCC_nekro_useMagenDefense_301");

        assertThat(pressedId(TechRules.placeMagenInfantry(test.context(magen)).orElseThrow()))
                .isEqualTo("FFCC_nekro_useMagenDefense_301");
    }

    // With nothing to save for, a ready planet with a technology specialty is worth more as a trade good once the
    // seat passes: planets ready again in the status phase anyway.
    @Test
    void tradesTechSkipPlanetsForTradeGoodsWithPsychoarchaeologyBeforePassing() {
        test.nekro.addTech("pa");
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_nekro_getPsychoButtons");
        AiPrompt planets = prompt("planets", PromptSource.PUBLIC, NOW, "psychoExhaust_meharxull", "deleteButtons");

        assertThat(pressedId(TechRules.beforePassing(test.context(start), List.of(start))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_getPsychoButtons");
        assertThat(pressedId(TechRules.beforePassing(test.context(planets), List.of(planets))
                        .orElseThrow()))
                .isEqualTo("psychoExhaust_meharxull");

        test.nekro.exhaustPlanet("meharxull");
        assertThat(TechRules.beforePassing(test.context(planets), List.of(planets)))
                .isEmpty();
    }

    private Optional<AiDecision> endOfTurn(AiPrompt prompt) {
        return TechRules.endOfTurn(test.context(prompt), List.of(prompt));
    }
}
