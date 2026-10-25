package ti4.ai.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import ti4.ai.strategy.ResearchPlan;
import ti4.game.Game;
import ti4.game.Player;

/**
 * Self-play research plan log (off by default, -Dai.selfplay.researchplan=true turns it on): each seat's plan is
 * printed at the first observation of every round, and again only when its text changes, with the inputs that changed.
 */
final class ResearchPlanLog {

    static final String PROPERTY = "ai.selfplay.researchplan";

    private final Consumer<String> out;
    private final Map<String, ResearchPlan> printed = new HashMap<>();

    ResearchPlanLog(Consumer<String> out) {
        this.out = out;
    }

    static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    void observe(Game game) {
        for (Player seat : game.getRealPlayers()) {
            ResearchPlan plan = ResearchPlan.of(game, seat);
            ResearchPlan before = printed.get(seat.getFaction());
            String reason = reasonToPrint(plan, before);
            if (reason == null) continue;
            printed.put(seat.getFaction(), plan);
            out.accept(plan.describe() + " | changed because " + reason);
        }
    }

    private static String reasonToPrint(ResearchPlan plan, ResearchPlan before) {
        if (before == null) return "first look";
        if (before.round() != plan.round()) return "new round";
        if (before.describe().equals(plan.describe())) return null;
        List<String> changed = plan.changedParts(before);
        return changed.isEmpty() ? "the board" : String.join(", ", changed);
    }
}
