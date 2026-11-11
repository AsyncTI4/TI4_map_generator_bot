package ti4.ai.yin;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;
import static ti4.ai.yin.YinTestBoard.PLANET;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.yin.DannelPlan.Landing;
import ti4.ai.yin.DannelPlan.Plan;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Dannel of the Tenth commits up to 3 infantry from reinforcements to non-home planets, with no space cannon, and is
// then purged. The seat spreads the 3 infantry over the planets where win chance x stake adds up most (planet value,
// objective progress, denial, Mecatol), and plays the hero when that beats its tactical plan by more than keeping the
// hero (half a point, nothing in the last round). Planets of other players' home systems are never offered.
class DannelRulesTest extends BaseTi4Test {

    private YinTestBoard board;
    private Tile open;

    @BeforeEach
    void setUp() {
        board = new YinTestBoard();
        board.yin.unsafeGetLeader(DannelPlan.HERO).setLocked(false);
        open = board.test.place("27", AiTestGame.HOME);
        board.sol.addPlanet(PLANET);
        board.units(PLANET, board.sol, UnitType.Infantry, 2);
    }

    @Test
    void takesTheOpenPlanetsBeforeAGuardedOne() {
        Plan plan = DannelPlan.best(board.test.game, board.yin).orElseThrow();

        assertThat(plan.landings()).extracting(Landing::planet).contains("starpoint", "newalbion");
        assertThat(plan.landings()).extracting(Landing::owner).isSubsetOf(DannelPlan.UNOWNED, "sol");
        assertThat(plan.landings().stream().mapToInt(Landing::infantry).sum()).isLessThanOrEqualTo(3);
    }

    @Test
    void needsTheHeroUnlocked() {
        board.yin.unsafeGetLeader(DannelPlan.HERO).setLocked(true);

        assertThat(DannelPlan.best(board.test.game, board.yin)).isEmpty();
    }

    @Test
    void neverTargetsAHomeSystem() {
        Plan plan = DannelPlan.best(board.test.game, board.yin).orElseThrow();

        assertThat(plan.landings()).extracting(Landing::planet).doesNotContain("darien");
    }

    @Test
    void keepingTheHeroCostsHalfAPointUntilTheLastRound() {
        assertThat(DannelPlan.holdValue(board.test.game)).isEqualTo(DannelPlan.HERO_VALUE);
    }

    @Test
    void walksTheHeroButtonsForEachLanding() {
        AiPrompt turn = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_yin_componentAction", "FFCC_yin_passForRound");
        Optional<AiDecision> opened = DannelRules.beforePassing(board.context(NOW, turn), List.of(turn));
        assertThat(opened.map(AiTestGame::pressedId)).contains("FFCC_yin_componentAction");
        DannelRules.Flow flow = DannelRules.current(board.context(NOW)).orElseThrow();
        Landing first = flow.current();

        long now = NOW + 1000;
        assertThat(step(now, "FFCC_yin_" + DannelRules.HERO_BUTTON)).contains("FFCC_yin_" + DannelRules.HERO_BUTTON);
        assertThat(step(now += 2000, "FFCC_yin_" + DannelRules.START)).contains("FFCC_yin_" + DannelRules.START);
        assertThat(step(now += 2000, DannelRules.TARGET_PREFIX + "sol", DannelRules.TARGET_PREFIX + first.owner()))
                .contains(DannelRules.TARGET_PREFIX + first.owner());
        assertThat(step(now += 2000, "FFCC_yin_" + DannelRules.PLANET_PREFIX + first.planet()))
                .contains("FFCC_yin_" + DannelRules.PLANET_PREFIX + first.planet());
        String count = "FFCC_yin_" + DannelRules.INFANTRY_PREFIX + first.planet() + "_" + first.infantry();
        assertThat(step(now += 2000, count)).contains(count);
        DannelRules.Flow after = DannelRules.current(board.context(now)).orElseThrow();
        assertThat(after.index()).isEqualTo(1);
    }

    @Test
    void pressesTheHeroMessageAgainForEveryLanding() {
        // The bot posts one "Invade A Planet With Yin Hero" message when the hero is played and keeps it until the
        // player deletes it, so every later landing starts from that same, older message.
        AiPrompt turn = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_yin_componentAction", "FFCC_yin_passForRound");
        DannelRules.beforePassing(board.context(NOW, turn), List.of(turn));
        List<Landing> landings =
                DannelRules.current(board.context(NOW)).orElseThrow().landings();
        assertThat(landings).hasSizeGreaterThan(1);

        long now = NOW + 1000;
        assertThat(step(now, "FFCC_yin_" + DannelRules.HERO_BUTTON)).isPresent();
        AiPrompt hero = prompt("hero", PromptSource.PUBLIC, now + 1000, "FFCC_yin_" + DannelRules.START);
        for (Landing landing : landings) {
            now += 30_000;
            assertThat(stepWith(now, hero)).contains("FFCC_yin_" + DannelRules.START);
            assertThat(step(now += 2000, DannelRules.TARGET_PREFIX + landing.owner()))
                    .contains(DannelRules.TARGET_PREFIX + landing.owner());
            assertThat(step(now += 2000, "FFCC_yin_" + DannelRules.PLANET_PREFIX + landing.planet()))
                    .isPresent();
            String count = "FFCC_yin_" + DannelRules.INFANTRY_PREFIX + landing.planet() + "_" + landing.infantry();
            assertThat(step(now += 2000, count)).contains(count);
        }
        DannelRules.Flow done = DannelRules.current(board.context(now)).orElseThrow();
        assertThat(done.index()).isEqualTo(landings.size());
        assertThat(done.stage()).isEqualTo(DannelRules.Stage.DONE);
    }

    @Test
    void givesUpOnAStepWhoseButtonNeverShows() {
        AiPrompt turn = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_yin_componentAction");
        DannelRules.beforePassing(board.context(NOW, turn), List.of(turn));

        assertThat(DannelRules.next(board.context(NOW + 1000))).containsInstanceOf(AiDecision.Wait.class);
        assertThat(DannelRules.next(board.context(NOW + 120_000))).isEmpty();
        assertThat(DannelRules.current(board.context(NOW)).orElseThrow().stage())
                .isEqualTo(DannelRules.Stage.DONE);
    }

    @Test
    void holdsTheTurnWhileAHeroGroundCombatIsOn() {
        AiPrompt turn = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_yin_componentAction");
        DannelRules.beforePassing(board.context(NOW, turn), List.of(turn));
        DannelRules.next(board.context(NOW + 120_000));
        board.units(PLANET, board.yin, UnitType.Infantry, 2);
        forcePlanOnto(PLANET);

        assertThat(DannelRules.holdTurn(board.context(NOW + 121_000))).containsInstanceOf(AiDecision.Wait.class);
    }

    private void forcePlanOnto(String planet) {
        DannelRules.Flow done = DannelRules.current(board.context(NOW)).orElseThrow();
        DannelRules.Flow onto = new DannelRules.Flow(
                DannelRules.Stage.DONE, done.since(), done.started(), 1, List.of(new Landing(planet, "sol", 2, 0)));
        board.test.memoryOf(board.yin).put("dannel|" + board.context(NOW).turnKey(), onto.encode());
    }

    private Optional<String> step(long now, String... ids) {
        AiPrompt prompt = prompt("step" + now, PromptSource.PUBLIC, now - 500, ids);
        return stepWith(now, prompt);
    }

    private Optional<String> stepWith(long now, AiPrompt prompt) {
        return DannelRules.next(board.context(now, prompt)).map(AiTestGame::pressedId);
    }
}
