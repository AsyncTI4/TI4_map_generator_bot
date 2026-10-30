package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.model.PromissoryNoteModel;

/**
 * What one self-play game did with its promissory notes. Plays come from the engine's play event; every other change of
 * holder (a note moving from one seat's hand into another seat's hand or play area) is read from the game state between
 * ticks and counted as a transfer, which in self-play means a trade. A note that returns to its owner because it was
 * played is not a transfer.
 */
final class PromissoryTracker {

    // A note changing hands: from the seat that held it to the seat that now holds it, with the note's owner.
    record Transfer(int round, String note, String owner, String from, String to) {
        boolean givenByOwner() {
            return owner.equals(from);
        }
    }

    // A note played by the seat holding it; own is true for a seat's own note (some notes are played by their owner).
    record Play(int round, String note, String owner, String player) {
        boolean own() {
            return owner.equals(player);
        }
    }

    // ownedNotes: every note a seat brought, as "owner|note name". neverLeftOwner: those still in the owner's hand at
    // the
    // end that were never given away or played. receivedUnplayed: notes sitting in another seat's hand at the end, as
    // "holder|note name"; a note face up in a play area (Support for the Throne, Alliance) is in effect, not unplayed.
    record Summary(
            List<Transfer> transfers,
            List<Play> plays,
            List<String> ownedNotes,
            List<String> neverLeftOwner,
            List<String> receivedUnplayed) {

        static final Summary NOTHING = new Summary(List.of(), List.of(), List.of(), List.of(), List.of());

        long givenByOwners() {
            return transfers.stream()
                    .filter(Transfer::givenByOwner)
                    .map(transfer -> transfer.owner() + "|" + transfer.note())
                    .distinct()
                    .count();
        }

        long playedByOthers() {
            return plays.stream().filter(play -> !play.own()).count();
        }
    }

    private static final String HAND = "hand:";
    private static final String AREA = "area:";

    private final Map<String, String> holders = new HashMap<>();
    private final Set<String> playedSinceLastLook = new HashSet<>();
    private final Set<String> everMoved = new HashSet<>();
    private final List<Transfer> transfers = new ArrayList<>();
    private final List<Play> plays = new ArrayList<>();

    synchronized void played(Game game, Player player, String note) {
        if (game == null || player == null || note == null) return;
        Player owner = game.getPNOwner(note);
        plays.add(new Play(game.getRound(), name(note), owner == null ? "" : owner.getFaction(), player.getFaction()));
        playedSinceLastLook.add(note + "|" + player.getFaction());
        everMoved.add(note);
    }

    synchronized void observe(Game game) {
        Map<String, String> now = holdersOf(game);
        for (Map.Entry<String, String> entry : now.entrySet()) {
            String note = entry.getKey();
            String before = holders.get(note);
            if (before == null || !before.startsWith(HAND) || before.equals(entry.getValue())) continue;
            String from = before.substring(HAND.length());
            String to = entry.getValue().substring(entry.getValue().indexOf(':') + 1);
            if (from.equals(to) || playedSinceLastLook.contains(note + "|" + from)) continue;
            Player owner = game.getPNOwner(note);
            transfers.add(new Transfer(game.getRound(), name(note), owner == null ? "" : owner.getFaction(), from, to));
            everMoved.add(note);
        }
        holders.clear();
        holders.putAll(now);
        playedSinceLastLook.clear();
    }

    synchronized Summary summary(Game game) {
        if (game == null)
            return new Summary(List.copyOf(transfers), List.copyOf(plays), List.of(), List.of(), List.of());
        observe(game);
        List<String> owned = new ArrayList<>();
        List<String> neverLeft = new ArrayList<>();
        List<String> receivedUnplayed = new ArrayList<>();
        for (Player seat : game.getRealPlayers()) {
            for (String note : seat.getPromissoryNotesOwned()) {
                owned.add(seat.getFaction() + "|" + name(note));
                if (!everMoved.contains(note) && seat.getPromissoryNotes().containsKey(note)) {
                    neverLeft.add(seat.getFaction() + "|" + name(note));
                }
            }
            for (String note : seat.getPromissoryNotes().keySet()) {
                if (seat.ownsPromissoryNote(note)
                        || seat.getPromissoryNotesInPlayArea().contains(note)) continue;
                receivedUnplayed.add(seat.getFaction() + "|" + name(note));
            }
        }
        return new Summary(
                List.copyOf(transfers),
                List.copyOf(plays),
                List.copyOf(owned),
                List.copyOf(neverLeft),
                List.copyOf(receivedUnplayed));
    }

    private static Map<String, String> holdersOf(Game game) {
        Map<String, String> now = new HashMap<>();
        for (Player seat : game.getRealPlayers()) {
            for (String note : seat.getPromissoryNotes().keySet()) now.put(note, HAND + seat.getFaction());
            for (String note : seat.getPromissoryNotesInPlayArea()) now.put(note, AREA + seat.getFaction());
        }
        return now;
    }

    // Colour variants of a generic note (Support for the Throne, Ceasefire, ...) share one name.
    static String name(String note) {
        PromissoryNoteModel model = Mapper.getPromissoryNote(note);
        return model == null ? note : model.getName();
    }
}
