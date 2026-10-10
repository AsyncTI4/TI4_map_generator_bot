package ti4.ai.promissory;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class NoteGiving {

    public static final String THRONE = "_sftt";
    public static final String ALLIANCE = "_an";
    private static final String OTHER = "";
    private static final List<String> NOTE_GIVE_ORDER = List.of("_cf", "_ta", "_ps", OTHER, ALLIANCE, THRONE);
    private static final int RETURNED_TO_OWNER = -1;
    private static final String OWED_KEY = "noteOwed|";

    public static int giveRank(Game game, String note, String receiverFaction) {
        Player owner = game.getPNOwner(note);
        if (owner != null && receiverFaction != null && receiverFaction.equals(owner.getFaction())) {
            return RETURNED_TO_OWNER;
        }
        for (int rank = 0; rank < NOTE_GIVE_ORDER.size(); rank++) {
            String suffix = NOTE_GIVE_ORDER.get(rank);
            if (!suffix.isEmpty() && note.endsWith(suffix)) return rank;
        }
        return NOTE_GIVE_ORDER.indexOf(OTHER);
    }

    public static boolean isThroneOrAlliance(String note) {
        return note.endsWith(THRONE) || note.endsWith(ALLIANCE);
    }

    public static Optional<String> leastHarmful(
            Game game, Player giver, Player receiver, boolean allowThroneOrAlliance) {
        List<String> inPlayArea = giver.getPromissoryNotesInPlayArea();
        return giver.getPromissoryNotes().keySet().stream()
                .filter(note -> !inPlayArea.contains(note))
                .filter(note -> allowThroneOrAlliance || !isThroneOrAlliance(note))
                .min(Comparator.comparingInt((String note) -> giveRank(game, note, receiver.getFaction()))
                        .thenComparing(Comparator.naturalOrder()));
    }

    public static void owe(AiTurnContext context, String receiverFaction, String alias) {
        context.memory().put(OWED_KEY + receiverFaction, alias);
    }

    public static Optional<String> owed(AiTurnContext context, String receiverFaction) {
        String key = OWED_KEY + receiverFaction;
        Optional<String> alias = context.memory().get(key);
        context.memory().remove(key);
        return alias;
    }
}
