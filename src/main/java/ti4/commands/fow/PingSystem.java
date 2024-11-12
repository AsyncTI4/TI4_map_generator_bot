package ti4.commands.fow;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.PlayerGameStateSubcommand;
import ti4.generator.PositionMapper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PingSystem extends PlayerGameStateSubcommand {

    public PingSystem() {
        super(Constants.PING_SYSTEM, "Alert players adjacent to a system with a message.", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile position on map").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.MESSAGE, "Message to send").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping positionMapping = event.getOption(Constants.POSITION);
        if (positionMapping == null) {
            MessageHelper.replyToMessage(event, "Specify position");
            return;
        }

        OptionMapping messageMapping = event.getOption(Constants.MESSAGE);
        String message = messageMapping == null ? "" : messageMapping.getAsString();

        String position = positionMapping.getAsString().toLowerCase();
        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.replyToMessage(event, "Tile position is not allowed");
            return;
        }

        //get players adjacent
        Game game = getGame();
        List<Player> players = FoWHelper.getAdjacentPlayers(game, position, true);
        List<Player> failList = new ArrayList<>();
        int successfulCount = 0;
        for (Player player_ : players) {
            String playerMessage = player_.getRepresentationUnfogged() + " - System " + position + " has been pinged:\n> " + message;
            boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, game, playerMessage);
            if (success) {
                successfulCount++;
            } else {
                failList.add(player_);
            }
        }

        if (successfulCount < players.size()) {
            StringBuilder sb = new StringBuilder();
            for (Player p : failList) {
                sb.append(p.getUserName()).append(" ");
            }
            MessageHelper.replyToMessage(event, "One or more pings failed to send. Please follow up with game's GM. Failed for: " + sb);
        } else {
            MessageHelper.replyToMessage(event, "Successfully sent all pings.");
        }
    }
}
