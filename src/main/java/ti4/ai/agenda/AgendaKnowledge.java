package ti4.ai.agenda;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.AgendaModel;
import ti4.model.Mapper;

@UtilityClass
public class AgendaKnowledge {

    static final String KEY = "aiAgendaKnowledge";
    private static final String FIELD = "~";
    private static final String CARD = "+";
    private static final int FIELDS = 3;
    private static final String NAALU_COMMANDER = "naalucommander";

    private record Known(int round, int deckSize, List<String> top) {

        String encode() {
            return round + FIELD + deckSize + FIELD + String.join(CARD, top);
        }

        static Optional<Known> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null || fields.length != FIELDS) return Optional.empty();
            if (!StringUtils.isNumeric(fields[0]) || !StringUtils.isNumeric(fields[1])) return Optional.empty();
            List<String> top = Arrays.stream(StringUtils.split(fields[2], CARD)).toList();
            return Optional.of(new Known(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]), top));
        }
    }

    public static void placedOnTop(Game game, Player seat, String alias) {
        List<String> top = new ArrayList<>(recordedTop(game, seat));
        top.remove(alias);
        top.addFirst(alias);
        seat.setStoredValue(KEY, new Known(game.getRound(), deckSize(game), top).encode());
    }

    public static void placedOnBottom(Game game, Player seat, String alias) {
        List<String> top = new ArrayList<>(recordedTop(game, seat));
        if (!top.remove(alias)) return;
        seat.setStoredValue(KEY, new Known(game.getRound(), deckSize(game), top).encode());
    }

    public static List<String> knownTop(Game game, Player seat) {
        List<String> recorded = recordedTop(game, seat);
        if (!seesTheTop(game, seat)) return recorded;
        Optional<String> top = topOutsideHands(game);
        if (top.isEmpty() || recorded.contains(top.get())) return recorded;
        List<String> known = new ArrayList<>();
        known.add(top.get());
        known.addAll(recorded);
        return List.copyOf(known);
    }

    private static boolean seesTheTop(Game game, Player seat) {
        return game.playerHasLeaderUnlockedOrAlliance(seat, NAALU_COMMANDER);
    }

    private static Optional<String> topOutsideHands(Game game) {
        List<String> deck = game.getAgendas();
        if (deck == null) return Optional.empty();
        for (int index = 0; index < deck.size(); index++) {
            String alias = game.lookAtTopAgenda(index);
            if (!game.getSentAgendas().containsKey(alias)) return Optional.of(alias);
        }
        return Optional.empty();
    }

    private static List<String> recordedTop(Game game, Player seat) {
        Optional<Known> known = Known.decode(seat.getStoredValue(KEY));
        if (known.isEmpty() || known.get().round() != game.getRound()) return List.of();
        int drawn = known.get().deckSize() - deckSize(game);
        if (drawn < 0) return List.of();
        List<String> top = known.get().top();
        return drawn >= top.size() ? List.of() : List.copyOf(top.subList(drawn, top.size()));
    }

    public static Optional<String> next(Game game, Player seat) {
        return knownTop(game, seat).stream().findFirst();
    }

    static Optional<String> covertCard(Game game, Player speaker, String announcedTarget) {
        if (speaker == null || !speaker.getUserID().equals(game.getSpeakerUserID())) return Optional.empty();
        List<String> held = game.getSentAgendas().keySet().stream()
                .filter(alias -> hasTarget(alias, announcedTarget))
                .toList();
        return held.size() == 1 ? Optional.of(held.getFirst()) : Optional.empty();
    }

    static boolean hasTarget(String alias, String target) {
        AgendaModel model = Mapper.getAgenda(alias);
        return model != null && target.equalsIgnoreCase(model.getTarget());
    }

    private static int deckSize(Game game) {
        List<String> deck = game.getAgendas();
        return deck == null ? 0 : deck.size();
    }
}
