package ti4.ai.explore;

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
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Three relic fragments of one kind (unknown fragments count as any kind) are purged for a relic as a component
// action. The bot does not check how many are purged, so the AI presses amounts that add up to exactly three.
class FragmentRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private AiPrompt turn;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.aiIsActive("action");
        turn = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_componentAction", "FFCC_nekro_passForRound");
    }

    // Three cultural fragments: it opens the component actions before passing, picks "Get Relic", purges all
    // three at once and draws the relic.
    @Test
    void purgesThreeFragmentsOfOneKindAndDrawsARelic() {
        fragments("crf1", "crf2", "crf3");

        assertThat(pressedId(RelicActionRules.beforePassing(test.context(turn), List.of(turn))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_componentAction");
        AiPrompt menu = prompt(
                "menu",
                PromptSource.PUBLIC,
                NOW + 1,
                "FFCC_nekro_componentActionRes_getRelic_",
                "FFCC_nekro_passForRound");
        assertThat(pressedId(RelicActionRules.next(test.context(menu)).orElseThrow()))
                .isEqualTo("FFCC_nekro_componentActionRes_getRelic_");

        AiPrompt purge = purgePrompt();
        assertThat(pressedId(RelicActionRules.next(test.context(purge)).orElseThrow()))
                .isEqualTo("FFCC_nekro_purge_Frags_CRF_3");

        removeFragments("crf1", "crf2", "crf3");
        assertThat(pressedId(RelicActionRules.next(test.context(purge)).orElseThrow()))
                .isEqualTo("FFCC_nekro_drawRelicFromFrag");
    }

    // Two cultural and one unknown fragment: the cultural ones first, the unknown one only to fill the third place.
    @Test
    void usesUnknownFragmentsOnlyToFill() {
        fragments("crf1", "crf2", "urf1", "irf1");
        RelicActionRules.beforePassing(test.context(turn), List.of(turn));
        AiPrompt menu = prompt("menu", PromptSource.PUBLIC, NOW + 1, "FFCC_nekro_componentActionRes_getRelic_");
        RelicActionRules.next(test.context(menu));
        AiPrompt purge = purgePrompt();

        assertThat(pressedId(RelicActionRules.next(test.context(purge)).orElseThrow()))
                .isEqualTo("FFCC_nekro_purge_Frags_CRF_2");
        removeFragments("crf1", "crf2");
        assertThat(pressedId(RelicActionRules.next(test.context(purge)).orElseThrow()))
                .isEqualTo("FFCC_nekro_purge_Frags_URF_1");
        removeFragments("urf1");
        assertThat(pressedId(RelicActionRules.next(test.context(purge)).orElseThrow()))
                .isEqualTo("FFCC_nekro_drawRelicFromFrag");
    }

    // Four fragments of one kind leave one behind: only three are purged.
    @Test
    void purgesNoMoreThanThree() {
        fragments("crf1", "crf2", "crf3", "crf4");
        RelicActionRules.beforePassing(test.context(turn), List.of(turn));
        RelicActionRules.next(
                test.context(prompt("menu", PromptSource.PUBLIC, NOW + 1, "FFCC_nekro_componentActionRes_getRelic_")));

        assertThat(pressedId(RelicActionRules.next(test.context(purgePrompt())).orElseThrow()))
                .isEqualTo("FFCC_nekro_purge_Frags_CRF_3");
    }

    // Two kinds are short of a set and an unknown fragment cannot make up the difference: nothing to purge yet.
    @Test
    void waitsForAFullSet() {
        fragments("crf1", "crf2", "irf1", "irf2");

        assertThat(RelicActionRules.beforePassing(test.context(turn), List.of(turn)))
                .isEmpty();
    }

    // Destroy Heretical Works needs two fragments at the status phase, so a relic is only made from five.
    @Test
    void keepsTwoFragmentsForDestroyHereticalWorks() {
        test.nekro.setSecret("dhw");
        fragments("crf1", "crf2", "crf3", "irf1");
        assertThat(RelicActionRules.beforePassing(test.context(turn), List.of(turn)))
                .isEmpty();

        fragments("irf2");
        assertThat(RelicActionRules.beforePassing(test.context(turn), List.of(turn)))
                .isPresent();
    }

    // It makes one relic a turn: the component action ends the turn.
    @Test
    void takesOneComponentActionPerTurn() {
        fragments("crf1", "crf2", "crf3", "crf4", "crf5", "crf6");
        assertThat(RelicActionRules.beforePassing(test.context(turn), List.of(turn)))
                .isPresent();

        assertThat(RelicActionRules.beforePassing(test.context(turn), List.of(turn)))
                .isEmpty();
    }

    // Mid-round, a relic is only worth an action when there is no better tactical plan: a free planet next door is.
    @Test
    void leavesTheFragmentsForLaterWhenATacticalActionIsWorthMore() {
        fragments("crf1", "crf2", "crf3");
        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        AiPrompt start =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_componentAction");

        assertThat(RelicActionRules.insteadOfTacticalAction(test.context(start), List.of(start)))
                .isEmpty();
    }

    // With no tactic token left there is nothing better to do with the turn.
    @Test
    void makesARelicInsteadOfAWeakTurn() {
        fragments("crf1", "crf2", "crf3");
        test.nekro.setTacticalCC(0);
        AiPrompt start =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_componentAction");

        assertThat(pressedId(RelicActionRules.insteadOfTacticalAction(test.context(start), List.of(start))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_componentAction");
    }

    private AiPrompt purgePrompt() {
        return prompt(
                "purge",
                PromptSource.PUBLIC,
                NOW + 2,
                "FFCC_nekro_purge_Frags_CRF_1",
                "FFCC_nekro_purge_Frags_CRF_2",
                "FFCC_nekro_purge_Frags_CRF_3",
                "FFCC_nekro_purge_Frags_URF_1",
                "FFCC_nekro_drawRelicFromFrag");
    }

    private void fragments(String... ids) {
        for (String id : ids) test.nekro.addFragment(id);
    }

    private void removeFragments(String... ids) {
        for (String id : ids) test.nekro.removeFragment(id);
    }
}
