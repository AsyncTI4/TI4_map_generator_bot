package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.model.SecretObjectiveModel;
import ti4.model.Source.ComponentSource;
import ti4.testUtils.BaseTi4Test;

/**
 * Status-phase 30k/JMSC secrets are scored through the generic threshold/progress pair, so these pin the
 * thresholds and the counting rules that do not need a board.
 */
class ThirtyKSecretsTest extends BaseTi4Test {

    private Game game;
    private Player sol;
    private Player hacan;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("thirtyk-secrets-test");
        sol = addPlayer("sol-id", "sol", "blue");
        hacan = addPlayer("hacan-id", "hacan", "green");
    }

    @Test
    void everyStatusPhaseSecretIsAutoChecked() {
        List<String> statusSecrets = Mapper.getSecretObjectives().values().stream()
                .filter(so -> so.getSource() == ComponentSource.thirtyk)
                .filter(so -> "Status".equalsIgnoreCase(so.getPhase()))
                .map(SecretObjectiveModel::getAlias)
                .toList();
        assertThat(statusSecrets)
                .isNotEmpty()
                .allSatisfy(id ->
                        assertThat(ThirtyKSecrets.isAutoChecked(id)).as(id).isTrue());
    }

    @Test
    void eventSecretsAreNotThresholdChecked() {
        // These are scored from prompts at the moment they happen, never from the status-phase check.
        assertThat(ThirtyKSecrets.threshold("quickdeliberationsjmsc")).isZero();
        assertThat(ThirtyKSecrets.threshold("reprimandedjmsc")).isZero();
        assertThat(ThirtyKSecrets.threshold("policydefiance30k")).isZero();
    }

    @Test
    void optionsCountsActionCards() {
        assertThat(ThirtyKSecrets.threshold("options30k")).isEqualTo(7);
        for (int i = 0; i < 7; i++) sol.setActionCard("card" + i);
        assertThat(ThirtyKSecrets.progress("options30k", game, sol)).isEqualTo(7);
    }

    @Test
    void preparationForWarSumsAllPools() {
        sol.setTacticalCC(3);
        sol.setFleetCC(3);
        sol.setStrategicCC(2);
        assertThat(ThirtyKSecrets.progress("preparationforwar30k", game, sol)).isEqualTo(8);
        assertThat(ThirtyKSecrets.threshold("preparationforwar30k")).isEqualTo(8);
    }

    @Test
    void unnecessaryPredictionsCountsStrategyTokens() {
        sol.setStrategicCC(3);
        assertThat(ThirtyKSecrets.progress("unnecessarypredictions30k", game, sol))
                .isEqualTo(ThirtyKSecrets.threshold("unnecessarypredictions30k"));
    }

    @Test
    void nothingToLoseNeedsNoCardsNoTradeGoodsAndNoReadyPlanets() {
        sol.setTg(0);
        assertThat(ThirtyKSecrets.progress("nothingtolose30k", game, sol)).isEqualTo(1);
        sol.setTg(1);
        assertThat(ThirtyKSecrets.progress("nothingtolose30k", game, sol)).isZero();
    }

    @Test
    void participationTrophyFailsOnceAPublicIsScoredOrAnotherSecretIsHeld() {
        sol.setSecret("participationtrophy30k");
        assertThat(ThirtyKSecrets.progress("participationtrophy30k", game, sol)).isEqualTo(1);

        game.setStoredValue("sol" + "round" + game.getRound() + "PO", "Erect a Monument");
        assertThat(ThirtyKSecrets.progress("participationtrophy30k", game, sol)).isZero();

        game.setStoredValue("sol" + "round" + game.getRound() + "PO", "None");
        sol.setSecret("options30k");
        assertThat(ThirtyKSecrets.progress("participationtrophy30k", game, sol)).isZero();
    }

    @Test
    void wormholeMatchingNeedsADifferentTypePerSystem() {
        // Two systems that both only have alpha count once; a third with alpha+beta frees the second slot.
        assertThat(ThirtyKSecrets.largestDistinctMatching(List.of(Set.of("alpha"), Set.of("alpha"))))
                .isEqualTo(1);
        assertThat(ThirtyKSecrets.largestDistinctMatching(
                        List.of(Set.of("alpha"), Set.of("alpha", "beta"), Set.of("beta", "gamma"), Set.of("delta"))))
                .isEqualTo(4);
    }

    @Test
    void policyMakerNeedsAStrictLeader() {
        assertThat(ThirtyKSecretHooks.soleTopVoter(Map.of("sol", 5, "hacan", 3)))
                .isEqualTo("sol");
        assertThat(ThirtyKSecretHooks.soleTopVoter(Map.of("sol", 5, "hacan", 5)))
                .isNull();
        assertThat(ThirtyKSecretHooks.soleTopVoter(Map.<String, Integer>of())).isNull();
    }

    @Test
    void votesAreReadPerOutcomeIgnoringRiders() {
        game.setCurrentAgendaVote("for", "sol_4;hacan_Galactic Threat Rider");
        game.setCurrentAgendaVote("against", "hacan_2;green_1");

        Map<String, Map<Player, Integer>> votes = ThirtyKSecretHooks.votesByOutcome(game);

        assertThat(votes.get("for")).containsOnly(Map.entry(sol, 4));
        assertThat(votes.get("against")).containsOnly(Map.entry(hacan, 3));
    }

    @Test
    void agendaVotersAreRememberedForTheRoundOnly() {
        game.setCurrentAgendaVote("for", "sol_4");
        ThirtyKSecretHooks.onAgendaResolved(game);
        assertThat(ThirtyKSecretHooks.votersThisRound(game)).containsExactly("sol");

        game.setRound(game.getRound() + 1);
        assertThat(ThirtyKSecretHooks.votersThisRound(game)).isEmpty();
    }

    @Test
    void combatPromptsAreOfferedOncePerTurn() {
        game.setActivePlayerID(sol.getUserID());
        assertThat(ThirtyKSecretHooks.markOnce(game, "space+101+hacan")).isTrue();
        assertThat(ThirtyKSecretHooks.markOnce(game, "space+101+hacan")).isFalse();

        sol.setNumberOfTurns(sol.getNumberOfTurns() + 1);
        assertThat(ThirtyKSecretHooks.markOnce(game, "space+101+hacan")).isTrue();
        assertThat(game.getStoredValue("thirtyKSecretPrompts")).doesNotContain(",", ":");
    }

    private Player addPlayer(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }
}
