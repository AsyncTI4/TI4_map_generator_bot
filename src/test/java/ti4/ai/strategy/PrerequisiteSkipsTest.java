package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

// Researching with a technology specialty means exhausting that planet, so its resources or influence are lost
// for the round (unless the seat owns Psychoarchaeology).
class PrerequisiteSkipsTest extends BaseTi4Test {

    private AiTestGame test;
    private Player sol;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        sol = test.sol;
        test.place("22", "101");
        test.place("37", "102");
        test.place("24", "103");
    }

    // Hyper Metabolism needs two green technologies and Sol owns one. Tarmann's biotic specialty covers the other at
    // the cost of its 1 resource.
    @Test
    void exhaustsAMatchingSpecialtyPlanet() {
        sol.addPlanet("tarmann");

        PrerequisiteSkips.Plan plan = PrerequisiteSkips.of(test.game, sol, Mapper.getTech("hm"));

        assertThat(plan.planets()).containsExactly("tarmann");
        assertThat(plan.aiDevelopment()).isFalse();
        assertThat(plan.resourcesLost()).isEqualTo(1);
    }

    @Test
    void psychoarchaeologyMakesSpecialtiesFree() {
        sol.addPlanet("tarmann");
        sol.addTech("pa");

        PrerequisiteSkips.Plan plan = PrerequisiteSkips.of(test.game, sol, Mapper.getTech("hm"));

        assertThat(plan.planets()).isEmpty();
        assertThat(plan.valueLost()).isZero();
    }

    // Magen Defense Grid needs a red technology. Meer could stand in, but exhausting it gives up 4 influence, more than
    // the technology is worth to Sol, so even Dacxive Animators (a stepping stone towards Hyper Metabolism) is
    // researched instead. With Psychoarchaeology the specialty is free and Magen Defense Grid wins.
    @Test
    void passesOnASkipThatCostsTooMuch() {
        sol.addPlanet("meer");

        assertThat(ResearchPolicy.best(test.game, sol, List.of("md", "dxa"))).contains("dxa");

        sol.addTech("pa");
        assertThat(ResearchPolicy.best(test.game, sol, List.of("md", "dxa"))).contains("md");
    }

    // Destroyer II needs two red technologies. AI Development Algorithm is one; Mehar Xull (1/3) could cover the
    // other, but with fewer than two unit upgrades exhausting AI Development Algorithm gives up no production
    // discount, so it stands in and Mehar Xull stays ready.
    @Test
    void aiDevelopmentStandsInForASpecialtyPlanet() {
        sol.addPlanet("meharxull");
        sol.addTech("aida");

        PrerequisiteSkips.Plan plan = PrerequisiteSkips.of(test.game, sol, Mapper.getTech("dd2"));

        assertThat(plan.planets()).isEmpty();
        assertThat(plan.aiDevelopment()).isTrue();
        assertThat(plan.resourcesLost()).isZero();

        sol.exhaustTech("aida");
        PrerequisiteSkips.Plan withoutAida = PrerequisiteSkips.of(test.game, sol, Mapper.getTech("dd2"));
        assertThat(withoutAida.planets()).containsExactly("meharxull");
        assertThat(withoutAida.resourcesLost()).isEqualTo(3);
    }
}
