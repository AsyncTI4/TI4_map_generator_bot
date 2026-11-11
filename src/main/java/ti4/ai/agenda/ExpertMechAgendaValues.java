package ti4.ai.agenda;

import static ti4.ai.agenda.AgendaValueMath.isAgainst;
import static ti4.ai.agenda.AgendaValueMath.isFor;
import static ti4.ai.agenda.AgendaValueMath.lawRounds;
import static ti4.ai.agenda.AgendaValueMath.liveRivals;
import static ti4.ai.agenda.AgendaValueMath.relativeToRivals;
import static ti4.ai.agenda.AgendaValueMath.threat;
import static ti4.ai.agenda.AgendaValueMath.unitsOnBoard;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.MechRating;
import ti4.ai.eval.ValueScale;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;

@UtilityClass
class ExpertMechAgendaValues {

    private static final int ARTICLES_TRADE_GOODS = 3;
    private static final double NEIGHBOUR_WEIGHT = 2.0;
    private static final double MECH_WORTH = 2.0;
    private static final double INFANTRY_WORTH = 0.4;
    private static final int DEFAULT_MECH_CAP = 4;
    private static final String MECH_ASYNC_ID = "mf";

    @Nullable
    static Double valueOf(AgendaView view, String agenda, String outcome, String ownVote) {
        return switch (agenda) {
            case "articles_war" -> articlesOfWar(view.game(), view.voter(), outcome, ownVote);
            case "rearmament" -> rearmamentAgreement(view.game(), view.voter(), outcome);
            default -> null;
        };
    }

    static double articlesOfWar(Game game, Player voter, String outcome, String ownVote) {
        if (isFor(outcome)) return rivalStakesLost(game, voter) - articlesStake(game, voter);
        if (isAgainst(outcome) && isFor(ownVote)) return ARTICLES_TRADE_GOODS * ValueScale.TRADE_GOOD;
        return 0;
    }

    static double articlesStake(Game game, Player seat) {
        double perRound = MechRating.articlesStake(seat)
                + MechRating.onBoardAbility(seat) * unitsOnBoard(game, seat, UnitType.Mech);
        return perRound * lawRounds(game);
    }

    private static double rivalStakesLost(Game game, Player voter) {
        List<Player> rivals = liveRivals(game, voter);
        if (rivals.isEmpty()) return 0;
        Set<Player> neighbours = voter.getNeighbouringPlayers(true);
        double lost = rivals.stream()
                .mapToDouble(rival -> articlesStake(game, rival)
                        * threat(game, rival)
                        * (neighbours.contains(rival) ? NEIGHBOUR_WEIGHT : 1))
                .sum();
        return lost / rivals.size();
    }

    static double rearmamentAgreement(Game game, Player voter, String outcome) {
        if (isFor(outcome)) return relativeToRivals(game, voter, seat -> homeMechGain(game, seat));
        if (isAgainst(outcome)) return relativeToRivals(game, voter, seat -> -mechDowngrades(game, seat));
        return 0;
    }

    static double homeMechGain(Game game, Player seat) {
        if (!AgendaValueMath.controlsHomePlanet(seat) || !hasMechInReinforcements(game, seat)) return 0;
        return MechRating.homeWorth(seat);
    }

    static double mechDowngrades(Game game, Player seat) {
        return unitsOnBoard(game, seat, UnitType.Mech)
                * (MECH_WORTH - INFANTRY_WORTH + MechRating.abilityQuality(seat));
    }

    private static boolean hasMechInReinforcements(Game game, Player seat) {
        if (seat.getUnitByType(UnitType.Mech) == null) return false;
        int cap = seat.getUnitCap(MECH_ASYNC_ID);
        if (cap <= 0) cap = DEFAULT_MECH_CAP;
        return unitsOnBoard(game, seat, UnitType.Mech) < cap;
    }
}
