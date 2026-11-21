package ti4.ai.trade;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSeats;
import ti4.ai.agenda.AgendaVoting;
import ti4.ai.agenda.PromisedVotes;
import ti4.ai.agenda.VoteDeals;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class VoteTurnDeals {

    static final long OFFER_WAIT_MILLIS = 60_000L;
    private static final String TURN_SEEN_KEY = "voteTurnSeen|";
    private static final String PROPOSED_KEY = "voteTurnProposed|";

    static Optional<AiDecision> hold(AiTurnContext context, Supplier<List<Player>> partners) {
        Game game = context.game();
        Player seller = context.seat();
        if (!PromiseDeals.conditional(seller) || !context.isActivePlayer()) return Optional.empty();
        if (!VoteDeals.aboutToVote(game, seller) || AgendaVoting.castableVotes(game, seller) <= 0) {
            return Optional.empty();
        }
        String agenda = game.getCurrentAgendaInfo();
        if (!context.memory().has(PROPOSED_KEY + agenda)) {
            context.memory().put(PROPOSED_KEY + agenda, "1");
            Optional<AiDecision> proposal = PromiseDeals.proposeVotes(context, partners.get());
            if (proposal.isPresent()) return proposal;
        }
        Optional<Promise> proposed =
                ownOpenVoteOffer(game, seller).filter(promise -> promise.expiresAt() > context.now());
        if (proposed.isPresent()) {
            return Optional.of(new AiDecision.Wait(proposed.get().expiresAt(), "wait for an answer on its votes"));
        }
        long until = turnSeenAt(context) + OFFER_WAIT_MILLIS;
        if (context.now() >= until || !someBuyerMayOffer(game, seller)) return Optional.empty();
        return Optional.of(new AiDecision.Wait(until, "wait for offers for its votes"));
    }

    private static long turnSeenAt(AiTurnContext context) {
        String key = TURN_SEEN_KEY + context.game().getCurrentAgendaInfo();
        Optional<String> seen = context.memory().get(key).filter(StringUtils::isNumeric);
        if (seen.isPresent()) return Long.parseLong(seen.get());
        context.memory().put(key, String.valueOf(context.now()));
        return context.now();
    }

    private static Optional<Promise> ownOpenVoteOffer(Game game, Player seller) {
        return PromiseBook.all(game).stream()
                .filter(Promise::open)
                .filter(promise -> promise.term() == Promise.Term.VOTE_FOR)
                .filter(promise -> promise.proposer().equals(seller.getFaction()))
                .filter(promise -> PromisedVotes.onThisAgenda(game, promise))
                .findFirst();
    }

    private static boolean someBuyerMayOffer(Game game, Player seller) {
        return game.getRealPlayers().stream()
                .filter(buyer -> buyer != seller && AiSeats.isAiSeat(buyer))
                .filter(PromiseDeals::conditional)
                .filter(buyer -> buyer.getTg() >= 1)
                .filter(buyer -> TradeLegality.canTransact(game, seller, buyer))
                .anyMatch(buyer -> PromiseDeals.noVoteDealYet(game, buyer, seller));
    }
}
