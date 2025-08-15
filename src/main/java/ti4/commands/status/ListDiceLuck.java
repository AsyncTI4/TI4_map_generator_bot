package ti4.commands.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ListDiceLuck extends GameStateSubcommand {

    public ListDiceLuck() {
        super(Constants.DICE_LUCK, "List dice luck for this game", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (FoWHelper.isPrivateGame(event)) {
            MessageHelper.replyToMessage(event, "This command is not available in fog of war private channels.");
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("__Average dice luck in ").append(game.getName());
        if (!game.getCustomName().isEmpty()) {
            message.append(" - ").append(game.getCustomName());
        }
        message.append("__");

        HashMap<String, Double> record = new HashMap<>();
        for (Player player : game.getPlayers().values()) {
            if (!player.isRealPlayer()) continue;
            playerAverageDiceLuck(player, record);
        }
        var lines = new ArrayList<String>();
        for (Map.Entry<String, Double> entry : record.entrySet()) {
            lines.add(entry.getKey());
        }
        lines.sort((s1, s2) -> record.get(s2).compareTo(record.get(s1)));
        for (String s : lines) {
            message.append("\n").append(s);
        }

        MessageHelper.replyToMessage(event, message.toString());
    }

    private static void playerAverageDiceLuck(Player player, HashMap<String, Double> record) {
        double expectedHits = player.getExpectedHitsTimes10() / 10.0;
        int actualHits = player.getActualHits();
        if (expectedHits == 0) {
            record.put("> " + player.getUserName() + " has not rolled dice yet.", -1.0);
            return;
        }

        double total = actualHits / expectedHits;

        record.put(
                "> " + player.getUserName() + ": `" + String.format("%.2f", total)
                        + "` ("
                        + actualHits + "/" + String.format("%.1f", expectedHits) + " actual/expected)",
                total);
    }
}
