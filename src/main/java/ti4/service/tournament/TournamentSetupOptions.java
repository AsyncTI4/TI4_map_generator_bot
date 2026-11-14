package ti4.service.tournament;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.game.Game;

@UtilityClass
public class TournamentSetupOptions {

    private static final List<TournamentSetupOption> OPTIONS = List.of();

    public static List<TournamentSetupOption> all() {
        return OPTIONS;
    }

    public static Optional<TournamentSetupOption> find(String id) {
        return OPTIONS.stream().filter(option -> option.id().equals(id)).findFirst();
    }

    public static String currentChoice(TournamentSetupOption option, Map<String, String> values) {
        String stored = values.get(option.id());
        return stored != null && option.choices().contains(stored)
                ? stored
                : option.choices().getFirst();
    }

    public static String nextChoice(TournamentSetupOption option, String current) {
        List<String> choices = option.choices();
        return choices.get((choices.indexOf(current) + 1) % choices.size());
    }

    public static List<String> applyAll(Game game, Map<String, String> values) {
        return apply(game, values, OPTIONS);
    }

    static List<String> apply(Game game, Map<String, String> values, List<TournamentSetupOption> options) {
        List<String> applied = new ArrayList<>();
        for (TournamentSetupOption option : options) {
            String choice = currentChoice(option, values);
            option.apply(game, choice);
            applied.add(option.label() + ": " + choice);
        }
        return applied;
    }
}
