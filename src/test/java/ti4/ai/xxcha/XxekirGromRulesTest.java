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
import ti4.ai.eval.ImaginedTurn;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.ExpectedReadying;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Xxekir Grom (Thunder's Edge) places up to 4 PDS or mechs on the seat's planets and readies each one. Xxcha holds its
// home (Archon Ren 2/3, Archon Tau 1/1), Fria (2/0) and Abyz (3/0), with Abyz and Archon Ren exhausted. It is worth
// playing when the planets it readies get spent this round (2 or more is enough), when the units it places help score
// (Mechanize the Military, structure objectives) or defend, or before passing when it pays for an objective: Amass
// Wealth (3 influence, 3 resources, 3 trade goods) needs Archon Ren ready. Units count for more in earlier rounds.
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

    private void mechanizeTheMilitaryInRoundThree() {
        test.game.setRound(3);
        xxcha.setSecret("mtm");
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

    // Played to pay for Amass Wealth, the hero must land on the exhausted planets the objective needs (Archon Ren),
    // and the readies it forecasts are only the placements that land on exhausted planets.
    @Test
    void placesOnThePlanetsTheSpendObjectiveNeedsAndCountsOnlyThoseReadies() {
        amassWealthNeedsArchonRen();

        XxekirGromPlan.Outlook outlook = XxekirGromPlan.outlook(test.game, xxcha);
        long onExhausted = outlook.placements().stream()
                .filter(placement -> xxcha.getExhaustedPlanets().contains(placement.planet()))
                .count();

        assertThat(outlook.unlocksSpend()).isTrue();
        assertThat(outlook.placements())
                .extracting(XxekirGromPlan.Placement::planet)
                .contains("archonren");
        assertThat(outlook.readiesCounted()).isEqualTo((int) onExhausted);
        assertThat(XxekirGromPlan.readiesThisRound(test.game, xxcha)).isEqualTo((int) onExhausted);
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

    // No mech is on the board yet: four mechs, one on each of the seat's four planets, complete Mechanize the Military.
    @Test
    void playsTheHeroInRoundThreeWhenFourMechsCompleteAnObjective() {
        mechanizeTheMilitaryInRoundThree();
        AiPrompt turn = turnMenu();

        Optional<AiDecision> decision = XxekirGromRules.insteadOfTacticalAction(context(NOW, turn), List.of(turn));

        assertThat(decision.map(AiTestGame::pressedId)).contains("FFCC_xxcha_componentAction");
        XxekirGromPlan.Outlook outlook = XxekirGromPlan.outlook(test.game, xxcha);
        assertThat(outlook.completesObjective()).isTrue();
        assertThat(outlook.placements())
                .extracting(XxekirGromPlan.Placement::unit)
                .containsOnly(XxekirGromRules.MECH)
                .hasSize(XxekirGromPlan.PLACEMENTS);
    }

    @Test
    void playsTheHeroBeforePassingWhenItsMechsCompleteAnObjective() {
        mechanizeTheMilitaryInRoundThree();
        AiPrompt turn = turnMenu();

        assertThat(XxekirGromRules.beforePassing(context(NOW, turn), List.of(turn))
                        .map(AiTestGame::pressedId))
                .contains("FFCC_xxcha_componentAction");
    }

    @Test
    void placesAMechForTheObjectiveWhenAskedWhichUnit() {
        mechanizeTheMilitaryInRoundThree();

        assertThat(XxekirGromRules.unitToPlace(test.game, xxcha)).isEqualTo(XxekirGromRules.MECH);
    }

    // Nothing to spend, nothing to score and no enemy near: the hero is kept, so its readies are not planned on. With
    // no
    // tactic token left the seat makes no tactical action, so the two exhausted planets would not be spent this round.
    @Test
    void countsNoHeroReadiesWhenItWouldNotBePlayed() {
        xxcha.setTacticalCC(0);
        AiPrompt turn = turnMenu();

        assertThat(XxekirGromRules.insteadOfTacticalAction(context(NOW, turn), List.of(turn)))
                .isEmpty();
        assertThat(XxekirGromPlan.readiesThisRound(test.game, xxcha)).isZero();
        // The seat still holds its agent, which readies one planet on its own.
        assertThat(ExpectedReadying.readiesBeforeStatus(test.game, xxcha))
                .isEqualTo(ExpectedReadying.XXCHA_AGENT_READIES);
    }

    // A rival imagining Xxcha's turn cannot see its secrets, so it plans on no hero readies; that answer must not be
    // served to Xxcha's own decision later in the same save, nor Xxcha's answer to the rival.
    @Test
    void anImaginedTurnAndTheSeatsOwnTurnKeepTheirOwnReadies() {
        mechanizeTheMilitaryInRoundThree();
        xxcha.setTacticalCC(0);

        int imaginedFirst = ImaginedTurn.of(xxcha, () -> XxekirGromPlan.readiesThisRound(test.game, xxcha));
        int own = XxekirGromPlan.readiesThisRound(test.game, xxcha);
        int imaginedAfter = ImaginedTurn.of(xxcha, () -> XxekirGromPlan.readiesThisRound(test.game, xxcha));

        assertThat(own).isPositive();
        assertThat(imaginedFirst).isZero();
        assertThat(imaginedAfter).isZero();
    }

    // Four PDS from the hero complete Build Defenses, but only Xxcha knows it holds that secret.
    @Test
    void anImaginedTurnDoesNotSeeAStructureSecret() {
        test.game.setRound(3);
        xxcha.setSecret("build_defenses");
        xxcha.setTacticalCC(0);

        int imagined = ImaginedTurn.of(xxcha, () -> XxekirGromPlan.readiesThisRound(test.game, xxcha));

        assertThat(imagined).isZero();
        assertThat(XxekirGromPlan.readiesThisRound(test.game, xxcha)).isPositive();
    }

    // Played for Amass Wealth, the hero readies the two exhausted planets (Archon Ren and Abyz), not a flat four.
    @Test
    void countsOnlyThePlanetsTheHeroReadiesWhenItWouldBePlayed() {
        amassWealthNeedsArchonRen();

        assertThat(XxekirGromPlan.readiesThisRound(test.game, xxcha)).isEqualTo(2);
        assertThat(ExpectedReadying.readiesBeforeStatus(test.game, xxcha))
                .isEqualTo(2 + ExpectedReadying.XXCHA_AGENT_READIES);
    }

    @Test
    void twoSpentPlanetsAreEnoughToPlayItWhileSpendingIsAhead() {
        assertThat(readyingOnly(2).worthAnAction(true)).isTrue();
        assertThat(readyingOnly(1).worthAnAction(true)).isFalse();
        assertThat(readyingOnly(2).worthAnAction(false)).isFalse();
    }

    // The same placements (6 trade goods of defence) clear the bar in round 3, with five rounds still ahead, but not in
    // round 6, when the units have only two rounds left to matter.
    @Test
    void anEarlierRoundIsWorthMoreThanALaterOneForTheSameUnits() {
        test.game.setRound(3);
        XxekirGromPlan.Outlook early = unitsOnly(XxekirGromPlan.roundsAhead(test.game));
        test.game.setRound(6);
        XxekirGromPlan.Outlook late = unitsOnly(XxekirGromPlan.roundsAhead(test.game));

        assertThat(early.value()).isGreaterThan(late.value());
        assertThat(early.worthAnAction(false)).isTrue();
        assertThat(late.worthAnAction(false)).isFalse();
    }

    private static XxekirGromPlan.Outlook readyingOnly(int spentPlanets) {
        return new XxekirGromPlan.Outlook(false, false, spentPlanets, 0.5 * spentPlanets, 0, 0, 1, List.of());
    }

    private static XxekirGromPlan.Outlook unitsOnly(int roundsAhead) {
        return new XxekirGromPlan.Outlook(false, false, 0, 0, 0, 6.0, roundsAhead, List.of());
    }
}
