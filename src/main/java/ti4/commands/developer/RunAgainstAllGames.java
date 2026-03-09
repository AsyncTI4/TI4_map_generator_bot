package ti4.commands.developer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

class RunAgainstAllGames extends Subcommand {

    private static final long ONE_SECOND_MILLIS = 1000L;

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        Set<String> changedGames = new HashSet<>();
        GamesPage.consumeAllGames(game -> {
            boolean changed = makeChanges(game);
            if (changed) {
                changedGames.add(game.getName());
                GameManager.save(game, "Developer ran custom command against this game, probably migration related.");
            }
        });

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    private static boolean makeChanges(Game game) {
        Map<String, String> replacements = Map.of(
            "disarmamament", "disarmament",
            "absol_disarmamament", "absol_disarmament",
            "cryypter_disarmamament", "cryypter_disarmament",
            "minister_commrece", "minister_commerce",
            "senate_sancuary", "senate_sanctuary");

        return replaceAgendaCards(game, List.of(game.getAgendaDeckID()), replacements);
    }

    private static boolean replaceAgendaCards(Game game, List<String> decksToCheck, Map<String, String> replacements) {
        if (!decksToCheck.contains(game.getAgendaDeckID())) {
            return false;
        }

        boolean mapNeededMigrating = false;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String toReplace = entry.getKey();
            String replacement = entry.getValue();

            mapNeededMigrating |= replace(game.getAgendas(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getDiscardAgendas(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getSentAgendas(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getLaws(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getLawsInfo(), toReplace, replacement);
        }
        return mapNeededMigrating;
    }

    private static <K, V> boolean replaceKey(Map<K, V> map, K toReplace, K replacement) {
        if (map.containsKey(toReplace)) {
            V value = map.get(toReplace);
            map.put(replacement, value);
            map.remove(toReplace);
            return true;
        }
        return false;
    }

    private static <K> boolean replace(List<K> list, K toReplace, K replacement) {
        boolean replaced = false;
        int index = list.indexOf(toReplace);
        while (index > -1) {
            list.set(index, replacement);
            replaced = true;
            index = list.indexOf(toReplace);
        }
        return replaced;
    }
}
