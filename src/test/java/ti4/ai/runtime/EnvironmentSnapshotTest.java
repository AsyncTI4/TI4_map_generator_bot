package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.game.persistence.GameManager;
import ti4.testUtils.BaseTi4Test;

// Plays a seeded game to the start of round 2, takes an in-process snapshot of everything (save file, fake Discord,
// message reactions, AI state, seat runtime state, clock, random source), plays on to the start of round 3, puts the
// snapshot back and plays the same stretch again. Both stretches must make the same decisions in the same order: that
// is what lets a rollout harness try other choices from one decision point.
// mvn -o test -Dtest=EnvironmentSnapshotTest -Dai.selfplay.snapshot=true
@EnabledIfSystemProperty(named = "ai.selfplay.snapshot", matches = "true")
class EnvironmentSnapshotTest extends BaseTi4Test {

    private static final List<String> FACTIONS = List.of("nekro", "sardakk", "hacan", "letnev", "sol", "xxcha");
    private static final long VIRTUAL_DAY = 24 * 60 * 60 * 1000L;

    @Test
    void restoringASnapshotPlaysTheSameStretchAgain() throws Exception {
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(1_790_000_000_000L)) {
            SelfPlayGame selfPlay = new SelfPlayGame(env, "aisnap731", 0, 731L);
            try {
                selfPlay.setUp(FACTIONS);
                selfPlay.run(game -> game.getRound() >= 2, 14 * VIRTUAL_DAY, 100_000);
                SelfPlayGame.Snapshot snapshot = selfPlay.snapshot();

                List<String> first = stretch(env, selfPlay);
                selfPlay.restore(snapshot);
                List<String> second = stretch(env, selfPlay);

                int differs = firstDifference(first, second);
                System.out.printf(
                        "SNAPSHOT %d and %d decisions, first difference at %d%n", first.size(), second.size(), differs);
                if (differs >= 0) {
                    System.out.println("  A: " + (differs < first.size() ? first.get(differs) : "(none)"));
                    System.out.println("  B: " + (differs < second.size() ? second.get(differs) : "(none)"));
                }
                assertThat(first).isNotEmpty();
                assertThat(differs)
                        .as("first differing decision after the restore")
                        .isEqualTo(-1);
            } finally {
                AiRuntime.forget(selfPlay.name);
                GameManager.delete(selfPlay.name);
            }
        }
    }

    private static List<String> stretch(SelfPlayEnvironment env, SelfPlayGame selfPlay) {
        List<String> decisions = new ArrayList<>();
        env.decisionListener = (context, decision) -> {
            if (TrajectoryLog.records(decision)) decisions.add(describe(context, decision));
        };
        selfPlay.run(game -> game.getRound() >= 3, 14 * VIRTUAL_DAY, 100_000);
        env.decisionListener = (context, decision) -> {};
        return decisions;
    }

    private static String describe(AiTurnContext context, AiDecision decision) {
        String choice = decision instanceof AiDecision.Press press
                ? press.button().customId() + " (" + press.reason() + ")"
                : decision.toString();
        return context.now() + " " + context.faction() + " " + choice;
    }

    private static int firstDifference(List<String> first, List<String> second) {
        for (int i = 0; i < Math.min(first.size(), second.size()); i++) {
            if (!first.get(i).equals(second.get(i))) return i;
        }
        return first.size() == second.size() ? -1 : Math.min(first.size(), second.size());
    }
}
