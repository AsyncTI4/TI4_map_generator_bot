package ti4.ai.strategy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import ti4.ai.AiExperiments;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.trade.TradeRules;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;

@UtilityClass
public class SpeakerSale {

    public record Offer(Player buyer, int price) {}

    public static final String BUY_PREFIX = "speakerBuy_";
    public static final String DECLINE_PREFIX = "speakerNo_";
    static final int BASE_PRICE = 2;
    static final int PROMISED_PICK_POSITION = 1;
    private static final String SEPARATOR = "_";

    public static Optional<AiDecision> sell(AiTurnContext context, AiPrompt prompt, Map<Player, PromptButton> choices) {
        Game game = context.game();
        if (!AiExperiments.enabled(context.seat(), AiExperiments.Experiment.SPEAKER_SALE)
                || game.hasAnyPriorityTrackMode()) {
            return Optional.empty();
        }
        Optional<SpeakerPromise> current =
                SpeakerPromise.read(game).filter(promise -> promise.soldThisRound(game, context.faction()));
        if (current.isPresent()) return progress(context, prompt, choices, current.get());
        return offer(context, prompt, choices);
    }

    public static Optional<Offer> offerFor(Game game, Player seller) {
        Player buyer = rightNeighbour(game, seller);
        if (buyer == null || ScoringStakes.contender(game, seller, buyer)) return Optional.empty();
        double gain = buyerGain(game, buyer, seller);
        int price = price(game, seller, buyer, gain);
        if (aheadLate(game, seller, buyer) && price <= BASE_PRICE) return Optional.empty();
        if (buyer.getTg() < price || gain < price) return Optional.empty();
        if (!isSpeaker(game, seller) && price * CardValueScale.TRADE_GOOD <= agendaPerks(game)) return Optional.empty();
        return Optional.of(new Offer(buyer, price));
    }

    public static boolean buyerAccepts(Game game, Player buyer, Player seller, int price) {
        return AiExperiments.enabled(buyer, AiExperiments.Experiment.SPEAKER_SALE)
                && buyer.getTg() >= price
                && !ScoringStakes.contender(game, buyer, seller)
                && buyerGain(game, buyer, seller) >= price;
    }

    static double buyerGain(Game game, Player buyer, Player seller) {
        int seats = game.getRealPlayers().size();
        int position = Helper.getSpeakerOrderFromThisPlayer(seller, game).indexOf(buyer);
        if (seats < 2 || position < 0) return 0;
        int positionsGained = Math.max(0, position - PROMISED_PICK_POSITION);
        double perks = Math.max(0, SpeakerTokenValue.of(game, buyer) - CardValueScale.SPEAKER);
        return positionsGained * pickPositionValue(seats) + perks;
    }

    static double pickPositionValue(int seats) {
        double averagePositionsFromFirst = (seats - 1) / 2.0;
        return CardValueScale.SPEAKER / averagePositionsFromFirst;
    }

    static int price(Game game, Player seller, Player buyer, double buyerGain) {
        return aheadLate(game, seller, buyer) ? (int) Math.floor(buyerGain) : BASE_PRICE;
    }

    public static boolean aheadLate(Game game, Player seller, Player buyer) {
        return ScoringStakes.lateGame(game) && buyer.getTotalVictoryPoints() > seller.getTotalVictoryPoints();
    }

    static Player rightNeighbour(Game game, Player seat) {
        List<Player> order = Helper.getSpeakerOrderFromThisPlayer(seat, game);
        Player last = order.isEmpty() ? null : order.getLast();
        return last == seat ? null : last;
    }

    public static Optional<AiDecision> answer(AiTurnContext context) {
        List<AiPrompt> prompts = Prompts.newestFirst(context.prompts());
        Optional<Match> buy = Prompts.owned(prompts, context.faction(), id -> id.startsWith(BUY_PREFIX));
        if (buy.isEmpty()
                || context.alreadyPressed(buy.get().prompt(), buy.get().button())) return Optional.empty();
        Game game = context.game();
        Optional<SpeakerPromise> open = SpeakerPromise.read(game)
                .filter(promise -> promise.status() == SpeakerPromise.Status.OFFERED)
                .filter(promise -> promise.buyer().equals(context.faction()));
        Player seller = open.map(promise -> game.getPlayerFromColorOrFaction(promise.seller()))
                .orElse(null);
        if (seller != null
                && buyerAccepts(game, context.seat(), seller, open.get().price())) {
            return Optional.of(buy.get()
                    .press("buy the speaker token for " + open.get().price()
                            + " trade goods and leave the seller first pick"));
        }
        return Prompts.owned(prompts, context.faction(), id -> id.startsWith(DECLINE_PREFIX))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()))
                .map(match -> match.press("decline the speaker sale"));
    }

    public static Optional<AiDecision> declare(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<SpeakerPromise> promise = SpeakerPromise.read(game)
                .filter(candidate -> candidate.seller().equals(context.faction()))
                .filter(candidate -> candidate.bindsThisStrategyPhase(game))
                .filter(candidate -> !candidate.declared());
        if (promise.isEmpty() || !AiExperiments.enabled(seat, AiExperiments.Experiment.SPEAKER_SALE)) {
            return Optional.empty();
        }
        Optional<Integer> wanted = wantedCard(game, seat);
        if (wanted.isEmpty()) return Optional.empty();
        promise.get().withWanted(wanted.get()).persist(game);
        Player buyer = game.getPlayerFromColorOrFaction(promise.get().buyer());
        TextChannel channel = seat.getCorrectChannel();
        if (buyer == null || channel == null) return Optional.empty();
        return Optional.of(
                new AiDecision.Post(channel.getId(), declaration(game, seat, buyer, wanted.get()), List.of()));
    }

    public static Optional<AiDecision> awaitSellerPick(AiTurnContext context) {
        Optional<SpeakerPromise> promise = bindingOnBuyer(context).filter(candidate -> !candidate.declared());
        if (promise.isEmpty()) return Optional.empty();
        Player seller = context.game().getPlayerFromColorOrFaction(promise.get().seller());
        if (seller == null) return Optional.empty();
        long until = Prompts.turnStart(context) + TradeRules.ownTurnReplyMillis(seller);
        if (context.now() >= until) return Optional.empty();
        return Optional.of(new AiDecision.Wait(until, "the speaker sale: the seller names its strategy card first"));
    }

    public static List<PromptButton> withoutPromisedCard(AiTurnContext context, List<PromptButton> picks) {
        Optional<SpeakerPromise> promise = bindingOnBuyer(context).filter(SpeakerPromise::declared);
        if (promise.isEmpty()) return picks;
        List<PromptButton> kept = picks.stream()
                .filter(button ->
                        StrategyCardRanking.initiative(button) != promise.get().wanted())
                .toList();
        return kept.isEmpty() ? picks : kept;
    }

    static String buyId(Player buyer, int round, Player seller) {
        return ComponentIdEnvelope.ownedBy(buyer.getFaction()) + BUY_PREFIX + suffix(round, seller);
    }

    static String declineId(Player buyer, int round, Player seller) {
        return ComponentIdEnvelope.ownedBy(buyer.getFaction()) + DECLINE_PREFIX + suffix(round, seller);
    }

    static String suffix(int round, Player seller) {
        return round + SEPARATOR + seller.getFaction();
    }

    private static Optional<SpeakerPromise> bindingOnBuyer(AiTurnContext context) {
        Game game = context.game();
        if (!AiExperiments.enabled(context.seat(), AiExperiments.Experiment.SPEAKER_SALE)) return Optional.empty();
        return SpeakerPromise.read(game)
                .filter(promise -> promise.buyer().equals(context.faction()))
                .filter(promise -> promise.bindsThisStrategyPhase(game));
    }

    private static Optional<AiDecision> progress(
            AiTurnContext context, AiPrompt prompt, Map<Player, PromptButton> choices, SpeakerPromise promise) {
        Game game = context.game();
        Player buyer = game.getPlayerFromColorOrFaction(promise.buyer());
        return switch (promise.status()) {
            case ACCEPTED ->
                Optional.ofNullable(buyer == null ? null : choices.get(buyer))
                        .map(button -> AiDecision.press(
                                prompt,
                                button,
                                "give the speaker token to " + promise.buyer() + ", who paid " + promise.price()
                                        + " trade goods and lets me pick first"));
            case OFFERED -> {
                if (context.now() < promise.expiresAt()) {
                    yield Optional.of(new AiDecision.Wait(promise.expiresAt(), "an answer to the speaker sale"));
                }
                promise.with(SpeakerPromise.Status.EXPIRED).persist(game);
                yield Optional.empty();
            }
            case DECLINED, EXPIRED -> Optional.empty();
        };
    }

    private static Optional<AiDecision> offer(
            AiTurnContext context, AiPrompt prompt, Map<Player, PromptButton> choices) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<Offer> offer = offerFor(game, seat)
                .filter(candidate -> choices.containsKey(candidate.buyer()))
                .filter(candidate -> TradeRules.canTransact(game, seat, candidate.buyer()));
        if (offer.isEmpty()) return Optional.empty();
        Player buyer = offer.get().buyer();
        long expiresAt = context.now() + TradeRules.ownTurnReplyMillis(buyer);
        new SpeakerPromise(
                        seat.getFaction(),
                        buyer.getFaction(),
                        game.getRound(),
                        offer.get().price(),
                        SpeakerPromise.Status.OFFERED,
                        context.now(),
                        expiresAt,
                        SpeakerPromise.NOT_DECLARED)
                .persist(game);
        return Optional.of(new AiDecision.Post(
                prompt.channelId(),
                offerText(seat, buyer, offer.get().price()),
                offerButtons(game, seat, buyer, offer.get().price())));
    }

    private static String offerText(Player seller, Player buyer, int price) {
        String sellerName = seller.getFactionEmojiOrColor();
        return buyer.getRepresentationUnfogged() + " " + sellerName + " is resolving Politics and offers you the"
                + " speaker token for " + price + " trade goods. The deal: you become the speaker now. In the next"
                + " strategy phase you pick first, but " + sellerName + " names the strategy card it wants when the"
                + " phase starts and you leave that card for it.";
    }

    private static List<Button> offerButtons(Game game, Player seller, Player buyer, int price) {
        int round = game.getRound();
        return List.of(
                Buttons.green(buyId(buyer, round, seller), "Buy speaker for " + price + " TG"),
                Buttons.gray(declineId(buyer, round, seller), "No thanks"));
    }

    private static Optional<Integer> wantedCard(Game game, Player seat) {
        Set<Integer> taken = game.getRealPlayers().stream()
                .flatMap(player -> player.getSCs().stream())
                .collect(Collectors.toSet());
        return game.getSCList().stream()
                .filter(initiative -> !taken.contains(initiative))
                .max(Comparator.comparingDouble(
                                (Integer initiative) -> StrategyCardRanking.value(game, seat, initiative))
                        .thenComparing(Comparator.reverseOrder()));
    }

    private static String declaration(Game game, Player seller, Player buyer, int initiative) {
        return buyer.getRepresentationUnfogged() + " as agreed when you bought the speaker token, "
                + seller.getFactionEmojiOrColor() + " wants **" + cardName(game, initiative)
                + "** this strategy phase. Please pick any other card.";
    }

    private static String cardName(Game game, int initiative) {
        String name = Helper.getSCName(initiative, game);
        return name == null || name.isBlank() ? String.valueOf(initiative) : name;
    }

    private static boolean isSpeaker(Game game, Player seat) {
        return seat.getUserID().equals(game.getSpeakerUserID());
    }

    private static double agendaPerks(Game game) {
        return game.isCustodiansScored() ? CardValueScale.AGENDA_PEEK : 0;
    }
}
