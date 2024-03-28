package ti4.commands.bothelper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.special.CheckDistance;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class JazzCommand extends BothelperSubcommandData {
    public JazzCommand() {
        super("jazz_command", "jazzxhands");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!"228999251328368640".equals(event.getUser().getId())) {
            String jazz = AsyncTI4DiscordBot.jda.getUserById("228999251328368640").getAsMention();
            if ("150809002974904321".equals(event.getUser().getId())) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are an honorary jazz so you may proceed");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are not " + jazz);
                return;
            }
        }

        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        int maxDistance = 10;
        StringBuilder sb = new StringBuilder("x");
        List<String> positions = game.getTileMap().values().stream().map(Tile::getPosition).sorted().toList();
        for (String pos : positions) {
            sb.append(" ").append(pos);
        }
        
        for (String pos : positions) {
            Map<String, Integer> distances = CheckDistance.getTileDistances(game, player, pos, maxDistance);
            sb.append("\n");
            String row = distances.entrySet().stream()
                .sorted(Comparator.comparing(Entry::getKey))
                .map(entry -> entry.getValue() == null ? "100" : Integer.toString(entry.getValue()))
                .reduce(pos, (a, b) -> a + " " + b);
            sb.append(row);
        }
        sendMessage(sb.toString());
    }
}
