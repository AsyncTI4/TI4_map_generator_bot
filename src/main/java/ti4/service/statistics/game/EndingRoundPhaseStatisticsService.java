package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
class EndingRoundPhaseStatisticsService {

    static void showEndingRoundPhaseStatistics(SlashCommandInteractionEvent event) {
        Map<String, Integer> endingRoundAndPhaseCount = new HashMap<>();

        GamesPage.consumeAllGames(GameStatisticsFilterer.getGamesFilter(event), game -> {
            if (!game.isHasEnded()) return;

            String phase =
                    game.getPhaseOfGame() == null || game.getPhaseOfGame().isBlank()
                            ? "unknown"
                            : game.getPhaseOfGame();
            if (phase.contains("status")) {
                phase = "status";
            } else if (phase.contains("agenda")) {
                phase = "agenda";
            } else if (phase.contains("action")) {
                phase = "action";
            } else {
                phase = "unknown";
            }

            String endingRoundAndPhase = "Round " + game.getRound() + " - " + phase;
            endingRoundAndPhaseCount.merge(endingRoundAndPhase, 1, Integer::sum);
        });

        int totalEndedGames = endingRoundAndPhaseCount.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        AtomicInteger index = new AtomicInteger();
        StringBuilder sb = new StringBuilder("__**Game Endings by Round and Phase:**__\n");

        endingRoundAndPhaseCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> sb.append(index.incrementAndGet())
                        .append(". `")
                        .append(entry.getValue())
                        .append(" (")
                        .append(Math.round(100 * entry.getValue() / (double) totalEndedGames))
                        .append("%)` ")
                        .append(entry.getKey())
                        .append('\n'));

        if (totalEndedGames == 0) {
            sb.append("No ended games found for the selected filters.\n");
        }

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Game Ending Rounds", sb.toString());
    }
}
