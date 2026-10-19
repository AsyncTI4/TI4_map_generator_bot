package ti4.ai.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.promissory.NoteGiving;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class NoteDeals {

    static final int MAX_PER_ROUND = 2;
    static final int MAX_PRICE = 6;
    private static final String ROUND_KEY = "tradeNoteDeals|";
    private static final String PARTNER_KEY = "tradeNoteDealWith|";
    private static final String KEY_SEPARATOR = "|";
    private static final List<String> OWN_KINDS = List.of(
            NoteGiving.THRONE,
            NoteGiving.ALLIANCE,
            NotesForTrade.TRADE_AGREEMENT,
            NotesForTrade.POLITICAL_SECRET,
            NotesForTrade.CEASEFIRE);

    record Option(Player partner, Deal deal, double utility) {}

    static Optional<AiDecision> propose(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        String roundKey = ROUND_KEY + game.getRound();
        if (TradeMemory.count(context.memory(), roundKey) >= MAX_PER_ROUND) return Optional.empty();
        List<Player> eligible = partners.stream()
                .filter(partner -> OwnTurnDeals.mayApproach(context, partner))
                .filter(partner -> !context.memory().has(partnerKey(game, partner)))
                .toList();
        for (Option option : best(context, eligible)) {
            Optional<AiDecision> start =
                    OfferBuilder.start(context, option.partner(), option.deal(), Purpose.NOTE_DEAL);
            if (start.isEmpty()) continue;
            TradeMemory.increment(context.memory(), roundKey);
            if (asksForAChasedNote(game, context.seat(), option)) NoteSecrets.asked(context.memory(), option.partner());
            context.memory().put(partnerKey(game, option.partner()), String.valueOf(context.now()));
            return start;
        }
        return Optional.empty();
    }

    static List<Option> best(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        List<Option> options = new ArrayList<>();
        for (Player partner : partners) {
            double trust = Trust.of(context, partner);
            int spare = TradeBudget.spareTradeGoods(game, seat, partner);
            ToIntFunction<String> floor = alias -> NoteSecrets.chases(game, seat, alias)
                    ? NoteSecrets.askFloor(context.memory(), game, seat, partner, alias)
                    : 1;
            for (Deal deal : candidates(game, seat, partner, spare, floor)) {
                if (!TradeValue.coverable(game, seat, partner, deal)) continue;
                if (!TradeValue.proposable(game, seat, partner, deal, trust)) continue;
                options.add(new Option(
                        partner,
                        deal,
                        TradeValue.of(game, seat, partner, deal, trust).utility()));
            }
        }
        options.sort(Comparator.comparingDouble(Option::utility).reversed());
        return options;
    }

    static List<Deal> candidates(Game game, Player seat, Player partner, int spareTradeGoods) {
        return candidates(game, seat, partner, spareTradeGoods, alias -> 1);
    }

    static List<Deal> candidates(
            Game game, Player seat, Player partner, int spareTradeGoods, ToIntFunction<String> floor) {
        String ours = seat.getFaction();
        String theirs = partner.getFaction();
        List<DealItem> given = ownNotes(seat, partner).stream()
                .map(alias -> new DealItem(
                        ours,
                        theirs,
                        ItemType.PROMISSORY,
                        String.valueOf(seat.getPromissoryNotes().get(alias))))
                .toList();
        List<String> askedAliases = partnerNotes(game, seat, partner);
        List<DealItem> asked = askedAliases.stream()
                .map(alias -> new DealItem(theirs, ours, ItemType.PROMISSORY, NotesForTrade.detailOf(alias)))
                .toList();
        List<Deal> deals = new ArrayList<>();
        for (DealItem note : given) {
            for (int price = 1; price <= Math.min(MAX_PRICE, partner.getTg()); price++) {
                deals.add(Deal.EMPTY.with(note).adjust(theirs, ours, ItemType.TRADE_GOODS, price));
            }
        }
        for (int index = 0; index < asked.size(); index++) {
            String alias = askedAliases.get(index);
            int lowest = floor.applyAsInt(alias);
            int highest = Math.min(lowest > 1 ? NoteSecrets.MAX_PRICE : MAX_PRICE, spareTradeGoods);
            for (int price = lowest; price <= highest; price++) {
                deals.add(Deal.EMPTY.with(asked.get(index)).adjust(ours, theirs, ItemType.TRADE_GOODS, price));
            }
        }
        for (DealItem mine : given) {
            for (DealItem yours : asked) {
                Deal swap = Deal.EMPTY.with(mine).with(yours);
                deals.add(swap);
                if (partner.getTg() >= 1) deals.add(swap.adjust(theirs, ours, ItemType.TRADE_GOODS, 1));
                if (spareTradeGoods >= 1) deals.add(swap.adjust(ours, theirs, ItemType.TRADE_GOODS, 1));
            }
        }
        return deals;
    }

    private static boolean asksForAChasedNote(Game game, Player seat, Option option) {
        return option.deal().items().stream()
                .filter(item ->
                        item.type() == ItemType.PROMISSORY && item.receiver().equals(seat.getFaction()))
                .map(item -> NotesForTrade.aliasOf(game, item))
                .flatMap(Optional::stream)
                .anyMatch(alias -> NoteSecrets.chases(game, seat, alias));
    }

    private static List<String> ownNotes(Player seat, Player partner) {
        return seat.getPromissoryNotes().keySet().stream()
                .filter(seat::ownsPromissoryNote)
                .filter(alias -> NotesForTrade.inHand(seat, alias))
                .filter(alias -> OWN_KINDS.stream().anyMatch(alias::endsWith) || ConvoysValue.isConvoys(alias))
                .filter(alias -> !alias.endsWith(NoteGiving.ALLIANCE) || !partner.hasAbility(AllianceValue.HUBRIS))
                .sorted()
                .toList();
    }

    private static List<String> partnerNotes(Game game, Player seat, Player partner) {
        Stream<String> publicNotes = partner.getPromissoryNotesOwned().stream()
                .filter(alias -> alias.endsWith(NoteGiving.THRONE)
                        || (alias.endsWith(NoteGiving.ALLIANCE) && !seat.hasAbility(AllianceValue.HUBRIS)))
                .filter(alias -> !inAnyPlayArea(game, alias));
        Stream<String> ceasefire = CeasefireValue.underDuress(game, seat, partner)
                ? partner.getPromissoryNotesOwned().stream()
                        .filter(alias -> alias.endsWith(NotesForTrade.CEASEFIRE))
                        .filter(alias -> !seat.getPromissoryNotes().containsKey(alias))
                : Stream.empty();
        return Stream.concat(publicNotes, ceasefire).sorted().toList();
    }

    private static boolean inAnyPlayArea(Game game, String alias) {
        return Standings.players(game).stream()
                .anyMatch(player -> player.getPromissoryNotesInPlayArea().contains(alias));
    }

    private static String partnerKey(Game game, Player partner) {
        return PARTNER_KEY + game.getRound() + KEY_SEPARATOR + partner.getFaction();
    }
}
