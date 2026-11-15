package ti4.ai.agenda;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.AgendaModel;
import ti4.model.DeckModel;
import ti4.model.Mapper;

@UtilityClass
class CovertValues {

    static final String COVERT = "covert";

    @Nullable
    static Double valueOf(AgendaView view, String agenda, String outcome, String ownVote) {
        if (!COVERT.equals(agenda)) return null;
        List<String> candidates = view.readsHandOf(speaker(view.game()))
                ? heldCovertAgenda(view.game()).map(List::of).orElseGet(() -> possibleAgendas(view.game()))
                : possibleAgendas(view.game());
        OptionalDouble average = candidates.stream()
                .map(candidate ->
                        AgendaValues.of(view.game(), view.voter(), view.ownView(), true, candidate, outcome, ownVote))
                .filter(OptionalDouble::isPresent)
                .mapToDouble(OptionalDouble::getAsDouble)
                .average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    static String announcedTarget(Game game) {
        String[] parts = StringUtils.split(game.getCurrentAgendaInfo(), "_");
        return parts != null && parts.length > 1 ? parts[1] : "";
    }

    static List<String> possibleAgendas(Game game) {
        String target = announcedTarget(game);
        DeckModel deck = Mapper.getDeck(game.getAgendaDeckID());
        if (deck == null || target.isBlank()) return List.of();
        Set<String> gone = seen(game);
        return deck.getNewDeck().stream()
                .distinct()
                .filter(alias -> !COVERT.equals(alias) && !gone.contains(alias))
                .filter(alias -> hasTarget(alias, target))
                .sorted()
                .toList();
    }

    private static Set<String> seen(Game game) {
        Set<String> seen = new HashSet<>(game.getDiscardAgendas().keySet());
        seen.addAll(game.getLaws().keySet());
        return seen.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static boolean hasTarget(String alias, String target) {
        AgendaModel model = Mapper.getAgenda(alias);
        return model != null && target.equalsIgnoreCase(model.getTarget());
    }

    private static Optional<String> heldCovertAgenda(Game game) {
        String target = announcedTarget(game);
        List<String> held = game.getSentAgendas().keySet().stream()
                .filter(alias -> hasTarget(alias, target))
                .toList();
        return held.size() == 1 ? Optional.of(held.getFirst()) : Optional.empty();
    }

    @Nullable
    private static Player speaker(Game game) {
        return game.getPlayer(game.getSpeakerUserID());
    }
}
