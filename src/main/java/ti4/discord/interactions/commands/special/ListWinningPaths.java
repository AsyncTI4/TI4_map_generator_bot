package ti4.discord.interactions.commands.special;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.statistics.game.WinningPathPersistenceService;

class ListWinningPaths extends Subcommand {

    private static final String PLAYER_COUNT = "player_count";
    private static final String VICTORY_POINTS = "victory_points";

    ListWinningPaths() {
        super(Constants.LIST_WINNING_PATHS, "List every winning path with how many games it won");
        addOptions(new OptionData(OptionType.INTEGER, PLAYER_COUNT, "Number of players in the game")
                .setRequired(true)
                .setMinValue(1));
        addOptions(new OptionData(OptionType.INTEGER, VICTORY_POINTS, "Victory points needed to win")
                .setRequired(true)
                .setMinValue(1));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int playerCount = event.getOption(PLAYER_COUNT).getAsInt();
        int victoryPoints = event.getOption(VICTORY_POINTS).getAsInt();
        Map<String, Integer> counts = WinningPathPersistenceService.getWinningPathCounts(playerCount, victoryPoints);

        StringBuilder sb = new StringBuilder("__**Winning Paths:**__ ")
                .append(playerCount)
                .append(" player, ")
                .append(victoryPoints)
                .append(" victory points\n");
        if (counts.isEmpty()) {
            sb.append("No recorded games match.");
        } else {
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            sb.append(total).append(" games across ").append(counts.size()).append(" paths\n");
            counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> sb.append("- **")
                            .append(entry.getValue())
                            .append("**: ")
                            .append(entry.getKey())
                            .append('\n'));
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Winning Paths", sb.toString());
    }
}
