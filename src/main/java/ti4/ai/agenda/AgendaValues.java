package ti4.ai.agenda;

import java.util.OptionalDouble;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class AgendaValues {

    private static final Set<String> DEPENDS_ON_OWN_VOTE = Set.of();

    public static boolean dependsOnOwnVote(String agenda) {
        return DEPENDS_ON_OWN_VOTE.contains(agenda);
    }

    static OptionalDouble of(Game game, Player voter, String agenda, String outcome, String ownVote) {
        return OptionalDouble.empty();
    }

    static double ownVoteAdjustment(Game game, Player voter, String agenda, String winner, String ownVote) {
        if (!dependsOnOwnVote(agenda)) return 0;
        OptionalDouble actual = of(game, voter, agenda, winner, ownVote);
        OptionalDouble assumed = of(game, voter, agenda, winner, winner);
        if (actual.isEmpty() || assumed.isEmpty()) return 0;
        return actual.getAsDouble() - assumed.getAsDouble();
    }
}
