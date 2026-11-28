package ti4.ai.promise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.StringHelper;
import ti4.model.Mapper;
import ti4.model.PromissoryNoteModel;

public record PromisePayment(int tradeGoods, int debt, List<String> notes) {

    private static final String KEY = "aiPromisePayment|";
    private static final String FIELD = "~";
    private static final String NOTE = "+";

    public PromisePayment {
        notes = List.copyOf(notes);
    }

    public static PromisePayment tradeGoods(int amount) {
        return new PromisePayment(amount, 0, List.of());
    }

    public static PromisePayment of(Game game, Promise promise) {
        return decode(game.getStoredValue(KEY + promise.id())).orElse(tradeGoods(promise.price()));
    }

    public static void attach(Game game, String promiseId, PromisePayment payment) {
        if (payment.onlyTradeGoods()) return;
        game.setStoredValue(KEY + promiseId, payment.encode());
    }

    public boolean onlyTradeGoods() {
        return debt == 0 && notes.isEmpty();
    }

    public boolean affordable(Player payer) {
        return payer.getTg() >= tradeGoods
                && notes.stream()
                        .allMatch(note -> payer.getPromissoryNotes().containsKey(note)
                                && !payer.getPromissoryNotesInPlayArea().contains(note));
    }

    public String describe() {
        List<String> parts = new ArrayList<>();
        if (tradeGoods > 0 || onlyTradeGoods()) parts.add(StringHelper.pluralize(tradeGoods, "trade good"));
        if (debt > 0) parts.add(StringHelper.pluralize(debt, "debt token"));
        notes.forEach(note -> parts.add(noteName(note)));
        if (parts.size() == 1) return parts.getFirst();
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
    }

    public String label() {
        List<String> parts = new ArrayList<>();
        if (tradeGoods > 0 || onlyTradeGoods()) parts.add(tradeGoods + " TG");
        if (debt > 0) parts.add(debt + " debt");
        notes.forEach(note -> parts.add(noteName(note)));
        return String.join(" + ", parts);
    }

    private static String noteName(String alias) {
        PromissoryNoteModel note = Mapper.getPromissoryNote(alias);
        return note == null ? alias : note.getName();
    }

    String encode() {
        return tradeGoods + FIELD + debt + FIELD + String.join(NOTE, notes);
    }

    static Optional<PromisePayment> decode(String encoded) {
        if (StringUtils.isBlank(encoded)) return Optional.empty();
        String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
        if (fields.length != 3 || !StringUtils.isNumeric(fields[0]) || !StringUtils.isNumeric(fields[1])) {
            return Optional.empty();
        }
        List<String> notes = Arrays.stream(StringUtils.split(fields[2], NOTE)).toList();
        return Optional.of(new PromisePayment(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]), notes));
    }
}
