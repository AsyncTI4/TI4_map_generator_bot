package ti4.service.statistics.game;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.map.persistence.ManagedPlayer;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

@UtilityClass
public class CommunityStatisticsService {

    public static void queueReply(SlashCommandInteractionEvent event) {
        getCommunityStatistics(event);
    }

    private void getCommunityStatistics(SlashCommandInteractionEvent event) {
        try {
            List<ManagedGame> games = GameManager.getManagedGames();
            long activeGames = GameManager.getActiveGameCount();
            long allGames = games.size();

            List<ManagedPlayer> players = new ArrayList<>(games.stream()
                    .flatMap(g -> g.getPlayers().stream())
                    .collect(Collectors.toMap(p -> p.getId(), p -> p, (p, q) -> p))
                    .values());
            long activePlayers = players.stream()
                    .filter(p -> p.getGames().stream().anyMatch(g -> g.isActive()))
                    .count();
            long allPlayers = players.size();

            String output = "# Statistics:";
            output += "\n> Lifetime game count: " + allGames;
            output += "\n> Active game count: " + activeGames;
            output += "\n> Lifetime player count: " + allPlayers;
            output += "\n> Active player count: " + activePlayers;
            MessageHelper.sendMessageToChannel(event.getChannel(), output);
        } catch (Exception e) {
            BotLogger.error(event, "error", e);
        }
    }
}
