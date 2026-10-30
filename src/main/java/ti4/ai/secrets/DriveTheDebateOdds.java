package ti4.ai.secrets;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.GameHorizon;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.model.AgendaModel;
import ti4.model.Mapper;

@UtilityClass
class DriveTheDebateOdds {

    private static final String ELECTION = "elect";
    private static final List<String> ELECTED_KINDS = List.of("player", "planet");
    private static final double AGENDAS_PER_ROUND = 2.0;
    private static final double FALLBACK_ELECTION_SHARE = 0.3;
    private static final double CEILING = 0.5;

    static double chance(Game game, Player seat) {
        double perAgenda = electionShare(game) * winChance(game, seat);
        double agendasLeft = AGENDAS_PER_ROUND * agendaRoundsLeft(game);
        return Math.min(CEILING, 1 - Math.pow(1 - perAgenda, agendasLeft));
    }

    private static int agendaRoundsLeft(Game game) {
        int roundsLeft = GameHorizon.roundsLeft(game);
        return game.isCustodiansScored() ? roundsLeft : Math.max(0, roundsLeft - 1);
    }

    static double electionShare(Game game) {
        List<String> deck = game.getAgendas();
        if (deck == null || deck.isEmpty()) return FALLBACK_ELECTION_SHARE;
        long elections = deck.stream().filter(DriveTheDebateOdds::isElection).count();
        return (double) elections / deck.size();
    }

    static double winChance(Game game, Player seat) {
        double own = Math.pow(influence(game, seat), 2);
        double all = game.getRealPlayers().stream()
                .mapToDouble(player -> Math.pow(influence(game, player), 2))
                .sum();
        return all <= 0 ? 0 : own / all;
    }

    private static int influence(Game game, Player player) {
        Integer total = Helper.getPlayerInfluenceTotal(player, game);
        return total == null ? 0 : total;
    }

    private static boolean isElection(String agendaId) {
        AgendaModel model = Mapper.getAgenda(agendaId);
        if (model == null || model.getTarget() == null) return false;
        String target = model.getTarget().toLowerCase();
        return target.startsWith(ELECTION) && ELECTED_KINDS.stream().anyMatch(target::contains);
    }
}
