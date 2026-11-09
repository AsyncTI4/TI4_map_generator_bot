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

    public static final class Outlook {

        private final Game game;
        private final Player viewer;
        private final AgendaForecast forecast;
        private AgendaForecast withItsBallot;

        private Outlook(Game game, Player viewer) {
            this.game = game;
            this.viewer = viewer;
            forecast = AgendaForecast.of(game, viewer);
        }

        private AgendaForecast withoutDeal(Player seller) {
            if (viewer != seller) return forecast;
            if (withItsBallot == null) withItsBallot = AgendaVoting.forecastWithItsBallot(game, seller);
            return withItsBallot;
        }
    }

    public static Outlook outlook(Game game, Player viewer) {
        return new Outlook(game, viewer);
    }

    public static Optional<Terms> bestSale(Outlook outlook, Player seller, Player buyer) {
        return best(outlook, seller, buyer, Comparator.comparingDouble(Terms::surplus));
    }

    public static Optional<Terms> bestPurchase(Outlook outlook, Player buyer, Player seller) {
        return best(outlook, seller, buyer, Comparator.comparingDouble(Terms::buyerGain));
    }

    public static Optional<Terms> valued(Game game, Player viewer, Player seller, Player buyer, String outcome) {
        Outlook outlook = outlook(game, viewer);
        if (!votesForSale(game, outlook.forecast, seller)
                || !outlook.forecast.outcomes().contains(outcome)) {
            return Optional.empty();
        }
        return Optional.of(valued(game, outlook.forecast, outlook.withoutDeal(seller), seller, buyer, outcome));
    }

    private static Optional<Terms> best(Outlook outlook, Player seller, Player buyer, Comparator<Terms> preference) {
        Game game = outlook.game;
        AgendaForecast forecast = outlook.forecast;
        if (!votesForSale(game, forecast, seller)) return Optional.empty();
        AgendaForecast withoutDeal = outlook.withoutDeal(seller);
        return forecast.outcomes().stream()
                .map(outcome -> valued(game, forecast, withoutDeal, seller, buyer, outcome))
                .max(preference);
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
