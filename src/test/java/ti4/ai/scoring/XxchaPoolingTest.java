package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The Xxcha hero (Political Data Nexus) counts every exhausted planet as resources plus influence, for both kinds of
// spending at once. The breakthrough (Archon's Gift) lets a planet pay either kind at its larger value, but only one.
class XxchaPoolingTest extends BaseTi4Test {

    private AiTestGame test;
    private Player xxcha;

    // Xxcha holds its home (Archon Ren 2/3, Archon Tau 1/1), all ready, and the 1/3 planet Centauri.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        xxcha = test.addSeat(AiTestGame.SECOND_AI_ID, "xxcha", "green");
        test.isActive(xxcha, "action");
        test.place("14", "301");
        test.place("34", "201");
        xxcha.addPlanet("archonren");
        xxcha.addPlanet("archontau");
    }

    private void holdCentauriOnly() {
        xxcha.removePlanet("archonren");
        xxcha.removePlanet("archontau");
        xxcha.addPlanet("centauri");
    }

    private void unlockHero() {
        xxcha.addLeader("xxchahero");
        xxcha.getLeader("xxchahero").orElseThrow().setLocked(false);
    }

    private void unlockBreakthrough() {
        xxcha.addBreakthrough("xxchabt");
        xxcha.setBreakthroughUnlocked("xxchabt", true);
    }

    private void reveal(String objective) {
        test.game
                .getRevealedPublicObjectives()
                .put(objective, test.game.getRevealedPublicObjectives().size() + 1);
    }

    @Test
    void anOrdinaryPlanetPaysItsOwnKindOnly() {
        holdCentauriOnly();

        Wallet wallet = Wallet.of(test.game, xxcha);

        assertThat(wallet.pooling()).isEqualTo(Pooling.SEPARATE);
        assertThat(wallet.resources()).isEqualTo(1);
        assertThat(wallet.influence()).isEqualTo(3);
        assertThat(wallet.canPay(SpendCost.resources(4))).isFalse();
    }

    // Centauri is 1 resource and 3 influence: 4 resources with the hero, 3 with the breakthrough.
    @Test
    void theHeroCountsAPlanetAsResourcesPlusInfluence() {
        holdCentauriOnly();
        unlockHero();

        Wallet wallet = Wallet.of(test.game, xxcha);

        assertThat(wallet.pooling()).isEqualTo(Pooling.COMBINED);
        assertThat(wallet.resources()).isEqualTo(4);
        assertThat(wallet.influence()).isEqualTo(4);
        assertThat(wallet.canPay(SpendCost.resources(4))).isTrue();
        assertThat(wallet.canPay(SpendCost.influence(4))).isTrue();
    }

    @Test
    void theBreakthroughCountsAPlanetAtItsLargerValue() {
        holdCentauriOnly();
        unlockBreakthrough();

        Wallet wallet = Wallet.of(test.game, xxcha);

        assertThat(wallet.pooling()).isEqualTo(Pooling.EITHER_AT_BEST);
        assertThat(wallet.resources()).isEqualTo(3);
        assertThat(wallet.influence()).isEqualTo(3);
        assertThat(wallet.canPay(SpendCost.resources(3))).isTrue();
        assertThat(wallet.canPay(SpendCost.resources(4))).isFalse();
    }

    // The hero treats the combined value as both resources and influence, so one planet can pay a resource cost and an
    // influence cost together. The breakthrough cannot: a planet pays one kind or the other.
    @Test
    void onlyTheHeroLetsOnePlanetPayBothKinds() {
        holdCentauriOnly();
        unlockHero();
        SpendCost both = new SpendCost(4, 4, 0, 0);

        Wallet.Payment payment = Wallet.of(test.game, xxcha).plan(both).orElseThrow();

        assertThat(payment.forResources()).containsExactly("centauri");
        assertThat(payment.forInfluence()).containsExactly("centauri");
        assertThat(payment.planetCount()).isEqualTo(1);

        xxcha.removeLeader("xxchahero");
        unlockBreakthrough();
        assertThat(Wallet.of(test.game, xxcha).canPay(both)).isFalse();
    }

    // Amass Wealth (3 resources, 3 influence, 3 trade goods): Archon Ren alone is 5 with the hero.
    @Test
    void theHeroPaysAmassWealthWithASinglePlanet() {
        xxcha.removePlanet("archontau");
        xxcha.setTg(3);
        SpendCost amassWealth = SpendCost.each(3);
        assertThat(Wallet.of(test.game, xxcha).canPay(amassWealth)).isFalse();

        unlockHero();
        Wallet.Payment payment = Wallet.of(test.game, xxcha).plan(amassWealth).orElseThrow();

        assertThat(payment.forResources()).containsExactly("archonren");
        assertThat(payment.forInfluence()).containsExactly("archonren");
        assertThat(payment.tradeGoods()).isEqualTo(3);
    }

    // With the breakthrough Archon Ren (2/3) pays 3 of either kind, so it still takes two planets for Amass Wealth.
    @Test
    void theBreakthroughStillNeedsTwoPlanetsForAmassWealth() {
        xxcha.setTg(3);
        unlockBreakthrough();
        Wallet wallet = Wallet.of(test.game, xxcha);

        assertThat(wallet.canPay(SpendCost.each(3))).isFalse();

        xxcha.addPlanet("centauri");
        Wallet.Payment payment =
                Wallet.of(test.game, xxcha).plan(SpendCost.each(3)).orElseThrow();
        assertThat(payment.planetCount()).isEqualTo(2);
        assertThat(payment.forResources()).doesNotContainAnyElementsOf(payment.forInfluence());
    }

    // Erect a Monument takes 8 resources. Archon Ren (2/3) and Archon Tau (1/1) are exhausted and Centauri (1/3) is
    // ready: readying the home planets is worth the point only because the planets pay their combined value.
    @Test
    void theHeroMakesReadyingAHomePlanetEnoughForAResourceObjective() {
        xxcha.addPlanet("centauri");
        xxcha.exhaustPlanet("archonren");
        xxcha.exhaustPlanet("archontau");
        reveal("monument");

        assertThat(SpendUnlock.unlocks(test.game, xxcha, List.of("archonren", "archontau"), 0))
                .isFalse();

        unlockHero();

        assertThat(SpendUnlock.unlocks(test.game, xxcha, List.of("archonren", "archontau"), 0))
                .isTrue();
    }

    // Readying an exhausted planet to afford a spend objective: the projected income from Diplomacy counts the pooled
    // value of the planets it will ready.
    @Test
    void expectedReadyingNetsOffBothKindsUnderTheHero() {
        List<Wallet.PlanetValue> readied = List.of(new Wallet.PlanetValue("archonren", 5, 5));

        SpendCost net = ExpectedReadying.netOf(SpendCost.each(6), readied, Pooling.COMBINED);

        assertThat(net).isEqualTo(new SpendCost(1, 1, 6, 0));
        assertThat(ExpectedReadying.netOf(SpendCost.each(6), readied, Pooling.SEPARATE))
                .isEqualTo(new SpendCost(1, 6, 6, 0));
    }

    // A planet that pays both kinds is exhausted once: the bot counts it for both costs from one button press.
    @Test
    void exhaustsAPlanetThatPaysBothKindsOnlyOnce() {
        holdCentauriOnly();
        unlockHero();
        test.game.setPhaseOfGame("statusScoring");
        PaymentRules.expect(
                test.contextFor(xxcha, Set.of(), AiTestGame.NOW),
                "an objective",
                new Wallet.Payment(List.of("centauri"), List.of("centauri"), 0, 0),
                PaymentRules.OBJECTIVE_DONE);
        AiPrompt payment = AiTestGame.prompt(
                "pay",
                PromptSource.PUBLIC,
                AiTestGame.NOW,
                List.of("spend_centauri_both", "deleteButtons"),
                List.of("Centauri", "Done Exhausting Planets"));

        assertThat(pressedBy(payment)).contains("spend_centauri_both");

        xxcha.exhaustPlanet("centauri");
        assertThat(pressedBy(payment)).contains("deleteButtons");
    }

    private String pressedBy(AiPrompt payment) {
        return PaymentRules.pay(test.contextFor(xxcha, Set.of(), AiTestGame.NOW, payment))
                .map(AiTestGame::pressedId)
                .orElse("");
    }
}
