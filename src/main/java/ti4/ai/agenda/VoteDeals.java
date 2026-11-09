package ti4.ai.agenda;

import java.util.Comparator;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class VoteDeals {

    public record Terms(String outcome, int votes, double buyerGain, double sellerCost) {

        public double surplus() {
            return buyerGain - sellerCost;
        }
    }

    public static Optional<Terms> bestSale(Game game, Player viewer, Player seller, Player buyer) {
        return best(game, viewer, seller, buyer, Comparator.comparingDouble(Terms::surplus));
    }

    public static Optional<Terms> bestPurchase(Game game, Player viewer, Player buyer, Player seller) {
        return best(game, viewer, seller, buyer, Comparator.comparingDouble(Terms::buyerGain));
    }

    public static Optional<Terms> valued(Game game, Player viewer, Player seller, Player buyer, String outcome) {
        AgendaForecast forecast = AgendaForecast.of(game, viewer);
        if (!votesForSale(game, forecast, seller) || !forecast.outcomes().contains(outcome)) return Optional.empty();
        return Optional.of(valued(game, forecast, withoutDeal(game, viewer, forecast, seller), seller, buyer, outcome));
    }

    private static Optional<Terms> best(
            Game game, Player viewer, Player seller, Player buyer, Comparator<Terms> preference) {
        AgendaForecast forecast = AgendaForecast.of(game, viewer);
        if (!votesForSale(game, forecast, seller)) return Optional.empty();
        AgendaForecast withoutDeal = withoutDeal(game, viewer, forecast, seller);
        return forecast.outcomes().stream()
                .map(outcome -> valued(game, forecast, withoutDeal, seller, buyer, outcome))
                .max(preference);
    }

    private static AgendaForecast withoutDeal(Game game, Player viewer, AgendaForecast forecast, Player seller) {
        return viewer == seller ? AgendaVoting.forecastWithItsBallot(game, seller) : forecast;
    }

    private static boolean votesForSale(Game game, AgendaForecast forecast, Player seller) {
        return !forecast.isEmpty()
                && forecast.votesLeft(seller) > 0
                && AgendaVoting.castableVotes(game, seller) > 0
                && !PromisedVotes.hasVoted(game, seller);
    }

    private static Terms valued(
            Game game,
            AgendaForecast forecast,
            AgendaForecast withoutDeal,
            Player seller,
            Player buyer,
            String outcome) {
        int votes = AgendaVoting.castableVotes(game, seller);
        AgendaForecast withDeal = forecast.withVotes(seller, outcome, votes);
        double buyerGain = withDeal.expectedUtility(buyer) - withoutDeal.expectedUtility(buyer);
        double sellerCost = withoutDeal.expectedUtility(seller) - withDeal.expectedUtility(seller);
        return new Terms(outcome, votes, buyerGain, sellerCost);
    }
}
