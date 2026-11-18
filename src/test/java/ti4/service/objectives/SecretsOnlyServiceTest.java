package ti4.service.objectives;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class SecretsOnlyServiceTest extends BaseTi4Test {

    private Game game;
    private Player sol;
    private Player hacan;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        sol = game.addPlayer("sol-id", "sol");
        sol.setFaction("sol");
        sol.setColor("blue");
        hacan = game.addPlayer("hacan-id", "hacan");
        hacan.setFaction("hacan");
        hacan.setColor("green");
        game.setSecretsOnlyMode(true);
    }

    @Test
    void scoredSecretGoesBackIntoTheDeckForOthersToScore() {
        String soID = giveSecret(sol);

        assertThat(game.scoreSecretObjective(sol.getUserID(), sol.getSecrets().get(soID)))
                .isTrue();

        assertThat(sol.getSecretsScored()).containsKey(soID);
        assertThat(game.getSecretObjectives()).contains(soID);

        // Another player can pull it back out of the deck and score it again.
        game.drawSpecificSecretObjective(soID, hacan.getUserID());
        assertThat(game.scoreSecretObjective(
                        hacan.getUserID(), hacan.getSecrets().get(soID)))
                .isTrue();
        assertThat(hacan.getSecretsScored()).containsKey(soID);
        assertThat(game.getSecretObjectives()).containsOnlyOnce(soID);
    }

    @Test
    void discardingAnUnscoredCopyDoesNotDuplicateACardSomeoneElseHolds() {
        String soID = giveSecret(sol);
        game.scoreSecretObjective(sol.getUserID(), sol.getSecrets().get(soID));
        game.drawSpecificSecretObjective(soID, hacan.getUserID());

        // Elimination unscores everything and then discards the hand.
        game.unscoreSecretObjective(sol.getUserID(), sol.getSecretsScored().get(soID));
        game.discardSecretObjective(sol.getUserID(), sol.getSecrets().get(soID));

        assertThat(hacan.getSecrets()).containsKey(soID);
        assertThat(game.getSecretObjectives()).doesNotContain(soID);
    }

    @Test
    void aPlayerNeverScoresTheSameSecretTwice() {
        String soID = giveSecret(sol);
        game.scoreSecretObjective(sol.getUserID(), sol.getSecrets().get(soID));

        // Force the same card back into sol's hand, as a misclick or manual fix could.
        game.drawSpecificSecretObjective(soID, sol.getUserID());
        int handNumber = sol.getSecrets().get(soID);

        assertThat(SecretsOnlyService.hasAlreadyScored(sol, handNumber)).isTrue();
        assertThat(game.scoreSecretObjective(sol.getUserID(), handNumber)).isFalse();
        assertThat(sol.getSecretsScored()).hasSize(1);
    }

    @Test
    void drawsSkipSecretsThePlayerAlreadyScored() {
        String soID = giveSecret(sol);
        game.scoreSecretObjective(sol.getUserID(), sol.getSecrets().get(soID));
        moveToTopOfDeck(soID);

        assertThat(game.nextSecretObjectiveScorableBy(sol)).isNotEqualTo(soID);
        assertThat(game.nextSecretObjectiveScorableBy(hacan)).isEqualTo(soID);

        String drawn = game.drawSecretObjective(sol.getUserID());
        assertThat(drawn).isNotEqualTo(soID);
        assertThat(game.getSecretObjectives()).contains(soID);
    }

    @Test
    void offerTakesCardsOutOfTheDeckUntilOneIsKept() {
        int deckSize = game.getSecretObjectives().size();

        List<String> offered = SecretsOnlyService.drawOffer(game, sol, 3, 1, "round1");

        assertThat(offered).hasSize(3);
        assertThat(game.getSecretObjectives()).hasSize(deckSize - 3).doesNotContainAnyElementsOf(offered);
        assertThat(SecretsOnlyService.hasPendingOffer(sol)).isTrue();

        String kept = offered.get(1);
        assertThat(SecretsOnlyService.keep(game, sol, "round1", kept)).isTrue();

        assertThat(sol.getSecrets()).containsOnlyKeys(kept);
        assertThat(game.getSecretObjectives())
                .hasSize(deckSize - 1)
                .contains(offered.get(0), offered.get(2))
                .doesNotContain(kept);
        assertThat(SecretsOnlyService.hasPendingOffer(sol)).isFalse();
    }

    @Test
    void startingOfferKeepsTwoOfFourBeforeReturningTheRest() {
        int deckSize = game.getSecretObjectives().size();
        String tag = SecretsOnlyService.STARTING_OFFER_TAG;

        List<String> offered = SecretsOnlyService.drawOffer(
                game, sol, SecretsOnlyService.STARTING_OFFER_SIZE, SecretsOnlyService.STARTING_KEEP_COUNT, tag);
        assertThat(offered).hasSize(4);
        assertThat(SecretsOnlyService.picksLeft(sol, tag)).isEqualTo(2);

        assertThat(SecretsOnlyService.keep(game, sol, tag, offered.get(0))).isTrue();
        // After the first pick the remaining 3 stay out of the deck, waiting for the second pick.
        assertThat(SecretsOnlyService.picksLeft(sol, tag)).isEqualTo(1);
        assertThat(SecretsOnlyService.hasPendingOffer(sol)).isTrue();
        assertThat(game.getSecretObjectives()).hasSize(deckSize - 4);

        assertThat(SecretsOnlyService.keep(game, sol, tag, offered.get(0))).isFalse();
        assertThat(SecretsOnlyService.keep(game, sol, tag, offered.get(2))).isTrue();

        assertThat(sol.getSecrets()).containsOnlyKeys(offered.get(0), offered.get(2));
        assertThat(SecretsOnlyService.picksLeft(sol, tag)).isZero();
        assertThat(SecretsOnlyService.hasPendingOffer(sol)).isFalse();
        assertThat(game.getSecretObjectives()).hasSize(deckSize - 2).contains(offered.get(1), offered.get(3));
    }

    @Test
    void offerCannotBeKeptTwiceOrForACardThatWasNotOffered() {
        List<String> offered = SecretsOnlyService.drawOffer(game, sol, 2, 1, "round2");
        String notOffered = game.getSecretObjectives().getFirst();

        assertThat(SecretsOnlyService.keep(game, sol, "round2", notOffered)).isFalse();
        assertThat(SecretsOnlyService.keep(game, sol, "round2", offered.getFirst()))
                .isTrue();
        assertThat(SecretsOnlyService.keep(game, sol, "round2", offered.getLast()))
                .isFalse();
        assertThat(sol.getSecrets()).hasSize(1);
    }

    @Test
    void repeatingAnOfferResendsThePendingCardsInsteadOfDrawingMore() {
        int deckSize = game.getSecretObjectives().size();
        List<String> first = SecretsOnlyService.drawOffer(game, sol, 2, 1, "round3");
        List<String> second = SecretsOnlyService.drawOffer(game, sol, 2, 1, "round3");

        assertThat(second).containsExactlyElementsOf(first);
        assertThat(game.getSecretObjectives()).hasSize(deckSize - 2);
    }

    @Test
    void offersNeverIncludeSecretsThePlayerAlreadyScored() {
        String soID = giveSecret(sol);
        game.scoreSecretObjective(sol.getUserID(), sol.getSecrets().get(soID));
        moveToTopOfDeck(soID);

        assertThat(SecretsOnlyService.drawOffer(game, sol, 3, 1, "round1")).doesNotContain(soID);
    }

    @Test
    void onlyUnscoredSecretsCountTowardTheLimit() {
        for (int i = 0; i < 3; i++) {
            String soID = giveSecret(sol);
            game.scoreSecretObjective(sol.getUserID(), sol.getSecrets().get(soID));
        }
        giveSecret(sol);

        assertThat(sol.getSecretsCountedTowardLimit()).isEqualTo(1);

        game.setSecretsOnlyMode(false);
        assertThat(sol.getSecretsCountedTowardLimit()).isEqualTo(4);
    }

    @Test
    void modeCardsAreAddedOnceAndRemovedWhenTheModeIsTurnedOff() {
        List<String> modeActionCards =
                Mapper.getDeck(SecretsOnlyService.ACTION_CARD_DECK).getNewDeck();
        List<String> modeExplores =
                Mapper.getDeck(SecretsOnlyService.EXPLORE_DECK).getNewDeck();
        assertThat(modeActionCards).hasSize(6);
        assertThat(modeExplores).hasSize(8);

        SecretsOnlyService.addModeCards(game);
        SecretsOnlyService.addModeCards(game);

        for (String acID : modeActionCards) {
            assertThat(game.getActionCards()).containsOnlyOnce(acID);
        }
        for (String exploreID : modeExplores) {
            assertThat(game.getAllExplores()).containsOnlyOnce(exploreID);
        }
        for (String type : List.of("cultural", "hazardous", "industrial", "frontier")) {
            long ofType = modeExplores.stream()
                    .filter(id -> Mapper.getExplore(id).getType().equalsIgnoreCase(type))
                    .count();
            assertThat(ofType).as(type).isEqualTo(2);
        }

        List<String> recycledSecrets =
                Mapper.getDeck(SecretsOnlyService.SECRET_OBJECTIVE_DECK).getNewDeck();
        assertThat(game.getSecretObjectives()).containsAll(recycledSecrets);

        SecretsOnlyService.removeModeCards(game);
        assertThat(game.getSecretObjectives()).doesNotContainAnyElementsOf(recycledSecrets);
        assertThat(game.getActionCards()).doesNotContainAnyElementsOf(modeActionCards);
        assertThat(game.getAllExplores()).doesNotContainAnyElementsOf(modeExplores);
    }

    @Test
    void stealTheirThunderNeedsASecretSomeoneElseScoredFirst() {
        String soID = giveSecret(hacan);
        game.scoreSecretObjective(hacan.getUserID(), hacan.getSecrets().get(soID));
        assertThat(SecretsOnlyService.progress(SecretsOnlyService.STEAL_THEIR_THUNDER, game, sol))
                .isZero();

        game.drawSpecificSecretObjective(soID, sol.getUserID());
        game.scoreSecretObjective(sol.getUserID(), sol.getSecrets().get(soID));
        SecretsOnlyService.recordScore(game, sol, soID);

        assertThat(SecretsOnlyService.progress(SecretsOnlyService.STEAL_THEIR_THUNDER, game, sol))
                .isEqualTo(1);
        assertThat(SecretsOnlyService.progress(SecretsOnlyService.STEAL_THEIR_THUNDER, game, hacan))
                .isZero();

        // Undoing the repeat score takes the progress away again.
        game.unscoreSecretObjective(sol.getUserID(), sol.getSecretsScored().get(soID));
        assertThat(SecretsOnlyService.progress(SecretsOnlyService.STEAL_THEIR_THUNDER, game, sol))
                .isZero();
    }

    @Test
    void unchartedPathCountsOnlySecretsNobodyElseScoredFromRoundThree() {
        for (int i = 0; i < 3; i++) {
            String soID = giveSecret(sol);
            game.scoreSecretObjective(sol.getUserID(), sol.getSecrets().get(soID));
        }
        game.setRound(2);
        assertThat(SecretsOnlyService.progress(SecretsOnlyService.UNCHARTED_PATH, game, sol))
                .isEqualTo(2);

        game.setRound(3);
        assertThat(SecretsOnlyService.progress(SecretsOnlyService.UNCHARTED_PATH, game, sol))
                .isEqualTo(3);

        String shared = sol.getSecretsScored().keySet().iterator().next();
        game.drawSpecificSecretObjective(shared, hacan.getUserID());
        game.scoreSecretObjective(hacan.getUserID(), hacan.getSecrets().get(shared));
        assertThat(SecretsOnlyService.progress(SecretsOnlyService.UNCHARTED_PATH, game, sol))
                .isEqualTo(2);
    }

    private String giveSecret(Player player) {
        String soID = game.nextSecretObjectiveScorableBy(player);
        game.drawSpecificSecretObjective(soID, player.getUserID());
        return soID;
    }

    private void moveToTopOfDeck(String soID) {
        game.getSecretObjectives().remove(soID);
        game.getSecretObjectives().addFirst(soID);
    }
}
