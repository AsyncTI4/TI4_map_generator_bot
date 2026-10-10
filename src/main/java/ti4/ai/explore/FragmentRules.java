package ti4.ai.explore;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.game.Player;
import ti4.helpers.ButtonHelperExplore;

@UtilityClass
class FragmentRules {

    static final double EARLY_RELIC_VALUE = 1.0;
    private static final String FLOW = "fragments";
    private static final String MENU = "menu";
    private static final String PURGE = "purge";
    private static final String DONE = "done";
    private static final String GET_RELIC = "componentActionRes_getRelic_";
    private static final String PURGE_PREFIX = "purge_Frags_";
    private static final String DRAW_RELIC = "drawRelicFromFrag";
    private static final String UNKNOWN = "URF";
    private static final String HERETICAL_WORKS = "dhw";
    private static final int FRAGMENTS_PER_RELIC = 3;
    private static final int HERETICAL_WORKS_FRAGMENTS = 2;
    private static final Map<String, String> TRAIT_OF_KIND =
            Map.of("CRF", "cultural", "IRF", "industrial", "HRF", "hazardous", UNKNOWN, "frontier");
    private static final List<String> TRAIT_KINDS = List.of("CRF", "IRF", "HRF");

    static Optional<AiDecision> insteadOfTacticalAction(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!canMakeRelic(context.seat())) return Optional.empty();
        if (ComponentFlow.tacticalActionBeats(context, EARLY_RELIC_VALUE + ComponentFlow.STALL_ACTION_VALUE)) {
            return Optional.empty();
        }
        return open(context, thisTurn, "purge relic fragments instead of a weak tactical action");
    }

    static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!canMakeRelic(context.seat())) return Optional.empty();
        return open(context, thisTurn, "purge relic fragments for a relic instead of passing");
    }

    private static Optional<AiDecision> open(AiTurnContext context, List<AiPrompt> thisTurn, String reason) {
        if (ComponentFlow.taken(context)) return Optional.empty();
        Optional<Match> menu = ComponentFlow.menu(context, thisTurn);
        menu.ifPresent(match -> ComponentFlow.put(
                context,
                FLOW,
                MENU,
                String.valueOf(context.seat().getFragments().size())));
        return menu.map(match -> match.press(reason));
    }

    static Optional<AiDecision> next(AiTurnContext context) {
        Optional<List<String>> state = ComponentFlow.fields(context, FLOW);
        if (state.isEmpty()) return Optional.empty();
        List<AiPrompt> visible = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden())
                .toList();
        return switch (state.get().get(1)) {
            case MENU -> openPurging(context, visible, state.get());
            case PURGE -> purge(context, visible, state.get());
            default -> Optional.empty();
        };
    }

    private static Optional<AiDecision> openPurging(AiTurnContext context, List<AiPrompt> visible, List<String> state) {
        Optional<Match> getRelic = Prompts.owned(visible, context.faction(), GET_RELIC::equals);
        if (getRelic.isEmpty()
                || context.alreadyPressed(
                        getRelic.get().prompt(), getRelic.get().button())) {
            return Optional.empty();
        }
        Optional<String> kind = kindToPurge(context.seat());
        if (kind.isEmpty()) return Optional.empty();
        ComponentFlow.put(context, FLOW, PURGE, state.get(2), kind.get());
        return Optional.of(getRelic.get().press("choose the relic fragments to purge"));
    }

    private static Optional<AiDecision> purge(AiTurnContext context, List<AiPrompt> visible, List<String> state) {
        Player seat = context.seat();
        int purged = Integer.parseInt(state.get(2)) - seat.getFragments().size();
        int remaining = FRAGMENTS_PER_RELIC - purged;
        if (remaining <= 0) return drawRelic(context, visible);
        String kind = state.get(3);
        int ofKind = count(seat, kind);
        String next = ofKind > 0 ? kind : UNKNOWN;
        int amount = Math.min(remaining, count(seat, next));
        if (amount <= 0) return Optional.empty();
        String handler = PURGE_PREFIX + next + "_" + amount;
        return Prompts.owned(visible, context.faction(), handler::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()))
                .map(match -> match.press("purge " + amount + " " + next + " relic fragments"));
    }

    private static Optional<AiDecision> drawRelic(AiTurnContext context, List<AiPrompt> visible) {
        Optional<Match> draw = Prompts.owned(visible, context.faction(), DRAW_RELIC::equals);
        draw.ifPresent(match -> ComponentFlow.put(context, FLOW, DONE));
        return draw.map(match -> match.press("draw a relic"));
    }

    static boolean canMakeRelic(Player seat) {
        if (kindToPurge(seat).isEmpty()) return false;
        int total = fragmentCount(seat);
        boolean keepsTwo = seat.getSecretsUnscored().containsKey(HERETICAL_WORKS);
        return total - FRAGMENTS_PER_RELIC >= (keepsTwo ? HERETICAL_WORKS_FRAGMENTS : 0);
    }

    static Optional<String> kindToPurge(Player seat) {
        int unknown = count(seat, UNKNOWN);
        return TRAIT_KINDS.stream()
                .filter(kind -> count(seat, kind) + unknown >= FRAGMENTS_PER_RELIC)
                .max(Comparator.comparingInt(kind -> count(seat, kind)));
    }

    private static int fragmentCount(Player seat) {
        return count(seat, UNKNOWN)
                + TRAIT_KINDS.stream().mapToInt(kind -> count(seat, kind)).sum();
    }

    private static int count(Player seat, String kind) {
        return ButtonHelperExplore.getNormalFragmentCount(seat, TRAIT_OF_KIND.get(kind));
    }
}
