package ti4.ai.agenda;

import static ti4.ai.agenda.AgendaValueMath.livePlayers;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;

@UtilityClass
class VoteCapacity {

    private static final String PREDICTIVE_INTELLIGENCE = "pi";
    private static final int PREDICTIVE_VOTES = 3;
    private static final String ZEAL = "zeal";
    private static final String BLOOD_PACT = "blood_pact";
    private static final int BLOOD_PACT_VOTES = 4;
    private static final String HACAN_COMMANDER = "hacancommander";
    private static final int VOTES_PER_TRADE_GOOD = 2;

    static int of(Game game, Player player) {
        if (AgendaVoting.cannotVote(game, player)) return 0;
        int planets = player.getPlanets().stream()
                .mapToInt(planet -> AgendaHelper.getSpecificPlanetsVoteWorth(player, game, planet))
                .sum();
        if (planets <= 0) return 0;
        return planets + otherSources(game, player);
    }

    static double shareSwing(Game game, Player voter) {
        if (AgendaVoting.cannotVote(game, voter)) return 0;
        int own = of(game, voter);
        List<Integer> capacities = livePlayers(game).stream()
                .filter(player -> !AgendaVoting.cannotVote(game, player))
                .map(player -> of(game, player))
                .toList();
        int total = capacities.stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) return 0;
        return 1.0 / capacities.size() - own / (double) total;
    }

    private static int otherSources(Game game, Player player) {
        int votes = 0;
        if (player.hasTech(PREDICTIVE_INTELLIGENCE)) votes += PREDICTIVE_VOTES;
        if (player.hasAbility(ZEAL)) votes += livePlayers(game).size();
        if (player.getPromissoryNotesInPlayArea().contains(BLOOD_PACT)) votes += BLOOD_PACT_VOTES;
        if (game.playerHasLeaderUnlockedOrAlliance(player, HACAN_COMMANDER)) {
            votes += VOTES_PER_TRADE_GOOD * player.getTg();
        }
        return votes;
    }
}
