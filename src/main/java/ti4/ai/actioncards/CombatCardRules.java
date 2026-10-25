package ti4.ai.actioncards;

import java.util.Comparator;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSettings;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.tactical.CombatMoment;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;

@UtilityClass
public class CombatCardRules {

    static final String RECOVERED = "recovered";
    private static final long HOLD_MILLIS = 10_000L;
    private static final long MODIFIER_WAIT_MILLIS = 20_000L;
    private static final long MODIFIER_POLL_MILLIS = 2_000L;
    private static final long RECENT_MILLIS = 2 * 60_000L;

    public static Optional<AiDecision> hold(AiTurnContext context) {
        for (String key : context.memory().keysStartingWith(PlayMemory.COMBAT_PREFIX)) {
            Optional<CardPlay> play = context.memory().get(key).flatMap(CardPlay::decode);
            if (play.isEmpty()) continue;
            if (play.get().active()) {
                String title = SabotageWindow.title(play.get().alias());
                return Optional.of(new AiDecision.Wait(context.now() + HOLD_MILLIS, "the " + title + " action card"));
            }
            if (awaitsModifier(context, play.get())) {
                return Optional.of(new AiDecision.Wait(
                        context.now() + MODIFIER_POLL_MILLIS, "its " + CombatCards.titleOf(play.get()) + " modifier"));
            }
        }
        return Optional.empty();
    }

    public static Optional<AiDecision> atRoundStart(AiTurnContext context, CombatMoment moment) {
        Optional<AiDecision> directHit = DirectHitRules.atRoundStart(context, moment);
        if (directHit.isPresent()) return directHit;
        Game game = context.game();
        Player seat = context.seat();
        if (!AiSettings.isCombatCardsEnabled()
                || game.isFowMode()
                || !ActionCardRules.mayPlayNow(game, seat)
                || holdsRoundModifier(seat)) {
            return Optional.empty();
        }
        String key = PlayMemory.combatKey(moment.position(), moment.holder(), moment.round());
        if (context.memory().has(key)) return Optional.empty();
        return bestAtRoundStart(context, moment)
                .flatMap(alias -> ActionCardRules.startPlay(context, key, alias, PlayMoment.IN_TURN));
    }

    public static void watchSpaceCannon(AiTurnContext context, String position) {
        DirectHitRules.openCannon(context, position);
    }

    public static Optional<AiDecision> afterSpaceCannon(AiTurnContext context) {
        return DirectHitRules.afterCannon(context);
    }

    public static Optional<AiDecision> cancelHits(AiTurnContext context) {
        return HitCancelCards.cancelHits(context);
    }

    static boolean outdated(AiTurnContext context, String key) {
        Game game = context.game();
        return !"action".equalsIgnoreCase(game.getPhaseOfGame())
                || !PlayMemory.combatPosition(key).equals(game.getActiveSystem());
    }

    static boolean tracksAPlay(AiTurnContext context) {
        return context.memory().keysStartingWith(PlayMemory.COMBAT_PREFIX).stream()
                .map(key -> context.memory().get(key).flatMap(CardPlay::decode))
                .flatMap(Optional::stream)
                .anyMatch(play -> play.active() || context.now() < play.since() + RECENT_MILLIS);
    }

    private record Candidate(String alias, double value) {

        double margin() {
            return value - ActionCardPrior.keepPrior(alias);
        }
    }

    private static Optional<String> bestAtRoundStart(AiTurnContext context, CombatMoment moment) {
        Game game = context.game();
        Player seat = context.seat();
        return seat.getActionCards().keySet().stream()
                .sorted()
                .map(alias -> new Candidate(alias, CombatCards.valueAtRoundStart(game, seat, moment, alias)))
                .filter(candidate -> candidate.value() > 0 && candidate.margin() >= 0)
                .max(Comparator.comparingDouble(Candidate::margin))
                .map(Candidate::alias)
                .or(() -> moraleBoostForCloseOdds(game, seat, moment));
    }

    private static Optional<String> moraleBoostForCloseOdds(Game game, Player seat, CombatMoment moment) {
        return seat.getActionCards().keySet().stream()
                .sorted()
                .filter(CombatCards.MORALE_BOOSTS::contains)
                .findFirst()
                .filter(alias -> CombatCards.closeEnoughForMoraleBoost(game, seat, moment));
    }

    private static boolean awaitsModifier(AiTurnContext context, CardPlay play) {
        return play.stage() == Stage.DONE
                && CombatCards.addsRoundModifier(play.alias())
                && context.now() < play.since() + MODIFIER_WAIT_MILLIS
                && !holdsRoundModifier(context.seat(), play.alias());
    }

    private static boolean holdsRoundModifier(Player seat) {
        return seat.getNewTempCombatModifiers().stream().anyMatch(mod -> Constants.AC.equals(mod.getRelatedType()));
    }

    private static boolean holdsRoundModifier(Player seat, String alias) {
        return seat.getNewTempCombatModifiers().stream().anyMatch(mod -> alias.equals(mod.getRelatedID()));
    }
}
