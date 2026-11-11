package ti4.ai.strategy;

import java.util.OptionalInt;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.CustodiansRace.Snapshot;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class InitiativeRace {

    static double gain(Game game, Player seat, int initiative) {
        Snapshot race = PlanInputs.of(game, seat, PlanMoment.PICK).race();
        if (!race.isOpen() || seat.hasTheZeroToken() || !race.stillPicking(seat)) return 0;
        OptionalInt arrival = race.arrival(seat);
        if (arrival.isEmpty() || arrival.getAsInt() != game.getRound()) return 0;
        int latest = race.openCards(initiative).stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(initiative);
        int actingAt = actingAt(seat, initiative);
        int actingLast = actingAt(seat, latest);
        if (actingLast <= actingAt) return 0;
        double acting = race.raceValueActingAt(seat, arrival, actingAt);
        double last = race.raceValueActingAt(seat, arrival, actingLast);
        return Math.max(0, acting - last);
    }

    private static int actingAt(Player seat, int initiative) {
        return seat.getSCs().isEmpty() ? initiative : Math.min(seat.getLowestSC(), initiative);
    }
}
