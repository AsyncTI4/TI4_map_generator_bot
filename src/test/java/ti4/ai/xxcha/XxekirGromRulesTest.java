package ti4.ai.xxcha;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Xxekir Grom (Thunder's Edge) places up to 4 PDS or mechs on the seat's planets and readies each one. It had no rule,
// so the AI never played it. Xxcha holds its home (Archon Ren 2/3, Archon Tau 1/1), Fria (2/0) and Abyz (3/0), with
// Abyz and Archon Ren exhausted. Like Diplomacy, it is played before passing only when the planets it readies pay for a
// public objective in the status phase: Amass Wealth (3 influence, 3 resources, 3 trade goods) needs Archon Ren ready.
class XxekirGromRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        xxcha = test.addSeat(AiTestGame.SECOND_AI_ID, "xxcha", "green");
        test.isActive(xxcha, "action");
        test.place("14", "301");
        test.place("38", "201");
        for (String planet : List.of("archonren", "archontau", "fria", "abyz")) xxcha.addPlanet(planet);
        xxcha.exhaustPlanet("abyz");
        xxcha.exhaustPlanet("archonren");
        xxcha.addLeader(XxekirGromRules.HERO);
        xxcha.unsafeGetLeader(XxekirGromRules.HERO).setLocked(false);
    }

    private void amassWealthNeedsArchonRen() {
        test.game.getRevealedPublicObjectives().put("amass_wealth", 1);
        xxcha.setTg(3);
    }

    private AiTurnContext context(long now, AiPrompt... prompts) {
        return test.contextFor(xxcha, Set.of(), now, prompts);
    }

    private static AiPrompt turnMenu() {
        return prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_xxcha_componentAction", "FFCC_xxcha_passForRound");
    }

    @Test
    void playsTheHeroBeforePassingWhenTheReadiedPlanetsPayForAPublic() {
        amassWealthNeedsArchonRen();
        AiPrompt turn = turnMenu();

        Optional<AiDecision> decision = XxekirGromRules.beforePassing(context(NOW, turn), List.of(turn));

        assertThat(decision.map(AiTestGame::pressedId)).contains("FFCC_xxcha_componentAction");
    }

    @Test
    void keepsTheHeroWhenPassingWithNothingToSpendTheReadiedPlanetsOn() {
        AiPrompt turn = turnMenu();

        assertThat(XxekirGromRules.beforePassing(context(NOW, turn), List.of(turn)))
                .isEmpty();
    }

    @Test
    void needsTheHeroUnlocked() {
        amassWealthNeedsArchonRen();
        xxcha.unsafeGetLeader(XxekirGromRules.HERO).setLocked(true);
        AiPrompt turn = turnMenu();

        assertThat(XxekirGromRules.beforePassing(context(NOW, turn), List.of(turn)))
                .isEmpty();
    }

    @Test
    void readiesTheExhaustedPlanetsFirst() {
        String planet = XxekirGromRules.bestPlanet(
                test.game, xxcha, XxekirGromRules.MECH, List.of("archontau", "fria", "abyz"));

        assertThat(planet).isEqualTo("abyz");
    }

    @Test
    void walksTheHeroThenEveryPlacement() {
        amassWealthNeedsArchonRen();
        AiPrompt turn = turnMenu();
        XxekirGromRules.beforePassing(context(NOW, turn), List.of(turn));

        AiPrompt menu =
                prompt("menu", PromptSource.AI_THREAD, NOW + 1000, "FFCC_xxcha_componentActionRes_leader_xxchahero-te");
        assertThat(XxekirGromRules.next(context(NOW + 1000, menu)).map(AiTestGame::pressedId))
                .contains("FFCC_xxcha_componentActionRes_leader_xxchahero-te");

        AiPrompt units = prompt(
                "units",
                PromptSource.PUBLIC,
                NOW + 2000,
                XxekirGromRules.UNIT_PREFIX + XxekirGromRules.MECH,
                XxekirGromRules.UNIT_PREFIX + XxekirGromRules.PDS);
        Optional<AiDecision> unit = XxekirGromRules.next(context(NOW + 2000, units));
        assertThat(unit.map(AiTestGame::pressedId)).isPresent();

        String prefix = "FFCC_xxcha_" + XxekirGromRules.PLACE_PREFIX + XxekirGromRules.MECH + "_";
        AiPrompt planets = prompt(
                "planets", PromptSource.PUBLIC, NOW + 3000, prefix + "fria", prefix + "abyz", prefix + "archontau");
        assertThat(XxekirGromRules.next(context(NOW + 3000, units, planets)).map(AiTestGame::pressedId))
                .contains(prefix + "abyz");
    }

    @Test
    void waitsForTheNextPromptThenGivesUp() {
        amassWealthNeedsArchonRen();
        AiPrompt turn = turnMenu();
        XxekirGromRules.beforePassing(context(NOW, turn), List.of(turn));

        assertThat(XxekirGromRules.next(context(NOW + 1000))).containsInstanceOf(AiDecision.Wait.class);
        assertThat(XxekirGromRules.next(context(NOW + 120_000))).isEmpty();
    }
}
