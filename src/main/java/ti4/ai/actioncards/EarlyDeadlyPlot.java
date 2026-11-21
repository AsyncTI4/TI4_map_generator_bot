package ti4.ai.actioncards;

import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.agenda.AgendaForecast;
import ti4.ai.agenda.AgendaPoints;
import ti4.ai.agenda.AgendaRound;
import ti4.ai.agenda.AgendaSwing;
import ti4.ai.eval.ValueScale;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;

@UtilityClass
class EarlyDeadlyPlot {

    static boolean applies(Game game, Player seat, String title) {
        return AgendaSwing.DEADLY_PLOT.equals(title)
                && !AgendaRound.isFinalAgenda(game)
                && AiExperiments.enabled(seat, Experiment.AGENDA_SABOTAGE);
    }

    static boolean worthDiscarding(Game game, Player seat, String winner) {
        return pointsOnlyToOthers(game, seat, winner) || powerfulForARivalDoingWell(game, seat, winner);
    }

    static double lostSecondAgendaVotes(Game game, Player seat) {
        int votes = seat.getReadiedPlanets().stream()
                .mapToInt(planet -> AgendaHelper.getSpecificPlanetsVoteWorth(seat, game, planet))
                .sum();
        return votes * ValueScale.INFLUENCE;
    }

    private static boolean pointsOnlyToOthers(Game game, Player seat, String winner) {
        Set<Player> gainers = AgendaPoints.gainers(game, winner);
        return !gainers.isEmpty() && !gainers.contains(seat);
    }

    private static boolean powerfulForARivalDoingWell(Game game, Player seat, String winner) {
        AgendaForecast forecast = AgendaForecast.of(game, seat);
        if (forecast.isEmpty()) return false;
        return game.getRealPlayers().stream()
                .filter(rival -> rival != seat && !rival.isEliminated())
                .filter(rival -> doingWell(game, seat, rival))
                .anyMatch(rival -> forecast.utility(rival, winner) >= ScoringStakes.vpWorth(game, rival));
    }

    private static boolean doingWell(Game game, Player seat, Player rival) {
        return ScoringStakes.contender(game, seat, rival) || TableStanding.aloneInTheLead(game, rival);
    }
}
