package ti4.commands.bothelper;

import java.util.Date;
import java.util.Map;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public class ListDeadGames extends BothelperSubcommandData {
    public ListDeadGames() {
        super(Constants.LIST_DEAD_GAMES, "List games that haven't moved in 2+ months but still have channels");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Delete with with DELETE, otherwise warning").setRequired(true));
    }

    public void execute(SlashCommandInteractionEvent event) {

        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        OptionMapping option = event.getOption(Constants.CONFIRM);
        boolean delete = "DELETE".equals(option.getAsString());
        StringBuilder sb2 = new StringBuilder("Dead Roles\n");
        StringBuilder sb = new StringBuilder("Dead Channels\n");
        int channelCount = 0;
        int roleCount = 0;
        for (Game game : mapList.values()) {
            if (Helper.getDateDifference(game.getCreationDate(), Helper.getDateRepresentation(new Date().getTime())) < 30 || !game.getName().contains("pbd") || game.getName().contains("test")) {
                continue;
            }
            if (game.getName().contains("pbd1000") || game.getName().contains("pbd2863") || game.getName().contains("pbd3000") || game.getName().equalsIgnoreCase("pbd104") || game.getName().equalsIgnoreCase("pbd100") || game.getName().equalsIgnoreCase("pbd100two")) {
                continue;
            }
            long milliSinceLastTurnChange = new Date().getTime()
                - game.getLastActivePlayerChange().getTime();

            if (game.isHasEnded() && game.getEndedDate() < game.getLastActivePlayerChange().getTime() && milliSinceLastTurnChange < 1259600000l) {
                continue;
            }
            boolean warned = false;
            if (game.isHasEnded() || milliSinceLastTurnChange > 5259600000l) {
                if (game.getActionsChannel() != null && !game.getActionsChannel().getName().equalsIgnoreCase(game.getName() + "-actions")) {
                    continue;
                }

                if (game.getActionsChannel() != null && AsyncTI4DiscordBot.getAvailablePBDCategories().contains(game.getActionsChannel().getParentCategory()) && game.getActionsChannel().getParentCategory() != null && !game.getActionsChannel().getParentCategory().getName().toLowerCase().contains("limbo")) {
                    sb.append(game.getActionsChannel().getJumpUrl() + "\n");
                    channelCount++;
                    if (delete) {
                        game.getActionsChannel().delete().queue();
                    } else {
                        warned = true;
                        MessageHelper.sendMessageToChannel(game.getActionsChannel(), game.getPing() + " this is a warning that this game will be cleaned up tomorrow, unless someone takes a turn. You can ignore this if you want it deleted. Ping fin if this should not be done. ");
                    }
                }
                if (game.getTableTalkChannel() != null && AsyncTI4DiscordBot.getAvailablePBDCategories().contains(game.getTableTalkChannel().getParentCategory()) && !game.getTableTalkChannel().getParentCategory().getName().toLowerCase().contains("limbo")) {
                    if (game.getTableTalkChannel().getName().contains(game.getName() + "-")) {
                        sb.append(game.getTableTalkChannel().getJumpUrl() + "\n");
                        channelCount++;
                        if (delete) {
                            game.getTableTalkChannel().delete().queue();
                        } else if (!warned) {
                            MessageHelper.sendMessageToChannel(game.getActionsChannel(), game.getPing() + " this is a warning that this game will be cleaned up tomorrow, unless someone takes a turn. You can ignore this if you want it deleted. Ping fin if this should not be done. ");
                        }
                    }
                }
                Guild guild = game.getGuild();
                if (!AsyncTI4DiscordBot.guilds.contains(guild)) {
                    continue;
                }
                if (guild != null) {
                    Role r = null;
                    for (Role role : guild.getRoles()) {
                        if (game.getName().equals(role.getName().toLowerCase())) {
                            sb2.append(role.getName() + "\n");
                            r = role;
                            roleCount++;
                            break;
                        }
                    }
                    if (r != null && delete) {
                        r.delete().queue();
                    }
                }
            }

        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString() + "Channel Count = " + channelCount);
        MessageHelper.sendMessageToChannel(event.getChannel(), sb2.toString() + "Role Count =" + roleCount);
    }

}
