package ti4.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.Player;
import ti4.game.helper.GameHelper;
import ti4.service.statistics.ActionCardStatsService.ImpactWeights;
import ti4.service.statistics.ActionCardStatsService.PlayToWinCorrelationCount;
import ti4.service.statistics.ActionCardStatsService.UnattributedPlays;
import ti4.testUtils.BaseTi4Test;

class ActionCardStatsServiceTest extends BaseTi4Test {

    private static final ImpactWeights WEIGHTS = new ImpactWeights(0.5, 0.25, 0.25);

    @Test
    void shouldScaleTheLeadingFourOfDownForOneOfs() {
        // Sabotage leads on plays per copy (25 vs Direct Hit's 15 and Rise of a Messiah's 20), so a
        // single copy is estimated at 25 draws: 100 for the 4-ofs, a quarter of that for the 1-of.
        Map<String, Integer> estimatedDraws = ActionCardStatsService.computeEstimatedDraws(
                Map.of("Sabotage", 100, "Direct Hit", 60, "Rise of a Messiah", 20),
                Map.of("Sabotage", 4, "Direct Hit", 4, "Rise of a Messiah", 1));

        assertThat(estimatedDraws)
                .containsEntry("Sabotage", 100)
                .containsEntry("Direct Hit", 100)
                .containsEntry("Rise of a Messiah", 25);
    }

    @Test
    void shouldScaleTheLeadingOneOfUpForFourOfs() {
        // Rise of a Messiah is played 30 times off one copy, beating Sabotage's 25 per copy, so it
        // sets the per-copy estimate and the 4-ofs get four times as many estimated draws.
        Map<String, Integer> estimatedDraws = ActionCardStatsService.computeEstimatedDraws(
                Map.of("Sabotage", 100, "Rise of a Messiah", 30), Map.of("Sabotage", 4, "Rise of a Messiah", 1));

        assertThat(estimatedDraws).containsEntry("Sabotage", 120).containsEntry("Rise of a Messiah", 30);
    }

    @Test
    void shouldGiveEveryCardOfTheSameCopyCountTheSameEstimatedDraws() {
        Map<String, Integer> estimatedDraws = ActionCardStatsService.computeEstimatedDraws(
                Map.of("Sabotage", 20, "Overrule", 30), Map.of("Sabotage", 4, "Overrule", 4));

        assertThat(estimatedDraws).containsEntry("Sabotage", 30).containsEntry("Overrule", 30);
    }

    @Test
    void shouldSkipCardsNotInTheDeck() {
        Map<String, Integer> estimatedDraws = ActionCardStatsService.computeEstimatedDraws(
                Map.of("Sabotage", 10, "Mystery Card", 5), Map.of("Sabotage", 4));

        assertThat(estimatedDraws).containsOnlyKeys("Sabotage");
    }

    @Test
    void shouldDeriveOneOfDrawsFromTheFourOfsWhenNoOneOfWasPlayed() {
        Map<String, Integer> estimatedDraws = ActionCardStatsService.computeEstimatedDraws(
                Map.of("Sabotage", 10), Map.of("Sabotage", 4, "Rise of a Messiah", 1));

        // 2.5 draws per copy, rounded for the single copy the 1-of has.
        assertThat(estimatedDraws).containsEntry("Sabotage", 10).containsEntry("Rise of a Messiah", 3);
    }

    @Test
    void shouldSkipEveryCardWhenNothingWasPlayed() {
        Map<String, Integer> estimatedDraws =
                ActionCardStatsService.computeEstimatedDraws(Map.of(), Map.of("Sabotage", 4, "Rise of a Messiah", 1));

        assertThat(estimatedDraws).isEmpty();
    }

    @Test
    void shouldIncludeUnplayedCardsWhoseCopyCountHasPlays() {
        Map<String, Integer> estimatedDraws = ActionCardStatsService.computeEstimatedDraws(
                Map.of("Sabotage", 10), Map.of("Sabotage", 4, "Direct Hit", 4));

        // Direct Hit was never played, but the per-copy estimate still approximates its draws.
        assertThat(estimatedDraws).containsEntry("Direct Hit", 10);
    }

    @Test
    void shouldCountCancelsThatTheMigrationCouldNotAttributeToAPlayer() {
        Game game = new Game();
        game.setName("pbd1000");
        Player winner = new Player("winner", "", game);
        // The legacy-save migration reconstructs a cancel it cannot attribute as a canceled play
        // with no player - the shape that used to be dropped before it was counted.
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, null);
        game.getGameStats().markLatestPlayCanceled(GameStats.OVERRULE);
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, winner);

        Map<String, PlayToWinCorrelationCount> counts = new HashMap<>();
        ActionCardStatsService.accumulateActionCardPlayToWinCorrelation(game, winner, counts, new HashMap<>());

        PlayToWinCorrelationCount overrule = counts.get(GameStats.OVERRULE);
        assertThat(overrule.getPlaysIncludingCanceled()).isEqualTo(2);
        assertThat(overrule.getCanceled()).isEqualTo(1);
        // The unattributed cancel must not leak into anything win-attributed.
        assertThat(overrule.getTotal()).isEqualTo(1);
        assertThat(overrule.getWins()).isEqualTo(1);
    }

    @Test
    void shouldCountEveryCopyOfAMultiCopyCardPlayedInOneGame() {
        Game game = new Game();
        Player winner = new Player("winner", "", game);
        Player loser = new Player("loser", "", game);
        // Flank Speed has four copies in the deck, so the same game can see it played more than
        // once - each copy is its own play, and the winner's copies are each their own win.
        game.getGameStats().recordAcPlay("Flank Speed", winner);
        game.getGameStats().recordAcPlay("Flank Speed", winner);
        game.getGameStats().recordAcPlay("Flank Speed", loser);

        Map<String, PlayToWinCorrelationCount> counts = new HashMap<>();
        ActionCardStatsService.accumulateActionCardPlayToWinCorrelation(game, winner, counts, new HashMap<>());

        PlayToWinCorrelationCount flankSpeed = counts.get("Flank Speed");
        assertThat(flankSpeed.getPlaysIncludingCanceled()).isEqualTo(3);
        assertThat(flankSpeed.getTotal()).isEqualTo(3);
        assertThat(flankSpeed.getWins()).isEqualTo(2);
    }

    @Test
    void shouldNotCountACanceledPlayAsAWin() {
        Game game = new Game();
        Player winner = new Player("winner", "", game);
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, winner);
        game.getGameStats().markLatestPlayCanceled(GameStats.OVERRULE);

        Map<String, PlayToWinCorrelationCount> counts = new HashMap<>();
        ActionCardStatsService.accumulateActionCardPlayToWinCorrelation(game, winner, counts, new HashMap<>());

        PlayToWinCorrelationCount overrule = counts.get(GameStats.OVERRULE);
        // The winner played it and won, but the cancel meant it never got to affect the game.
        assertThat(overrule.getPlaysIncludingCanceled()).isEqualTo(1);
        assertThat(overrule.getTotal()).isZero();
        assertThat(overrule.getWins()).isZero();
    }

    @Test
    void shouldStillIgnoreUncancelledPlaysWithNoPlayer() {
        Game game = new Game();
        game.setName("pbd1000");
        Player winner = new Player("winner", "", game);
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, null);

        Map<String, PlayToWinCorrelationCount> counts = new HashMap<>();
        ActionCardStatsService.accumulateActionCardPlayToWinCorrelation(game, winner, counts, new HashMap<>());

        // An uncancelled play still feeds the win rate, so one with no player stays out entirely.
        assertThat(counts).isEmpty();
    }

    @Test
    void shouldLabelTheRatesOnTheLeadingCardOnly() {
        // Overrule tops all three rates, so it anchors each of them and scores the full 100.
        String rendered = renderCorrelation(
                Map.of(GameStats.OVERRULE, playToWinCount(200, 150, 60), "Lie in Wait", playToWinCount(100, 90, 20)),
                Map.of(GameStats.OVERRULE, 200, "Lie in Wait", 200),
                Map.of(GameStats.OVERRULE, 1, "Lie in Wait", 1));

        // The average row leads, so it is the one that names each rate in full and carries no rank,
        // having nothing to be ranked against. Every card below it abbreviates.
        assertThat(rendered).isEqualTo("""
                        - **Average across all cards:**
                          - 75.9 Impact Score
                          - 31.1% uncancelled play win rate
                          - 75.0% plays vs estimated draw rate
                          - 17.5% cancel rate
                        - **Overrule:**
                          - 100.0 Impact Score
                          - 40.0% win (#1)
                          - 100.0% play (#1)
                          - 25.0% cancel (#1)
                        - **Lie in Wait:**
                          - 53.9\\* Impact Score
                          - 22.2% win (#2)
                          - 50.0% play (#2)
                          - 10.0% cancel (#2)
                        """);
    }

    @Test
    void shouldAverageEachRateOverTheCardsRatherThanOverThePlays() {
        // Overrule is played twice as often as Lie in Wait, and must not count twice as much for
        // it: one card, one vote. Win rates of 40% and 22.2% average to 31.1%, not to the 33.3%
        // that pooling 80 wins over 240 uncancelled plays would give.
        String rendered = renderCorrelation(
                Map.of(GameStats.OVERRULE, playToWinCount(200, 150, 60), "Lie in Wait", playToWinCount(100, 90, 20)),
                Map.of(GameStats.OVERRULE, 200, "Lie in Wait", 200),
                Map.of(GameStats.OVERRULE, 1, "Lie in Wait", 1));

        assertThat(rendered).contains("- 31.1% uncancelled play win rate\n");
        assertThat(rendered).contains("- 75.0% plays vs estimated draw rate\n");
        assertThat(rendered).contains("- 17.5% cancel rate\n");
    }

    @Test
    void shouldKeepRawCountsOffTheAverageRowEvenUnderFullDetails() {
        String rendered = renderCorrelation(
                Map.of(GameStats.OVERRULE, playToWinCount(200, 150, 60), "Lie in Wait", playToWinCount(100, 90, 20)),
                Map.of(GameStats.OVERRULE, 200, "Lie in Wait", 200),
                Map.of(GameStats.OVERRULE, 1, "Lie in Wait", 1),
                true);

        // Every figure on that row is a mean over cards, while a count is a total over plays. The
        // two do not divide back into each other, so printing them together would only imply they do.
        assertThat(rendered.substring(0, rendered.indexOf("- **Overrule:**")))
                .contains("- 17.5% cancel rate\n")
                .doesNotContain(" wins, ");
        // The cards below it still carry theirs.
        assertThat(rendered).contains("  - 60 wins, 200 plays, 50 cancels\n");
    }

    @Test
    void shouldOnlyShowTheRawCountsWhenFullDetailsIsAskedFor() {
        Map<String, PlayToWinCorrelationCount> counts =
                Map.of(GameStats.OVERRULE, playToWinCount(200, 150, 60), "Lie in Wait", playToWinCount(100, 90, 20));
        Map<String, Integer> estimatedDraws = Map.of(GameStats.OVERRULE, 200, "Lie in Wait", 200);
        Map<String, Integer> copies = Map.of(GameStats.OVERRULE, 1, "Lie in Wait", 1);

        assertThat(renderCorrelation(counts, estimatedDraws, copies)).doesNotContain(" wins, ");
        assertThat(renderCorrelation(counts, estimatedDraws, copies, true))
                .contains("  - 60 wins, 200 plays, 50 cancels\n")
                .contains("  - 20 wins, 100 plays, 10 cancels\n");
    }

    @Test
    void shouldMatchThePluralToTheCountInFrontOfIt() {
        String rendered = renderCorrelation(
                Map.of(GameStats.OVERRULE, playToWinCount(200, 150, 60), "Lie in Wait", playToWinCount(2, 1, 1)),
                Map.of(GameStats.OVERRULE, 200, "Lie in Wait", 200),
                Map.of(GameStats.OVERRULE, 1, "Lie in Wait", 1),
                true);

        assertThat(rendered).contains("  - 1 win, 2 plays, 1 cancel\n");
    }

    @Test
    void shouldRankEachFigureSeparately() {
        // Every figure orders the two cards differently, so none of the ranks may be shared between
        // figures - a card leading one of them must still be ranked on its own merits in the others.
        String rendered = renderCorrelation(
                Map.of(GameStats.OVERRULE, playToWinCount(200, 150, 60), "Lie in Wait", playToWinCount(100, 50, 40)),
                Map.of(GameStats.OVERRULE, 200, "Lie in Wait", 200),
                Map.of(GameStats.OVERRULE, 1, "Lie in Wait", 1));

        // Lie in Wait wins and is canceled far more often per play, which outweighs Overrule being
        // played twice as much - so it leads the list and carries the labels.
        assertThat(rendered).contains("- 87.5\\* Impact Score").contains("- 64.0 Impact Score");
        assertThat(rendered).contains("- 80.0% win (#1)").contains("- 40.0% win (#2)");
        assertThat(rendered).contains("- 50.0% play (#2)").contains("- 100.0% play (#1)");
        assertThat(rendered).contains("- 50.0% cancel (#1)").contains("- 25.0% cancel (#2)");
    }

    @Test
    void shouldShareARankBetweenCardsTiedOnAFigure() {
        String rendered = renderCorrelation(
                Map.of(GameStats.OVERRULE, playToWinCount(100, 100, 50), "Lie in Wait", playToWinCount(50, 50, 25)),
                Map.of(GameStats.OVERRULE, 100, "Lie in Wait", 100),
                Map.of(GameStats.OVERRULE, 1, "Lie in Wait", 1));

        // Both convert half their plays, so both are #1 on win rate while the play rates differ.
        assertThat(rendered)
                .contains("- 50.0% win (#1)")
                .contains("- 50.0% win (#1)")
                .contains("- 100.0% play (#1)")
                .contains("- 50.0% play (#2)");
    }

    @Test
    void shouldWeightTheRatesTwoOneOne() {
        // Neither card is ever canceled, so the cancel component pays nothing to anybody and the
        // best score on offer is the other two weights. Lie in Wait matches Overrule's win rate but
        // is played half as often, so it keeps the win weight and half the play weight.
        String rendered = renderCorrelation(
                Map.of(GameStats.OVERRULE, playToWinCount(100, 100, 50), "Lie in Wait", playToWinCount(50, 50, 25)),
                Map.of(GameStats.OVERRULE, 100, "Lie in Wait", 100),
                Map.of(GameStats.OVERRULE, 1, "Lie in Wait", 1));

        assertThat(rendered).contains("- 75.0 Impact Score").contains("- 62.5\\* Impact Score");
    }

    @Test
    void shouldSplitTheCancelWeightBetweenWinsAndPlaysForACardNoSabotageCanCancel() {
        // Identical records, and neither card is ever canceled. Overrule still concedes the cancel
        // weight, because it could have been sabotaged and no card earned that category. Sabotage
        // never could be, so its tenth is shared out over the two figures that do apply to it.
        Map<String, PlayToWinCorrelationCount> counts = Map.of(
                GameStats.SABOTAGE, playToWinCount(100, 100, 50),
                GameStats.OVERRULE, playToWinCount(100, 100, 50));
        Map<String, Integer> draws = Map.of(GameStats.SABOTAGE, 100, GameStats.OVERRULE, 100);

        Map<String, Double> scores =
                ActionCardStatsService.computeImpactScores(counts, draws, Set.of(GameStats.SABOTAGE), WEIGHTS);

        assertThat(scores.get(GameStats.SABOTAGE)).isCloseTo(100.0, within(1e-9));
        assertThat(scores.get(GameStats.OVERRULE)).isCloseTo(75.0, within(1e-9));
    }

    @Test
    void shouldReadTheWeightsAsProportionsRatherThanAbsolutes() {
        Map<String, PlayToWinCorrelationCount> counts =
                Map.of(GameStats.OVERRULE, playToWinCount(100, 100, 50), "Lie in Wait", playToWinCount(50, 50, 25));
        Map<String, Integer> draws = Map.of(GameStats.OVERRULE, 100, "Lie in Wait", 100);

        // 2/1/1 is the same weighting as 0.5/0.25/0.25 written differently, so a caller should not
        // have to normalise by hand to get a score they can compare against another run.
        assertThat(ActionCardStatsService.computeImpactScores(counts, draws, Set.of(), new ImpactWeights(2, 1, 1)))
                .isEqualTo(ActionCardStatsService.computeImpactScores(counts, draws, Set.of(), WEIGHTS));
    }

    @Test
    void shouldLetTheWeightsDecideWhichCardLeads() {
        // Overrule is played twice as often; Lie in Wait is canceled far more per play. Which of
        // them leads is entirely a question of what the caller says matters.
        Map<String, PlayToWinCorrelationCount> counts =
                Map.of(GameStats.OVERRULE, playToWinCount(200, 200, 40), "Lie in Wait", playToWinCount(100, 50, 20));
        Map<String, Integer> draws = Map.of(GameStats.OVERRULE, 200, "Lie in Wait", 200);

        Map<String, Double> onPlays =
                ActionCardStatsService.computeImpactScores(counts, draws, Set.of(), new ImpactWeights(0, 1, 0));
        Map<String, Double> onCancels =
                ActionCardStatsService.computeImpactScores(counts, draws, Set.of(), new ImpactWeights(0, 0, 1));

        assertThat(onPlays.get(GameStats.OVERRULE)).isGreaterThan(onPlays.get("Lie in Wait"));
        assertThat(onCancels.get("Lie in Wait")).isGreaterThan(onCancels.get(GameStats.OVERRULE));
    }

    @Test
    void shouldLeaveACardNoSabotageCanCancelUnscoredWhenCancelsCarryTheWholeScore() {
        Map<String, PlayToWinCorrelationCount> counts = Map.of(
                GameStats.SABOTAGE, playToWinCount(100, 100, 50),
                GameStats.OVERRULE, playToWinCount(100, 50, 25));
        Map<String, Integer> draws = Map.of(GameStats.SABOTAGE, 100, GameStats.OVERRULE, 100);

        Map<String, Double> scores = ActionCardStatsService.computeImpactScores(
                counts, draws, Set.of(GameStats.SABOTAGE), new ImpactWeights(0, 0, 1));

        // The only figure being scored is the one Sabotage is exempt from, so there is nothing left
        // to judge it on - and a flat zero would read as a verdict rather than as the absence of one.
        assertThat(scores).containsOnlyKeys(GameStats.OVERRULE);
    }

    @Test
    void shouldNotLetARareWatcherCancelDragDownACardNoSabotageCanCancel() {
        // The one cancel Sabotage suffered came from something like a Watcher. Scoring it on the
        // cancel category would read that rarity as "nobody wanted this gone" and mark it down.
        Map<String, PlayToWinCorrelationCount> counts = Map.of(
                GameStats.SABOTAGE, playToWinCount(200, 199, 100),
                GameStats.OVERRULE, playToWinCount(200, 100, 100));
        Map<String, Integer> draws = Map.of(GameStats.SABOTAGE, 200, GameStats.OVERRULE, 200);

        double carvedOut = ActionCardStatsService.computeImpactScores(
                        counts, draws, Set.of(GameStats.SABOTAGE), WEIGHTS)
                .get(GameStats.SABOTAGE);
        double scoredOnCancels = ActionCardStatsService.computeImpactScores(counts, draws, Set.of(), WEIGHTS)
                .get(GameStats.SABOTAGE);

        assertThat(carvedOut).isGreaterThan(scoredOnCancels);
    }

    @Test
    void shouldStillShowTheCancelRateOfACardNoSabotageCanCancel() {
        // The figure is worth seeing even though it is not worth scoring.
        String rendered = renderCorrelation(
                Map.of(GameStats.SABOTAGE, playToWinCount(100, 99, 50)),
                Map.of(GameStats.SABOTAGE, 100),
                Map.of(GameStats.SABOTAGE, 4),
                Set.of(GameStats.SABOTAGE),
                false);

        assertThat(rendered).contains("- 1.0% cancel (#1)");
    }

    @Test
    void shouldNotLetATinySampleRunAwayWithTheScore() {
        // Two wins from two plays is a 100% win rate and nothing else. Shrinkage drags it back to
        // near the deck average, and the play rate it never earned keeps it below the real card.
        String rendered = renderCorrelation(
                Map.of(GameStats.OVERRULE, playToWinCount(200, 200, 60), "Lucky Shot", playToWinCount(2, 2, 2)),
                Map.of(GameStats.OVERRULE, 200, "Lucky Shot", 200),
                Map.of(GameStats.OVERRULE, 1, "Lucky Shot", 1));

        assertThat(rendered).contains("- 74.2 Impact Score").contains("- 50.2\\* Impact Score");
        // The real card leads despite conceding the win-rate anchor to the lucky one.
        assertThat(rendered.indexOf(GameStats.OVERRULE)).isLessThan(rendered.indexOf("Lucky Shot"));
    }

    @Test
    void shouldScoreACardThatWasAlwaysCanceledAtTheDeckAverage() {
        // Every play canceled means no win rate can be measured at all. The deck average is the
        // honest stand-in - scoring it a flat zero would punish the card for being feared.
        String rendered = renderCorrelation(
                Map.of(GameStats.OVERRULE, playToWinCount(100, 100, 40), "Always Saboed", playToWinCount(10, 0, 0)),
                Map.of(GameStats.OVERRULE, 100, "Always Saboed", 100),
                Map.of(GameStats.OVERRULE, 1, "Always Saboed", 1));

        // With a quarter of the score riding on cancels, a card nobody is willing to let resolve
        // leads the deck on that alone.
        assertThat(rendered).contains("- 0.0% win (#2)").contains("- 77.5\\* Impact Score");
        assertThat(rendered.indexOf("Always Saboed")).isLessThan(rendered.indexOf(GameStats.OVERRULE));
    }

    @Test
    void shouldScaleTheInstabilityMarkerWithCopiesInTheDeck() {
        // Identical records, so identical scores - but a 4-of should have been seen four times as
        // often, which makes the same sample thin for it and sound for the 1-of.
        String rendered = renderCorrelation(
                Map.of(GameStats.OVERRULE, playToWinCount(180, 150, 60), "Flank Speed", playToWinCount(180, 150, 60)),
                Map.of(GameStats.OVERRULE, 180, "Flank Speed", 180),
                Map.of(GameStats.OVERRULE, 1, "Flank Speed", 4));

        assertThat(rendered).contains("- 100.0 Impact Score").contains("- 100.0\\* Impact Score");
    }

    @Test
    void shouldShrinkLessWhenCardRatesReallyDoDiffer() {
        // Two cards 10 points apart on 100 plays each. Luck alone explains only a sliver of that
        // gap, so the rest is treated as real and a card needs 59 plays to half-outweigh the deck.
        assertThat(ActionCardStatsService.estimatePseudoPlays(List.of(rateSample(0.30, 100), rateSample(0.20, 100))))
                .isEqualTo(59, within(0.001));
    }

    @Test
    void shouldBarelyShrinkWildlySpreadRates() {
        // An 80% card beside a 20% card is far more spread than luck could produce, so each card's
        // own record is trusted almost outright - the floor keeps a little shrinkage in place.
        assertThat(ActionCardStatsService.estimatePseudoPlays(List.of(rateSample(0.80, 100), rateSample(0.20, 100))))
                .isEqualTo(5);
    }

    @Test
    void shouldShrinkHardestWhenTheSpreadIsNoWiderThanLuck() {
        // Identical rates are no evidence that cards differ at all, and two cards 10 points apart on
        // 12 plays each is a gap luck fully explains. Both lean entirely on the deck average.
        assertThat(ActionCardStatsService.estimatePseudoPlays(List.of(rateSample(0.25, 100), rateSample(0.25, 100))))
                .isEqualTo(500);
        assertThat(ActionCardStatsService.estimatePseudoPlays(List.of(rateSample(0.30, 12), rateSample(0.20, 12))))
                .isEqualTo(500);
    }

    @Test
    void shouldShrinkHardestWhenThereIsNothingToCompare() {
        assertThat(ActionCardStatsService.estimatePseudoPlays(List.of(rateSample(0.30, 100))))
                .isEqualTo(500);
        assertThat(ActionCardStatsService.estimatePseudoPlays(List.of())).isEqualTo(500);
    }

    private static double[] rateSample(double rate, int denominator) {
        return new double[] {rate, denominator};
    }

    private static String renderCorrelation(
            Map<String, PlayToWinCorrelationCount> counts,
            Map<String, Integer> estimatedDraws,
            Map<String, Integer> copiesPerName) {
        return renderCorrelation(counts, estimatedDraws, copiesPerName, false);
    }

    private static String renderCorrelation(
            Map<String, PlayToWinCorrelationCount> counts,
            Map<String, Integer> estimatedDraws,
            Map<String, Integer> copiesPerName,
            boolean includeFullDetails) {
        return renderCorrelation(counts, estimatedDraws, copiesPerName, Set.of(), includeFullDetails);
    }

    private static String renderCorrelation(
            Map<String, PlayToWinCorrelationCount> counts,
            Map<String, Integer> estimatedDraws,
            Map<String, Integer> copiesPerName,
            Set<String> uncancelableCards,
            boolean includeFullDetails) {
        List<String> blocks = new ArrayList<>();
        ActionCardStatsService.appendPlayToWinCorrelationStats(
                blocks, counts, estimatedDraws, copiesPerName, uncancelableCards, WEIGHTS, includeFullDetails);
        return String.join("", blocks);
    }

    @Test
    void shouldOnlyCorrelateGamesStartedAfterPlayerTrackingBegan() {
        assertThat(ActionCardStatsService.startedAfterPlayerTracking(gameCreatedOn("2026.05.24")))
                .isTrue();
        // The cutoff date itself is not after the cutoff, so it stays out along with everything
        // older - those games record plays with no player and would contribute cancels with no wins.
        assertThat(ActionCardStatsService.startedAfterPlayerTracking(gameCreatedOn("2026.05.23")))
                .isFalse();
        assertThat(ActionCardStatsService.startedAfterPlayerTracking(gameCreatedOn("2024.01.01")))
                .isFalse();
    }

    @Test
    void shouldLeaveOutGamesWithAnUnreadableCreationDate() {
        assertThat(ActionCardStatsService.startedAfterPlayerTracking(gameCreatedOn("not a date")))
                .isFalse();
    }

    private static Game gameCreatedOn(String creationDate) {
        Game game = new Game();
        game.setCreationDate(creationDate);
        return game;
    }

    @Test
    void shouldTallyUnattributedPlaysPerCardForTheDeveloperDebug() {
        Game game = gameCreatedOn("2026.06.01");
        game.setName("pbd1000");
        Player winner = new Player("winner", "", game);
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, null);
        game.getGameStats().markLatestPlayCanceled(GameStats.OVERRULE);
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, null);
        game.getGameStats().markLatestPlayCanceled(GameStats.OVERRULE);
        game.getGameStats().recordAcPlay(GameStats.OVERRULE, winner);
        game.getGameStats().recordAcPlay("Flank Speed", winner);

        Map<String, UnattributedPlays> unattributedPlays = new HashMap<>();
        ActionCardStatsService.accumulateActionCardPlayToWinCorrelation(
                game, winner, new HashMap<>(), unattributedPlays);

        // Only the two plays with no recorded player are tallied, and cards with none stay out.
        assertThat(unattributedPlays).containsOnlyKeys(GameStats.OVERRULE);
        UnattributedPlays overrule = unattributedPlays.get(GameStats.OVERRULE);
        assertThat(overrule.getCount()).isEqualTo(2);
        // Both plays came from the one game, so it is named once and dates the whole span.
        assertThat(overrule.getGameNames()).containsExactly("pbd1000");
        assertThat(overrule.getFirstCreationDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(overrule.getLastCreationDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void shouldSpanTheCreationDatesOfEveryGameACardWasUnattributedIn() {
        UnattributedPlays plays = new UnattributedPlays();
        plays.record("pbd2", LocalDate.of(2026, 7, 1));
        plays.record("pbd1", LocalDate.of(2026, 6, 1));
        plays.record("pbd3", LocalDate.of(2026, 8, 1));

        assertThat(plays.getFirstCreationDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(plays.getLastCreationDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void shouldNameTheGamesBehindEveryUnattributedCardExceptOverrule() {
        String rendered = renderUnattributedPlayDebug(Map.of(
                GameStats.OVERRULE,
                unattributedPlays(Map.of("pbd1", "2026.06.01", "pbd2", "2026.08.01")),
                "Repeal Law",
                unattributedPlays(Map.of("pbd3", "2026.07.01", "pbd4", "2026.07.02"))));

        // Overrule is in too many games to name, so it reports the window they were created in.
        assertThat(rendered)
                .contains("- Overrule: 2 (games created 2026-06-01 through 2026-08-01, 2 games)\n")
                .contains("- Repeal Law: 2 (pbd3, pbd4)\n");
    }

    @Test
    void shouldStillCountOverruleWhenNoneOfItsGamesCouldBeDated() {
        String rendered = renderUnattributedPlayDebug(
                Map.of(GameStats.OVERRULE, unattributedPlays(Collections.singletonMap("pbd1", null))));

        assertThat(rendered).contains("- Overrule: 1\n");
    }

    private static UnattributedPlays unattributedPlays(Map<String, String> creationDatePerGameName) {
        UnattributedPlays plays = new UnattributedPlays();
        creationDatePerGameName.forEach((gameName, creationDate) -> plays.record(
                gameName,
                creationDate == null ? null : LocalDate.parse(creationDate, GameHelper.CREATION_DATE_FORMATTER)));
        return plays;
    }

    private static String renderUnattributedPlayDebug(Map<String, UnattributedPlays> unattributedPlays) {
        StringBuilder message = new StringBuilder();
        ActionCardStatsService.appendUnattributedPlayDebug(message, unattributedPlays);
        return message.toString();
    }

    private static PlayToWinCorrelationCount playToWinCount(int playsIncludingCanceled, int uncancelled, int wins) {
        PlayToWinCorrelationCount count = new PlayToWinCorrelationCount();
        for (int i = 0; i < playsIncludingCanceled; i++) {
            count.incrementPlaysIncludingCanceled();
        }
        for (int i = 0; i < uncancelled; i++) {
            count.incrementTotal();
        }
        for (int i = 0; i < wins; i++) {
            count.incrementWins();
        }
        return count;
    }
}
