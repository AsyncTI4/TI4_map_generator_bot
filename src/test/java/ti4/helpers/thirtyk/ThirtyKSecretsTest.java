package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.model.SecretObjectiveModel;
import ti4.model.Source.ComponentSource;
import ti4.testUtils.BaseTi4Test;

/**
 * Status-phase 30k/JMSC secrets are scored through the generic threshold/progress pair; event secrets are
 * scored from prompts. These pin the thresholds, the counting rules and the prompt bookkeeping.
 */
class ThirtyKSecretsTest extends BaseTi4Test {

    private Game game;
    private Player sol;
    private Player hacan;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("thirtyk-secrets-test");
        game.setAcDeckID("action_cards_30k");
        sol = addPlayer("sol-id", "sol", "blue");
        hacan = addPlayer("hacan-id", "hacan", "green");
    }

    @Test
    void statusSecretsAreAutoCheckedAndEventSecretsAreNot() {
        List<String> statusSecrets = Mapper.getSecretObjectives().values().stream()
                .filter(so -> so.getSource() == ComponentSource.thirtyk)
                .filter(so -> "Status".equalsIgnoreCase(so.getPhase()))
                .map(SecretObjectiveModel::getAlias)
                .toList();
        assertThat(statusSecrets).isNotEmpty().allMatch(ThirtyKSecrets::isAutoChecked);
        assertThat(List.of("quickdeliberationsjmsc", "reprimandedjmsc", "policydefiance30k"))
                .noneMatch(ThirtyKSecrets::isAutoChecked);
    }

    @Test
    void countingSecretsReachTheirThresholdExactly() {
        for (int i = 0; i < 7; i++) sol.setActionCard("card" + i);
        sol.setTacticalCC(3);
        sol.setFleetCC(3);
        sol.setStrategicCC(2);
        assertThat(ThirtyKSecrets.progress("options30k", game, sol)).isEqualTo(7);
        assertThat(ThirtyKSecrets.progress("preparationforwar30k", game, sol))
                .isEqualTo(ThirtyKSecrets.threshold("preparationforwar30k"));

        sol.setStrategicCC(3);
        assertThat(ThirtyKSecrets.progress("unnecessarypredictions30k", game, sol))
                .isEqualTo(ThirtyKSecrets.threshold("unnecessarypredictions30k"));
    }

    // Ruling: only public objectives scored in the status phase count against Participation Trophy.
    @Test
    void participationTrophyFailsOnceAPublicIsScoredOrAnotherSecretIsHeld() {
        String scoredKey = "sol" + "round" + game.getRound() + "PO";
        sol.setSecret("participationtrophy30k");
        assertThat(ThirtyKSecrets.progress("participationtrophy30k", game, sol)).isEqualTo(1);

        game.setStoredValue(scoredKey, "Erect a Monument");
        assertThat(ThirtyKSecrets.progress("participationtrophy30k", game, sol)).isZero();

        game.setStoredValue(scoredKey, "None");
        sol.setSecret("options30k");
        assertThat(ThirtyKSecrets.progress("participationtrophy30k", game, sol)).isZero();
    }

    // Observing the Possibilities: each system must be matched to a different wormhole type.
    @Test
    void wormholeMatchingNeedsADifferentTypePerSystem() {
        assertThat(ThirtyKSecrets.largestDistinctMatching(List.of(Set.of("alpha"), Set.of("alpha"))))
                .isEqualTo(1);
        assertThat(ThirtyKSecrets.largestDistinctMatching(
                        List.of(Set.of("alpha"), Set.of("alpha", "beta"), Set.of("beta", "gamma"), Set.of("delta"))))
                .isEqualTo(4);
    }

    // Votes are read per outcome: riders and null entries are skipped, a colour counts as its player.
    @Test
    void votesAreParsedAndPolicyMakerNeedsAStrictLeader() {
        game.setCurrentAgendaVote("for", "sol_4;hacan_Galactic Threat Rider");
        game.setCurrentAgendaVote("against", "hacan_2;green_1");
        game.setCurrentAgendaVote("abstain", null);

        Map<String, Map<Player, Integer>> votes = ThirtyKSecretHooks.votesByOutcome(game);

        assertThat(votes).containsOnlyKeys("for", "against");
        assertThat(votes.get("for")).containsOnly(Map.entry(sol, 4));
        assertThat(votes.get("against")).containsOnly(Map.entry(hacan, 3));
        assertThat(ThirtyKSecretHooks.soleTopVoter(Map.of("sol", 5, "hacan", 3)))
                .isEqualTo("sol");
        assertThat(ThirtyKSecretHooks.soleTopVoter(Map.of("sol", 5, "hacan", 5)))
                .isNull();
    }

    // Voters are only tracked while someone can still score Policy Indifference, and only for the round.
    @Test
    void agendaVotersAreRememberedOnlyForHoldersAndOnlyThisRound() {
        game.setCurrentAgendaVote("for", "sol_4");
        ThirtyKSecretHooks.onAgendaResolved(game);
        assertThat(ThirtyKSecretHooks.votersThisRound(game)).isEmpty();

        hacan.setSecret("policyindifference30k");
        ThirtyKSecretHooks.onAgendaResolved(game);
        assertThat(ThirtyKSecretHooks.votersThisRound(game)).containsExactly("sol");

        game.setRound(game.getRound() + 1);
        assertThat(ThirtyKSecretHooks.votersThisRound(game)).isEmpty();
    }

    // Custodia Vigilia is a fake attachment; losing a planet with only that must not offer Relinquish Technology.
    @Test
    void relinquishTechnologyCountsOnlyRealAttachments() {
        Planet planet = new Planet("jord", null);
        planet.addToken("attachment_custodiavigilia_1.png");
        assertThat(ThirtyKSecretHooks.hasAttachment(planet)).isFalse();

        planet.addToken("attachment_biotic.png");
        assertThat(ThirtyKSecretHooks.hasAttachment(planet)).isTrue();
    }

    // Combat prompts fire once per turn, A Grand Entrance's flagship start is recorded once per turn and only
    // for holders, and re-running turn start keeps both; end of turn drops them.
    @Test
    void perTurnPromptStateLastsUntilEndOfTurn() {
        String flagshipKey = ThirtyKSecretHooks.FLAGSHIP_START_KEY + "sol";
        assertThat(ThirtyKSecretHooks.markOnce(game, "space+101+hacan")).isTrue();
        assertThat(ThirtyKSecretHooks.markOnce(game, "space+101+hacan")).isFalse();

        ThirtyKSecretHooks.onTurnStart(game, sol);
        assertThat(game.getStoredValue(flagshipKey)).isBlank();
        sol.setSecret(ThirtyKSecretHooks.A_GRAND_ENTRANCE);
        game.setStoredValue(flagshipKey, "start;101");
        ThirtyKSecretHooks.onTurnStart(game, sol);
        assertThat(game.getStoredValue(flagshipKey)).isEqualTo("start;101");

        ThirtyKTurnState.clearEndOfTurn(game);

        assertThat(ThirtyKSecretHooks.markOnce(game, "space+101+hacan")).isTrue();
        assertThat(game.getStoredValue(flagshipKey)).isBlank();
    }

    private Player addPlayer(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }
}
