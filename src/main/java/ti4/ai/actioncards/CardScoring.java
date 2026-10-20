package ti4.ai.actioncards;

import lombok.experimental.UtilityClass;
import ti4.ai.agenda.AgendaForecast;
import ti4.ai.agenda.AgendaPolicy;
import ti4.ai.brain.StrategyCard;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.scoring.SystemStake;
import ti4.ai.strategy.ResearchPolicy;
import ti4.ai.strategy.StrategyCardRanking;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;

@UtilityClass
public class CardScoring {

    private static final String IMPERIAL_RIDER = "Imperial Rider";
    private static final double IMPERIAL_RIDER_CHANCE = 0.6;
    private static final double IMPERIAL_HOLDER_CHANCE = 0.5;
    private static final int UPRISING_TYPICAL_GAIN = 2;
    private static final int IMPERIAL_WITH_ANOTHER_PUBLIC = 2;
    private static final int ONE_POINT = 1;

    public static double pointsFor(String title, Game game, Player player) {
        return switch (title) {
            case "Overrule" -> ScoringStakes.overrulePoints(game, player);
            case IMPERIAL_RIDER -> IMPERIAL_RIDER_CHANCE;
            case "Political Stability", "Puppets on a String", "Master Plan" ->
                ScoringStakes.holdsUnplayedImperial(game, player) ? IMPERIAL_HOLDER_CHANCE : 0;
            case "Mining Initiative" -> SpendUnlock.pointDelta(game, player, ActionCardValue.miningGain(game, player));
            case "Industrial Initiative" ->
                SpendUnlock.pointDelta(
                        game, player, ButtonHelper.getNumberOfXTypePlanets(player, game, "industrial", true));
            case "Economic Initiative" ->
                SpendUnlock.pointsUnlocked(game, player, ActionCardValue.exhaustedCulturalPlanets(game, player), 0);
            case "Uprising" -> SpendUnlock.pointDelta(game, player, UPRISING_TYPICAL_GAIN);
            case "Focused Research", "Divert Funding", "Plagiarize" ->
                ResearchPolicy.publicPointsOneTechShort(game, player) > 0 ? ONE_POINT : 0;
            default -> 0;
        };
    }

    public static double rivalStake(String title, Game game, Player viewer, Player cardPlayer) {
        if (!IMPERIAL_RIDER.equals(title)) {
            return ScoringStakes.rivalStake(game, cardPlayer, pointsFor(title, game, cardPlayer));
        }
        return imperialRiderChance(game, viewer, cardPlayer) * ScoringStakes.rivalStake(game, cardPlayer, ONE_POINT);
    }

    private static double imperialRiderChance(Game game, Player viewer, Player owner) {
        AgendaForecast forecast = AgendaForecast.of(game, viewer);
        return forecast.riderOutcome(owner, IMPERIAL_RIDER)
                .map(forecast::chance)
                .orElse(IMPERIAL_RIDER_CHANCE);
    }

    public static double pointsDenied(String title, Game game, Player seat) {
        if (Involvement.isCombatCard(title)) return SystemStake.points(game, seat);
        return switch (title) {
            case "Public Disgrace", "Coup d'Etat", "Crisis" -> strategicPlayStake(game, seat);
            case "Extreme Duress" -> Math.max(0, -SpendUnlock.pointDelta(game, seat, -seat.getTg()));
            case "Confounding Legal Text", "Deadly Plot", "Veto", "Assassinate Representative" ->
                AgendaPolicy.wouldElectToAPoint(game, seat) ? ONE_POINT : 0;
            default -> 0;
        };
    }

    private static int mecatolPoint(Player player) {
        return player.controlsMecatol(true) ? ONE_POINT : 0;
    }

    private static double strategicPlayStake(Game game, Player seat) {
        double stake = 0;
        for (int card : seat.getSCs()) {
            if (game.getPlayedSCs().contains(card)) continue;
            stake = Math.max(stake, cardStake(game, seat, card));
        }
        return stake;
    }

    private static double cardStake(Game game, Player seat, int card) {
        if (StrategyCard.of(game, card) == StrategyCard.IMPERIAL) return imperialStake(game, seat);
        boolean worthAPoint = StrategyCardRanking.value(game, seat, card) >= ObjectiveValue.VICTORY_POINT_VALUE;
        return worthAPoint ? ONE_POINT : 0;
    }

    private static double imperialStake(Game game, Player seat) {
        boolean extraPublic = ObjectivePolicy.scorablePublics(game, seat).size() >= IMPERIAL_WITH_ANOTHER_PUBLIC;
        return (extraPublic ? ONE_POINT : 0) + mecatolPoint(seat);
    }
}
