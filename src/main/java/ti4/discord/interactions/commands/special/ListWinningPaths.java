package ti4.discord.interactions.commands.special;

import java.util.HashMap;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.statistics.game.WinningPathHelper;

class ListWinningPaths extends Subcommand {

    ListWinningPaths() {
        super(Constants.LIST_WINNING_PATHS, "List every winning path with how many games it won");
        addOptions(GameStatisticsFilterer.gameStatsFiltersExcept(
                GameStatisticsFilterer.HAS_WINNER_FILTER,
                GameStatisticsFilterer.MIN_PLAYER_COUNT_FILTER,
                GameStatisticsFilterer.FRACTURE_IN_PLAY_FILTER));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map<String, Integer> counts = new HashMap<>();
        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event),
                game -> game.getWinner().ifPresent(winner -> {
                    String path = WinningPathHelper.buildWinningPath(game, winner);
                    counts.merge(path, 1, Integer::sum);
                }),
                ExecutionLockType.READ);

        StringBuilder sb = new StringBuilder("__**Winning Paths**__\n");
        if (counts.isEmpty()) {
            sb.append("No games match the selected filters.");
        } else {
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            sb.append(total).append(" games across ").append(counts.size()).append(" paths\n");
            counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> sb.append("- **")
                            .append(entry.getValue())
                            .append("** (")
                            .append(formatPercent(entry.getValue(), total))
                            .append("): ")
                            .append(entry.getKey())
                            .append('\n'));
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Winning Paths", sb.toString());
    }

    private static String formatPercent(int count, int total) {
        return String.format("%.1f%%", 100.0 * count / total);
    }
}
