package ti4.commands.bothelper;

import java.util.Date;
import java.util.Map;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public class ListDeadGames extends BothelperSubcommandData {
    public ListDeadGames() {
        super(Constants.LIST_DEAD_GAMES, "List games that haven't moved in 2+ months but still have channels");
    }

    public void execute(SlashCommandInteractionEvent event) {

        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        StringBuilder sb2 = new StringBuilder("Dead Roless\n");
        StringBuilder sb = new StringBuilder("Dead Channels\n");
        for (Game game : mapList.values()) {
            long milliSinceLastTurnChange = new Date().getTime()
                - game.getLastActivePlayerChange().getTime();
            if (game.isHasEnded() || milliSinceLastTurnChange > 5259600000f) {
                if (game.getActionsChannel() != null && !game.getActionsChannel().getParentCategory().getName().toLowerCase().contains("limbo")) {
                    sb.append(game.getActionsChannel().getJumpUrl() + "\n");
                }
                if (game.getTableTalkChannel() != null && !game.getTableTalkChannel().getParentCategory().getName().toLowerCase().contains("limbo")) {
                    sb.append(game.getTableTalkChannel().getJumpUrl() + "\n");
                }
                Guild guild = game.getGuild();
                if (guild != null) {
                    for (Role role : guild.getRoles()) {
                        if (game.getName().equals(role.getName().toLowerCase())) {
                            sb2.append(role.getAsMention() + "\n");
                        }
                    }
                }
            }

        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        MessageHelper.sendMessageToChannel(event.getChannel(), sb2.toString());
    }

}
