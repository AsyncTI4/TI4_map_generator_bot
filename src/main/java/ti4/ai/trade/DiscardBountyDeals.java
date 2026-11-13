package ti4.ai.trade;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.AgendaCardRules;
import ti4.ai.agenda.AgendaForecast;
import ti4.ai.agenda.AgendaPolicy;
import ti4.ai.agenda.AgendaQueue;
import ti4.ai.agenda.AgendaVoting;
import ti4.ai.agenda.DiscardBounties;
import ti4.ai.agenda.DiscardBounties.Bounty;
import ti4.ai.agenda.PromisedVotes;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.ai.xxcha.QuashRules;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.Player;
import ti4.helpers.AgendaHistory;

@UtilityClass
class DiscardBountyDeals {

    private static final String VOTING_PHASE = "agendaVoting";
    private static final String VETO_TITLE = DiscardBounties.VETO;

    static Optional<AiDecision> offer(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!DiscardBounties.active(seat) || !AgendaQueue.whenWindowOpen(game)) return Optional.empty();
        String agenda = PromisedVotes.agendaKey(game);
        if (agenda.isBlank() || DiscardBounties.postedOnThisAgenda(game, seat).isPresent()) return Optional.empty();
        OptionalDouble loss = AgendaPolicy.expectedLoss(game, seat);
        if (loss.isEmpty()) return Optional.empty();
        int most = DiscardBounties.mostOffered(PromiseDeals.spendableTradeGoods(game, seat), loss.getAsDouble());
        if (most < 1) return Optional.empty();
        Optional<Player> quasher = quasher(game, seat);
        int veto = AgendaCardRules.holdsVeto(seat)
                ? 0
                : remaining(game, seat, most, DiscardBounties.VETO_CAP, Bounty::veto);
        int quash = quasher.map(xxcha -> quashShare(game, seat, xxcha, most)).orElse(0);
        if (veto <= 0 && quash <= 0) return Optional.empty();
        Optional<String> channel = PromiseDeals.channelId(context);
        if (channel.isEmpty()) return Optional.empty();
        Bounty bounty = new Bounty(
                game.getRound(),
                agenda,
                seat.getFaction(),
                veto,
                quash,
                game.getGameStats().getActionCardPlays().size(),
                quasher.map(Player::getFaction).orElse(""),
                quasher.map(Player::getStrategicCC).orElse(-1),
                DiscardBounties.Status.OPEN);
        DiscardBounties.persist(game, bounty);
        return Optional.of(new AiDecision.Post(channel.get(), offerText(game, seat, bounty), List.of()));
    }

    static void observe(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!DiscardBounties.active(seat)) return;
        for (Bounty bounty : DiscardBounties.all(game)) {
            if (bounty.round() != game.getRound()
                    || !bounty.postedBy(seat)
                    || bounty.status() != DiscardBounties.Status.OPEN) {
                continue;
            }
            settle(context, bounty);
        }
    }

    private static void settle(AiTurnContext context, Bounty bounty) {
        Game game = context.game();
        Player payer = context.seat();
        if (bounty.agenda().equals(PromisedVotes.agendaKey(game))) {
            if (VOTING_PHASE.equalsIgnoreCase(game.getPhaseOfGame())) {
                DiscardBounties.persist(game, bounty.with(DiscardBounties.Status.LAPSED));
            }
            return;
        }
        Optional<AgendaHistory.Entry> fate = AgendaHistory.of(game, bounty.round(), bounty.agenda());
        boolean resolved = fate.filter(AgendaHistory.Entry::resolved).isPresent();
        boolean quashed = fate.filter(AgendaHistory.Entry::quashed).isPresent();
        Optional<Claim> claim = resolved
                ? Optional.empty()
                : quashed ? quashClaim(game, payer, bounty) : vetoClaim(game, payer, bounty);
        if (claim.isEmpty()) {
            DiscardBounties.persist(game, bounty.with(DiscardBounties.Status.LAPSED));
            return;
        }
        DiscardBounties.persist(game, bounty.with(DiscardBounties.Status.CLAIMED));
        PromiseBook.persist(
                game,
                Promise.owed(
                        game,
                        Promise.Term.DISCARD_AGENDA,
                        claim.get().holder(),
                        payer,
                        bounty.agenda(),
                        claim.get().card(),
                        claim.get().amount(),
                        context.now()));
    }

    private record Claim(Player holder, String card, int amount) {}

    private static Optional<Claim> vetoClaim(Game game, Player payer, Bounty bounty) {
        if (bounty.veto() <= 0) return Optional.empty();
        List<GameStats.ActionCardPlay> plays = game.getGameStats().getActionCardPlays();
        for (int index = Math.max(0, bounty.plays()); index < plays.size(); index++) {
            GameStats.ActionCardPlay play = plays.get(index);
            if (!VETO_TITLE.equals(play.getActionCard()) || play.isCanceled()) continue;
            Optional<Player> holder = game.getRealPlayers().stream()
                    .filter(player -> player != payer)
                    .filter(player -> play.getPlayerId() != null
                            && play.getPlayerId().equals(GameStats.getTrackedPlayerId(player)))
                    .findFirst();
            if (holder.isPresent()) return Optional.of(new Claim(holder.get(), VETO_TITLE, bounty.veto()));
        }
        return Optional.empty();
    }

    private static Optional<Claim> quashClaim(Game game, Player payer, Bounty bounty) {
        if (bounty.quash() <= 0 || bounty.quasher().isBlank()) return Optional.empty();
        Player quasher = game.getPlayerFromColorOrFaction(bounty.quasher());
        if (quasher == null || quasher == payer) {
            return Optional.empty();
        }
        return Optional.of(new Claim(quasher, DiscardBounties.QUASH, bounty.quash()));
    }

    private static int remaining(
            Game game, Player payer, int most, int cap, java.util.function.ToIntFunction<Bounty> amount) {
        int pooled = DiscardBounties.pooledFromOthers(game, payer, amount);
        return Math.max(0, Math.min(most, cap - pooled));
    }

    private static int quashShare(Game game, Player payer, Player quasher, int most) {
        int offered = remaining(game, payer, most, DiscardBounties.QUASH_CAP, Bounty::quash);
        int pooled = DiscardBounties.pooledFromOthers(game, payer, Bounty::quash);
        AgendaForecast forecast = AgendaVoting.forecastWithItsBallot(game, payer);
        double quasherLoss = forecast.isEmpty() ? 0 : -forecast.expectedUtility(quasher);
        return pooled + offered >= QuashRules.bountyNeeded(quasher, quasherLoss) ? offered : 0;
    }

    private static Optional<Player> quasher(Game game, Player payer) {
        return game.getRealPlayers().stream()
                .filter(player -> player != payer)
                .filter(QuashRules::canQuash)
                .findFirst();
    }

    static String offerText(Game game, Player payer, Bounty bounty) {
        String name = payer.getFactionEmojiOrColor();
        StringBuilder text = new StringBuilder(name + " would rather this agenda never resolved.");
        if (bounty.veto() > 0) {
            text.append(" It pays ").append(bounty.veto()).append(" trade goods to whoever plays _Veto_ on it.");
        }
        Player quasher = game.getPlayerFromColorOrFaction(bounty.quasher());
        if (bounty.quash() > 0 && quasher != null) {
            text.append(" It pays ")
                    .append(bounty.quash())
                    .append(" trade goods to ")
                    .append(quasher.getFactionEmojiOrColor())
                    .append(" for a **Quash**.");
        }
        return text.append(" Nothing is paid unless the card is played.").toString();
    }
}
