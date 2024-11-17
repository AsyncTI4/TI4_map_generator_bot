package ti4.commands.special;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.CheckDistanceHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

class CheckAllDistance extends GameStateSubcommand {

    public CheckAllDistance() {
        super(Constants.CHECK_ALL_DISTANCE, "Check All Distance", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.MAX_DISTANCE, "Max distance to check"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        int maxDistance = event.getOption(Constants.MAX_DISTANCE, 10, OptionMapping::getAsInt);

        List<String> data = new ArrayList<>();
        StringBuilder sb = new StringBuilder("Distances");
        Game game = getGame();
        List<String> positions = game.getTileMap().values().stream()//.filter(t -> t.getHyperlaneData(0) == null)
            .map(Tile::getPosition)
            .sorted().toList();
        for (String pos : positions) {
            sb.append(",").append(pos);
        }
        data.add(sb.toString());

        Player player = getPlayer();
        for (String pos : positions) {
            Map<String, Integer> distances = CheckDistanceHelper.getTileDistances(game, player, pos, maxDistance, true);
            String row = distances.entrySet().stream()
                .filter(dist -> positions.contains(dist.getKey()))
                .sorted(Entry.comparingByKey())
                .map(entry -> entry.getValue() == null ? "99" : Integer.toString(entry.getValue()))
                .reduce(pos, (a, b) -> a + "," + b);
            data.add(row);
        }

        File csv = new File("distances.csv");
        try (PrintWriter pw = new PrintWriter(csv)) {
            for (String s : data) {
                pw.print(s);
                pw.println();
            }
            MessageHelper.sendFileToChannel(event.getChannel(), csv);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Something broke. Ping jazz");
        }
        if (!game.isFowMode()) {
            sb.append("Map String: `").append(game.getMapString()).append("`").append("\n");
        } else {
            sb.append("Map String: Cannot show map string for private games").append("\n");
        }
    }
}
