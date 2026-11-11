package ti4.ai.agenda;

import static ti4.ai.agenda.AgendaValueMath.relativeToRivals;
import static ti4.ai.agenda.AgendaValueMath.rivalGainCost;

import java.util.Map;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.ValueScale;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;

@UtilityClass
class ExpertPlanetValues {

    private static final Map<String, Double> OWNER_WORTH = Map.of(
            "core_mining", 1.0,
            "demilitarized_zone", 2.0,
            "holy_planet_of_ixth", 3.0,
            "rt_biotic", 1.0,
            "rt_cybernetic", 1.0,
            "rt_propulsion", 1.0,
            "rt_warfare", 1.0,
            "senate_sanctuary", 1.0,
            "terraforming_initiative", 1.0);
    private static final String COMPENSATED_DISARMAMENT = "disarmament";
    private static final double INFANTRY_TG = 1.0;
    private static final double MECH_TG = 2.5;
    private static final double EXPOSED_PLANET_SHARE = 0.5;
    private static final int SPENDING_NEED = 6;

    @Nullable
    static Double valueOf(AgendaView view, String agenda, String outcome, String ownVote) {
        Double worth = OWNER_WORTH.get(agenda);
        if (worth != null) return electedPlanetGain(view.game(), view.voter(), agenda, outcome, worth);
        if (COMPENSATED_DISARMAMENT.equals(agenda)) return compensatedDisarmament(view.game(), view.voter(), outcome);
        return null;
    }

    private static double electedPlanetGain(Game game, Player voter, String agenda, String outcome, double worth) {
        Planet planet = game.getPlanetsInfo().get(outcome);
        if (planet == null || !AgendaPolicy.hasRequiredTrait(agenda, planet)) return 0;
        Player owner = BoardView.controller(game, outcome);
        if (owner == null || !game.getRealPlayers().contains(owner)) return 0;
        if (owner == voter) return worth;
        return -rivalGainCost(game, voter, owner, worth);
    }

    private static double compensatedDisarmament(Game game, Player voter, String outcome) {
        Planet planet = game.getPlanetsInfo().get(outcome);
        Player controller = BoardView.controller(game, outcome);
        if (planet == null || controller == null) return 0;
        double gain = disarmamentGain(game, planet, controller);
        return relativeToRivals(game, voter, player -> player == controller ? gain : 0);
    }

    private static double disarmamentGain(Game game, Planet planet, Player controller) {
        int infantry = BoardView.count(planet, controller, UnitType.Infantry);
        int mechs = BoardView.count(planet, controller, UnitType.Mech);
        if (infantry + mechs == 0) return 0;
        double tradeGoods = (infantry + mechs) * ValueScale.TRADE_GOOD;
        if (needsTradeGoods(game, controller)) return tradeGoods;
        return tradeGoods
                - infantry * INFANTRY_TG
                - mechs * MECH_TG
                - EXPOSED_PLANET_SHARE * BoardView.planetValue(planet);
    }

    private static boolean needsTradeGoods(Game game, Player seat) {
        Wallet wallet = Wallet.of(game, seat);
        return wallet.tradeGoods() * wallet.tradeGoodValue() + wallet.resources() < SPENDING_NEED;
    }
}
