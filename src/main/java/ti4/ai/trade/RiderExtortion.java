package ti4.ai.trade;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.agenda.AgendaForecast;
import ti4.ai.agenda.AgendaVoting;
import ti4.ai.agenda.VoteDeals;
import ti4.ai.promise.Promise;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class RiderExtortion {

    static final double RIDER_SHARE = 0.5;
    private static final String ABSTAIN = "";

    record Stake(double holderGain, double chance) {}

    static boolean active(Player seat) {
        return PromiseDeals.conditional(seat) && AiExperiments.enabled(seat, Experiment.RIDER_EXTORTION);
    }

    static Optional<PromiseDeals.Proposal> offer(Game game, Player seat, Player holder, Predicate<String> backed) {
        if (!active(seat) || !VoteDeals.aboutToVote(game, seat)) return Optional.empty();
        AgendaVoting.PlannedBallot planned = AgendaVoting.itsBallot(game, seat);
        String opposed = planned.outcome();
        if (opposed.isBlank()) return Optional.empty();
        AgendaForecast untouched = AgendaForecast.of(game, seat);
        return untouched.ridersOf(holder).stream()
                .filter(rider -> !rider.outcome().equalsIgnoreCase(opposed) && backed.test(rider.outcome()))
                .max(Comparator.comparingDouble(AgendaForecast.PlacedRider::payoff))
                .flatMap(rider -> proposal(game, seat, holder, planned, untouched.without(seat), rider));
    }

    private static Optional<PromiseDeals.Proposal> proposal(
            Game game,
            Player seat,
            Player holder,
            AgendaVoting.PlannedBallot planned,
            AgendaForecast abstaining,
            AgendaForecast.PlacedRider rider) {
        double sellerCost = planned.value(seat) - abstaining.expectedUtility(seat, ABSTAIN);
        double holderGain =
                abstaining.expectedUtility(holder) - planned.forecast().expectedUtility(holder);
        double chance = abstaining.chance(rider.outcome());
        int cap = cap(game, seat, holder, rider);
        if (cap <= 0) return Optional.empty();
        return PromiseDeals.sellerPrice(holderGain, sellerCost, holder, holder.getTg(), cap, chance)
                .map(price -> new PromiseDeals.Proposal(
                        Promise.Term.NOT_AGAINST,
                        seat,
                        holder,
                        rider.outcome(),
                        0,
                        price,
                        chance * price - sellerCost));
    }

    static int cap(Game game, Player holder, AgendaForecast.PlacedRider rider) {
        return cap(game, holder, holder, rider);
    }

    static int cap(Game game, Player seat, Player holder, AgendaForecast.PlacedRider rider) {
        int share = (int) Math.floor(rider.payoff() * RIDER_SHARE);
        int prediction = PromiseDeals.predictionCap(game, seat, holder, rider.outcome());
        return prediction > 0 ? Math.min(share, prediction) : share;
    }

    static Optional<Stake> toHolder(Game game, Player holder, Player promiser, String outcome) {
        AgendaForecast forecast = AgendaForecast.of(game, holder);
        int votes = AgendaVoting.castableVotes(game, promiser);
        if (forecast.isEmpty() || votes <= 0 || forecast.votesLeft(promiser) <= 0) return Optional.empty();
        Optional<String> threat = forecast.outcomes().stream()
                .filter(other -> !other.equalsIgnoreCase(outcome))
                .max(Comparator.comparingDouble(other -> forecast.utility(promiser, other)));
        if (threat.isEmpty()) return Optional.empty();
        AgendaForecast threatened = forecast.withVotes(promiser, threat.get(), votes);
        AgendaForecast abstaining = forecast.without(promiser);
        return Optional.of(new Stake(
                abstaining.expectedUtility(holder) - threatened.expectedUtility(holder), abstaining.chance(outcome)));
    }
}
