package ti4.ai.actioncards;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSettings;
import ti4.ai.actioncards.DirectHitCards.Choice;
import ti4.ai.actioncards.DirectHitCards.Hurt;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.tactical.CombatMoment;
import ti4.ai.tactical.CombatRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class DirectHitRules {

    private static final String SEEN_KEY = "directHitSeen|";
    private static final String CANNON_KEY = "directHitCannon|";
    private static final String ROUND_HOLDER = "directhit";
    private static final String CANNON_HOLDER = "directhitcannon";
    private static final String CANNON_ASSIGNMENT = "autoAssignSpaceCannonOffenceHits_";
    private static final String STAMP = "#";
    private static final String ENTRY = ";";
    private static final String COUNT = "=";
    private static final String OWNER_TYPE = "|";
    private static final long CANNON_SETTLE_MILLIS = 10_000L;
    private static final long CANNON_WINDOW_MILLIS = 10 * 60_000L;

    private record Seen(long stamp, Map<String, Integer> damaged) {

        static Optional<Seen> decode(String encoded) {
            String stamp = StringUtils.substringBefore(encoded, STAMP);
            if (!StringUtils.isNumeric(stamp)) return Optional.empty();
            Map<String, Integer> damaged = new TreeMap<>();
            for (String entry : StringUtils.split(StringUtils.substringAfter(encoded, STAMP), ENTRY)) {
                String count = StringUtils.substringAfter(entry, COUNT);
                if (StringUtils.isNumeric(count))
                    damaged.put(StringUtils.substringBefore(entry, COUNT), Integer.parseInt(count));
            }
            return Optional.of(new Seen(Long.parseLong(stamp), damaged));
        }

        static String encode(long stamp, List<Hurt> hurt) {
            List<String> entries = new ArrayList<>();
            hurt.forEach(found -> entries.add(keyOf(found) + COUNT + found.count()));
            return stamp + STAMP + String.join(ENTRY, entries);
        }
    }

    private static String keyOf(Hurt hurt) {
        return hurt.owner().getFaction() + OWNER_TYPE + hurt.type().name();
    }

    static Optional<AiDecision> atRoundStart(AiTurnContext context, CombatMoment moment) {
        Game game = context.game();
        if (moment.ground()
                || game.isFowMode()
                || !AiSettings.isCombatCardsEnabled()
                || !moment.position().equals(game.getActiveSystem())) {
            return Optional.empty();
        }
        Tile tile = game.getTileByPosition(moment.position());
        if (tile == null) return Optional.empty();
        List<Hurt> hurt = DirectHitCards.hurtShips(game, context.seat(), tile);
        String seenKey = SEEN_KEY + moment.position();
        Optional<Seen> seen = context.memory().get(seenKey).flatMap(Seen::decode);
        if (seen.filter(found -> found.stamp() == moment.round()).isPresent()) return Optional.empty();
        context.memory().put(seenKey, Seen.encode(moment.round(), hurt));
        if (moment.round() == 0) return Optional.empty();
        // TODO A ship that arrived already damaged and was first seen after round 0 counts as freshly sustained.
        Map<String, Integer> before = seen.filter(found -> found.stamp() < moment.round())
                .map(Seen::damaged)
                .orElse(Map.of());
        String key = PlayMemory.combatKey(moment.position(), ROUND_HOLDER, moment.round());
        return playAgainst(context, moment.position(), freshSince(hurt, before), key);
    }

    static void openCannon(AiTurnContext context, String position) {
        Game game = context.game();
        Tile tile = game.getTileByPosition(position);
        if (tile == null
                || game.isFowMode()
                || !AiSettings.isCombatCardsEnabled()
                || DirectHitCards.heldAlias(context.seat()).isEmpty()) {
            return;
        }
        List<Hurt> hurt = DirectHitCards.hurtShips(game, context.seat(), tile);
        context.memory().put(CANNON_KEY + position, Seen.encode(context.now(), hurt));
    }

    static Optional<AiDecision> afterCannon(AiTurnContext context) {
        for (String key : context.memory().keysStartingWith(CANNON_KEY)) {
            String position = StringUtils.removeStart(key, CANNON_KEY);
            Optional<Seen> opened = context.memory().get(key).flatMap(Seen::decode);
            if (opened.isEmpty() || cannonWindowClosed(context, position, opened.get())) {
                context.memory().remove(key);
                continue;
            }
            if (context.now() < opened.get().stamp() + CANNON_SETTLE_MILLIS || assignmentPending(context, position)) {
                continue;
            }
            context.memory().remove(key);
            Tile tile = context.game().getTileByPosition(position);
            if (tile == null) continue;
            List<Hurt> hurt = DirectHitCards.hurtShips(context.game(), context.seat(), tile);
            String playKey =
                    PlayMemory.combatKey(position, CANNON_HOLDER + opened.get().stamp(), 0);
            Optional<AiDecision> play =
                    playAgainst(context, position, freshSince(hurt, opened.get().damaged()), playKey);
            if (play.isPresent()) return play;
        }
        return Optional.empty();
    }

    private static boolean cannonWindowClosed(AiTurnContext context, String position, Seen opened) {
        Game game = context.game();
        return context.now() > opened.stamp() + CANNON_WINDOW_MILLIS
                || !"action".equalsIgnoreCase(game.getPhaseOfGame())
                || !position.equals(game.getActiveSystem())
                || CombatRules.tracker(game, context.faction(), position, "space") > 0;
    }

    private static boolean assignmentPending(AiTurnContext context, String position) {
        String prefix = CANNON_ASSIGNMENT + position + "_";
        return context.prompts().stream()
                .map(AiPrompt::enabledButtons)
                .flatMap(List::stream)
                .map(PromptButton::handlerId)
                .anyMatch(id -> id.startsWith(prefix));
    }

    private static List<Hurt> freshSince(List<Hurt> hurt, Map<String, Integer> before) {
        List<Hurt> fresh = new ArrayList<>();
        for (Hurt found : hurt) {
            int added = found.count() - before.getOrDefault(keyOf(found), 0);
            if (added > 0) fresh.add(new Hurt(found.owner(), found.type(), found.model(), added));
        }
        return fresh;
    }

    private static Optional<AiDecision> playAgainst(
            AiTurnContext context, String position, List<Hurt> fresh, String key) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<String> alias = DirectHitCards.heldAlias(seat);
        if (alias.isEmpty()
                || fresh.isEmpty()
                || context.memory().has(key)
                || !ActionCardRules.mayPlayNow(game, seat)) {
            return Optional.empty();
        }
        Optional<Choice> best = DirectHitCards.best(game, seat, position, fresh);
        return best.flatMap(choice -> ActionCardRules.startPlay(context, key, alias.get(), choice.plan()));
    }
}
