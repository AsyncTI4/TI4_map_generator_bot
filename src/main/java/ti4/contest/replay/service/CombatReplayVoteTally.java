package ti4.contest.replay.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class CombatReplayVoteTally {

    private CombatReplayVoteTally() {}

    public static <V> long distinctVoterCount(List<V> votes, Function<V, String> voterId) {
        return votes.stream().map(voterId).distinct().count();
    }

    public static <V, O> List<Tally<V, O>> tallies(List<V> votes, Function<V, O> option) {
        Map<O, Tally<V, O>> talliesByOption = new LinkedHashMap<>();
        for (V vote : votes) {
            O selectedOption = option.apply(vote);
            if (selectedOption == null) continue;
            Tally<V, O> current = talliesByOption.get(selectedOption);
            if (current == null) {
                talliesByOption.put(selectedOption, new Tally<>(selectedOption, vote, 1));
                continue;
            }
            talliesByOption.put(
                    selectedOption, new Tally<>(selectedOption, current.firstVote(), current.voteCount() + 1));
        }
        return new ArrayList<>(talliesByOption.values());
    }

    public record Tally<V, O>(O option, V firstVote, int voteCount) {}
}
