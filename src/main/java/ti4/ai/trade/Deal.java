package ti4.ai.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import ti4.game.Player;

record Deal(List<DealItem> items) {

    static final Deal EMPTY = new Deal(List.of());
    private static final String ITEM_SEPARATOR = ";";
    private static final String FINGERPRINT_SEPARATOR = ",";
    private static final String SUMMARY_PARTIES = ">";
    private static final String SUMMARY_FIELDS = ":";

    Deal {
        items = List.copyOf(items);
    }

    static Deal between(Player holder, Player other) {
        String holderFaction = holder.getFaction();
        String otherFaction = other.getFaction();
        boolean cardsAllowed = TradeActionCards.tradable(holder, other);
        return new Deal(holder.getTransactionItemsWithPlayer(other).stream()
                .map(raw -> DealItem.parse(raw, holderFaction, otherFaction))
                .flatMap(Optional::stream)
                .map(item -> cardsAllowed || item.type() != ItemType.ACTION_CARDS ? item : item.unsupported())
                .toList());
    }

    boolean isEmpty() {
        return items.isEmpty();
    }

    int total(String sender, ItemType type) {
        return items.stream()
                .filter(item -> item.isFrom(sender) && item.type() == type)
                .mapToInt(DealItem::amount)
                .sum();
    }

    int fragments(String sender, String kind) {
        return items.stream()
                .filter(item -> item.isFrom(sender) && item.type() == ItemType.FRAGMENTS)
                .filter(item -> item.fragmentKind().equals(kind))
                .mapToInt(DealItem::amount)
                .sum();
    }

    int received(String receiver, ItemType type) {
        return items.stream()
                .filter(item -> item.isTo(receiver) && item.type() == type)
                .mapToInt(DealItem::amount)
                .sum();
    }

    List<String> notes(String sender) {
        return named(sender, ItemType.PROMISSORY);
    }

    List<String> named(String sender, ItemType type) {
        return items.stream()
                .filter(item -> item.isFrom(sender) && item.type() == type)
                .map(DealItem::detail)
                .toList();
    }

    boolean has(ItemType type) {
        return items.stream().anyMatch(item -> item.type() == type);
    }

    Deal withNotesNamedAs(String sender, Deal target) {
        List<String> named = target.notes(sender);
        List<DealItem> aligned = new ArrayList<>();
        int next = 0;
        for (DealItem item : items) {
            boolean note = item.isFrom(sender) && item.type() == ItemType.PROMISSORY;
            if (note && next < named.size()) {
                aligned.add(new DealItem(item.sender(), item.receiver(), item.type(), named.get(next++)));
            } else {
                aligned.add(item);
            }
        }
        return new Deal(aligned);
    }

    boolean isDebtOnly() {
        return items.stream().allMatch(item -> item.type().isDebt());
    }

    boolean hasUnsupported() {
        return items.stream().anyMatch(item -> item.type() == ItemType.UNSUPPORTED);
    }

    boolean hasDetails() {
        return items.stream().anyMatch(item -> item.type() == ItemType.DETAILS);
    }

    Deal withoutUnsupported() {
        return new Deal(items.stream()
                .filter(item -> item.type() != ItemType.UNSUPPORTED && item.type() != ItemType.DETAILS)
                .toList());
    }

    Deal withoutNotes(String sender) {
        return new Deal(items.stream()
                .filter(item -> !(item.isFrom(sender) && item.type() == ItemType.PROMISSORY))
                .toList());
    }

    Deal without(Predicate<DealItem> unwanted) {
        return new Deal(items.stream().filter(unwanted.negate()).toList());
    }

    boolean fitsWithin(Deal target) {
        Map<String, Integer> limits = target.summary();
        return summary().entrySet().stream()
                .allMatch(entry -> entry.getValue() <= limits.getOrDefault(entry.getKey(), 0));
    }

    Deal with(DealItem item) {
        List<DealItem> extended = new ArrayList<>(items);
        extended.add(item);
        return new Deal(extended);
    }

    Deal adjust(String sender, String receiver, ItemType type, int delta) {
        if (!type.countable()) throw new IllegalArgumentException("Only amounts can be adjusted, not " + type);
        int amount = total(sender, type) + delta;
        List<DealItem> kept = new ArrayList<>(items.stream()
                .filter(item -> !(item.isFrom(sender) && item.type() == type))
                .toList());
        if (amount > 0) kept.add(new DealItem(sender, receiver, type, String.valueOf(amount)));
        return new Deal(kept);
    }

    int netTradeGoodsTo(String faction) {
        return received(faction, ItemType.TRADE_GOODS)
                + received(faction, ItemType.COMMODITIES)
                - total(faction, ItemType.TRADE_GOODS);
    }

    String fingerprint() {
        return items.stream().map(DealItem::engineString).sorted().collect(Collectors.joining(FINGERPRINT_SEPARATOR));
    }

    String encode() {
        return items.stream().map(DealItem::encode).collect(Collectors.joining(ITEM_SEPARATOR));
    }

    static Optional<Deal> decode(String encoded) {
        if (encoded == null) return Optional.empty();
        if (encoded.isEmpty()) return Optional.of(EMPTY);
        List<DealItem> decoded = new ArrayList<>();
        for (String part : encoded.split(ITEM_SEPARATOR)) {
            Optional<DealItem> item = DealItem.decode(part);
            if (item.isEmpty()) return Optional.empty();
            decoded.add(item.get());
        }
        return Optional.of(new Deal(decoded));
    }

    boolean sameAs(Deal other) {
        return summary().equals(other.summary());
    }

    private Map<String, Integer> summary() {
        Map<String, Integer> summary = new TreeMap<>();
        for (DealItem item : items) summary.merge(summaryKey(item), summaryAmount(item), Integer::sum);
        summary.values().removeIf(amount -> amount == 0);
        return summary;
    }

    private static String summaryKey(DealItem item) {
        String parties = item.sender() + SUMMARY_PARTIES + item.receiver() + SUMMARY_FIELDS + item.type();
        return switch (item.type()) {
            case TRADE_GOODS, COMMODITIES, SEND_DEBT, CLEAR_DEBT -> parties;
            case FRAGMENTS -> parties + SUMMARY_FIELDS + item.fragmentKind();
            case PROMISSORY, ACTION_CARDS, PLANETS, DETAILS, UNSUPPORTED -> parties + SUMMARY_FIELDS + item.detail();
        };
    }

    private static int summaryAmount(DealItem item) {
        return switch (item.type()) {
            case PROMISSORY, ACTION_CARDS, PLANETS, DETAILS, UNSUPPORTED -> 1;
            default -> item.amount();
        };
    }
}
