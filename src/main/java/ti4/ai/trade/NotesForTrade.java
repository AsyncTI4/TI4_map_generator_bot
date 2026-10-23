package ti4.ai.trade;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.SearchMemo;
import ti4.ai.promissory.NoteGiving;
import ti4.ai.strategy.CardFacade;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class NotesForTrade {

    private static final String RECEIVE_MEMO = "notes.receiveValue";
    private static final String GIVE_MEMO = "notes.giveCost";
    private static final String LEAST_HARMFUL_MEMO = "notes.leastHarmful";
    static final double OTHER_NOTE = 0.3;
    static final double MIN_NOTE_WORTH = 0.25;
    static final double UNKNOWN_NOTE_COST = 1.0;
    static final double INFINITE = Double.POSITIVE_INFINITY;
    static final String TRADE_AGREEMENT = "_ta";
    static final String CEASEFIRE = "_cf";
    static final String POLITICAL_SECRET = "_ps";
    static final int LAST_AGENDA_ROUND = 5;
    private static final double OWN_THRONE_BACK = 1.0;
    private static final String POLITICAL_FAVOR = "favor";
    private static final double FAVOR_PLAY_CHANCE = 0.25;
    private static final double TRADE_GOOD_PER_AGENDA = 1.0;
    private static final int AGREEMENT_DISCOUNT = 1;
    private static final double OWN_FACTION_NOTE_COST = 1.5;
    private static final double RETURN_DISCOUNT = 0.3;
    private static final Map<String, Double> NOTES_IT_PLAYS = Map.of(
            "ra", 1.5,
            "gift", 1.0,
            "tekklar", 1.0,
            "antivirus", 0.5,
            "greyfire", 0.5,
            "favor", 0.5);

    static Optional<String> aliasOf(Player sender, String detail) {
        if (!StringUtils.isNumeric(detail)) return Optional.of(detail.replace(DealItem.ALIAS_UNDERSCORE, "_"));
        int id = Integer.parseInt(detail);
        return sender.getPromissoryNotes().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() == id)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    static Optional<String> aliasOf(Game game, DealItem item) {
        if (item.type() != ItemType.PROMISSORY || item.isGenericNote()) return Optional.empty();
        Player sender = game.getPlayerFromColorOrFaction(item.sender());
        return sender == null ? Optional.empty() : aliasOf(sender, item.detail());
    }

    static String detailOf(String alias) {
        return alias.replace("_", DealItem.ALIAS_UNDERSCORE);
    }

    static double receiveValue(Game game, Player seat, String alias) {
        return receiveValue(game, seat, alias, false);
    }

    static double receiveValue(Game game, Player seat, String alias, boolean seatPrivate) {
        return SearchMemo.remember(
                () -> baseReceiveValue(game, seat, alias, seatPrivate) + NoteSecrets.value(game, seat, alias),
                RECEIVE_MEMO,
                seat,
                alias,
                seatPrivate);
    }

    private static double baseReceiveValue(Game game, Player seat, String alias, boolean seatPrivate) {
        Player owner = game.getPNOwner(alias);
        boolean own = Standings.same(owner, seat);
        if (alias.endsWith(NoteGiving.THRONE)) {
            return own ? ownThroneBack(game, seat, alias) : ThroneValue.received(game, seat, owner);
        }
        return Math.max(MIN_NOTE_WORTH, floorlessReceiveValue(game, seat, alias, owner, own, seatPrivate));
    }

    private static double floorlessReceiveValue(
            Game game, Player seat, String alias, Player owner, boolean own, boolean seatPrivate) {
        if (alias.endsWith(NoteGiving.ALLIANCE)) {
            return own ? AllianceValue.ownBack() : AllianceValue.received(game, seat, owner, seatPrivate);
        }
        if (alias.endsWith(TRADE_AGREEMENT)) return agreementValue(owner);
        if (alias.endsWith(CEASEFIRE)) {
            return own ? CeasefireValue.OWN_BACK : CeasefireValue.received(game, seat, owner);
        }
        if (alias.endsWith(POLITICAL_SECRET)) return politicalSecretValue(game);
        if (ConvoysValue.isConvoys(alias)) return own ? ConvoysValue.OWN_BACK : ConvoysValue.received(game, seat);
        if (MilitarySupport.ALIAS.equals(alias)) return own ? 0 : MilitarySupport.receivedBy(game, seat);
        return NOTES_IT_PLAYS.getOrDefault(alias, OTHER_NOTE);
    }

    static double giveCost(Game game, Player seat, Player receiver, String alias) {
        return SearchMemo.remember(
                () -> Math.max(MIN_NOTE_WORTH, computeGiveCost(game, seat, receiver, alias)),
                GIVE_MEMO,
                seat,
                receiver,
                alias);
    }

    private static double computeGiveCost(Game game, Player seat, Player receiver, String alias) {
        Player owner = game.getPNOwner(alias);
        if (!Standings.same(owner, seat)) {
            double value = receiveValue(game, seat, alias);
            return Standings.same(owner, receiver) ? Math.max(0, value - RETURN_DISCOUNT) : value;
        }
        if (alias.endsWith(NoteGiving.THRONE)) return ThroneValue.givenCost(game, seat, receiver);
        if (alias.endsWith(NoteGiving.ALLIANCE)) {
            return receiver.hasAbility(AllianceValue.HUBRIS) ? INFINITE : AllianceValue.givenCost(game, seat, receiver);
        }
        if (alias.endsWith(TRADE_AGREEMENT)) return agreementValue(seat);
        if (alias.endsWith(CEASEFIRE)) return CeasefireValue.givenCost(game, seat, receiver);
        if (alias.endsWith(POLITICAL_SECRET)) return politicalSecretValue(game);
        if (POLITICAL_FAVOR.equals(alias)) return politicalFavorCost(game);
        if (MilitarySupport.ALIAS.equals(alias)) return MilitarySupport.givenCost(seat);
        if (ConvoysValue.isConvoys(alias)) return ConvoysValue.GIVE_COST;
        return OWN_FACTION_NOTE_COST;
    }

    static Optional<String> leastHarmful(Game game, Player seat, Player receiver) {
        return SearchMemo.remember(() -> pickLeastHarmful(game, seat, receiver), LEAST_HARMFUL_MEMO, seat, receiver);
    }

    private static Optional<String> pickLeastHarmful(Game game, Player seat, Player receiver) {
        List<String> inPlayArea = seat.getPromissoryNotesInPlayArea();
        return seat.getPromissoryNotes().keySet().stream()
                .filter(alias -> !inPlayArea.contains(alias))
                .filter(alias -> !alias.endsWith(NoteGiving.THRONE))
                .filter(alias -> Double.isFinite(giveCost(game, seat, receiver, alias)))
                .min(Comparator.comparingDouble((String alias) -> giveCost(game, seat, receiver, alias))
                        .thenComparingInt(alias -> NoteGiving.giveRank(game, alias, receiver.getFaction()))
                        .thenComparing(Comparator.naturalOrder()));
    }

    static boolean inHand(Player seat, String alias) {
        return seat.getPromissoryNotes().containsKey(alias)
                && !seat.getPromissoryNotesInPlayArea().contains(alias);
    }

    static boolean grantsThrone(Game game, DealItem item, Player receiver) {
        return aliasOf(game, item)
                .filter(alias -> alias.endsWith(NoteGiving.THRONE))
                .filter(alias -> !Standings.same(game.getPNOwner(alias), receiver))
                .isPresent();
    }

    static double agreementValue(Player owner) {
        return owner == null ? OTHER_NOTE : Math.max(0, owner.getCommoditiesTotal() - AGREEMENT_DISCOUNT);
    }

    static double politicalSecretValue(Game game) {
        return TRADE_GOOD_PER_AGENDA * agendaPhasesLeft(game);
    }

    private static double politicalFavorCost(Game game) {
        return CardFacade.strategyToken() * FAVOR_PLAY_CHANCE * agendaPhasesLeft(game);
    }

    private static int agendaPhasesLeft(Game game) {
        int first = game.isCustodiansScored() ? game.getRound() : game.getRound() + 1;
        return Math.max(0, LAST_AGENDA_ROUND - first + 1);
    }

    private static double ownThroneBack(Game game, Player seat, String alias) {
        return Standings.others(game, seat).stream()
                .filter(holder -> holder.getPromissoryNotesInPlayArea().contains(alias))
                .findFirst()
                .map(holder ->
                        OWN_THRONE_BACK + Stinginess.rivalry(game, seat, holder) * Desperation.vpWorth(game, holder))
                .orElse(OWN_THRONE_BACK);
    }
}
