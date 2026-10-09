package ti4.spring.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.gesundkrank.jskills.Rating;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalibratedRatingChangeCapTest {

    private static final double TOLERANCE = 1.0e-9;
    private static final double CAP = TrueSkillMatchmakingRatingService.MAX_CALIBRATED_RATING_LOSS_PER_GAME;
    private static final double FLOOR = TrueSkillMatchmakingRatingService.MIN_CALIBRATED_RATING_GAIN_PER_GAME;
    private static final double CALIBRATED_SIGMA = 1.3;
    private static final double UNCALIBRATED_SIGMA = 2.5;
    private static final long DAY_MILLIS = 86_400_000L;
    private static final int NARROW_LOSS_RANK = 2;
    private static final int HEAVY_LOSS_RANK = 10;

    @Test
    void leavesALargeGainForACalibratedPlayerAlone() {
        Rating current = new Rating(30.0, CALIBRATED_SIGMA);
        Rating unclamped = new Rating(31.5, CALIBRATED_SIGMA - 0.01);

        assertThat(TrueSkillMatchmakingRatingService.clampCalibratedChange(current, unclamped, true))
                .isSameAs(unclamped);
    }

    @Test
    void capsALargeLossForACalibratedPlayer() {
        Rating current = new Rating(30.0, CALIBRATED_SIGMA);
        Rating uncapped = new Rating(28.5, CALIBRATED_SIGMA - 0.01);

        Rating capped = TrueSkillMatchmakingRatingService.clampCalibratedChange(current, uncapped, false);

        assertThat(capped.getConservativeRating() - current.getConservativeRating())
                .isEqualTo(-CAP, within(TOLERANCE));
        assertThat(capped.getStandardDeviation()).isEqualTo(uncapped.getStandardDeviation());
    }

    @Test
    void leavesASmallChangeForACalibratedPlayerAlone() {
        Rating current = new Rating(30.0, CALIBRATED_SIGMA);
        Rating uncapped = new Rating(30.2, CALIBRATED_SIGMA);

        assertThat(TrueSkillMatchmakingRatingService.clampCalibratedChange(current, uncapped, false))
                .isSameAs(uncapped);
    }

    @Test
    void raisesATinyGainToTheMinimum() {
        Rating current = new Rating(30.0, CALIBRATED_SIGMA);
        Rating unclamped = new Rating(30.02, CALIBRATED_SIGMA);

        Rating clamped = TrueSkillMatchmakingRatingService.clampCalibratedChange(current, unclamped, true);

        assertThat(clamped.getConservativeRating() - current.getConservativeRating())
                .isEqualTo(FLOOR, within(TOLERANCE));
    }

    @Test
    void leavesATinyLossAlone() {
        Rating current = new Rating(30.0, CALIBRATED_SIGMA);
        Rating unclamped = new Rating(29.98, CALIBRATED_SIGMA);

        assertThat(TrueSkillMatchmakingRatingService.clampCalibratedChange(current, unclamped, false))
                .isSameAs(unclamped);
    }

    @Test
    void raisesATinyGainWithoutAWinToTheMinimum() {
        Rating current = new Rating(30.0, CALIBRATED_SIGMA);
        Rating unclamped = new Rating(30.02, CALIBRATED_SIGMA);

        Rating clamped = TrueSkillMatchmakingRatingService.clampCalibratedChange(current, unclamped, false);

        assertThat(clamped.getConservativeRating() - current.getConservativeRating())
                .isEqualTo(FLOOR, within(TOLERANCE));
    }

    @Test
    void turnsAWinThatWouldLoseRatingIntoTheMinimumGain() {
        // A win against much weaker opponents can lower the conservative rating; a win must always gain.
        Rating current = new Rating(30.0, CALIBRATED_SIGMA);
        Rating unclamped = new Rating(29.7, CALIBRATED_SIGMA);

        Rating clamped = TrueSkillMatchmakingRatingService.clampCalibratedChange(current, unclamped, true);

        assertThat(clamped.getConservativeRating() - current.getConservativeRating())
                .isEqualTo(FLOOR, within(TOLERANCE));
    }

    @Test
    void leavesAPlayerWhoIsStillCalibratingAlone() {
        Rating current = new Rating(25.0, UNCALIBRATED_SIGMA);
        Rating uncapped = new Rating(28.0, UNCALIBRATED_SIGMA - 0.3);

        assertThat(TrueSkillMatchmakingRatingService.clampCalibratedChange(current, uncapped, false))
                .isSameAs(uncapped);
    }

    @Test
    void treatsTheCalibrationThresholdItselfAsCalibrated() {
        // The ladder shows a rating once calibrationPercent reaches 100%, i.e. sigma <= 1.7.
        Rating current = new Rating(30.0, 1.7);
        Rating uncapped = new Rating(28.0, 1.69);

        Rating capped = TrueSkillMatchmakingRatingService.clampCalibratedChange(current, uncapped, false);

        assertThat(capped.getConservativeRating() - current.getConservativeRating())
                .isEqualTo(-CAP, within(TOLERANCE));
    }

    @Test
    void heavyLossAfterCalibrationCostsExactlyTheCapInDisplayPoints() {
        List<MatchmakingRatingHistoryEntry> history =
                TrueSkillMatchmakingRatingService.calculateRatingHistory(strongPlayerThenHeavyLoss(), "u0", true);

        // Before calibration nothing is capped: the first game alone moves the rating by far more than 40.
        assertThat(displayChange(history.getFirst())).isGreaterThan(40);

        // u0 expects to win, so finishing 7 VP back would cost more than 40; the cap limits it to exactly 40.
        assertThat(displayChange(history.getLast())).isEqualTo(-40);
    }

    @Test
    void ladderAndHistoryAgreeWhenTheCapApplies() {
        MatchmakingRating ladderRating =
                TrueSkillMatchmakingRatingService.calculateRatings(strongPlayerThenHeavyLoss(), true).stream()
                        .filter(rating -> "u0".equals(rating.userId()))
                        .findFirst()
                        .orElseThrow();
        List<MatchmakingRatingHistoryEntry> history =
                TrueSkillMatchmakingRatingService.calculateRatingHistory(strongPlayerThenHeavyLoss(), "u0", true);

        assertThat(history.getLast().endRating()).isEqualTo(ladderRating.rating());
    }

    private static long displayChange(MatchmakingRatingHistoryEntry entry) {
        return MatchmakingRatingEventService.toDisplayRating(entry.endRating())
                - MatchmakingRatingEventService.toDisplayRating(entry.startRating());
    }

    // u0 wins four games in five against the same five opponents, which is enough mixed results to calibrate
    // (a player who never loses stays uncalibrated), losing narrowly. The final game is a heavy loss.
    private static List<MatchmakingGame> strongPlayerThenHeavyLoss() {
        List<MatchmakingGame> games = new ArrayList<>();
        for (int gameIndex = 0; gameIndex < 60; gameIndex++) {
            int winningSeat = gameIndex % 5 == 4 ? 1 + gameIndex / 5 % 5 : 0;
            games.add(game(gameIndex, winningSeat, NARROW_LOSS_RANK));
        }
        games.add(game(60, 1, HEAVY_LOSS_RANK));
        return games;
    }

    private static MatchmakingGame game(int gameIndex, int winningSeat, int u0RankWhenLosing) {
        List<MatchmakingPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 6; seat++) {
            String userId = "u" + seat;
            // Rank 1 wins, rank 2 is within 3 VP and rank 3 + n is n VP back (see MatchmakingGame).
            // The others finish 4-7 VP back.
            int rank = seat == winningSeat ? 1 : seat == 0 ? u0RankWhenLosing : 7 + (seat + gameIndex) % 4;
            players.add(new MatchmakingPlayer(userId, userId, rank));
        }
        return new MatchmakingGame("pbd" + (1000 + gameIndex), gameIndex * DAY_MILLIS, players);
    }
}
