package ti4.commands.bothelper;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public class GameStats extends BothelperSubcommandData {
    public GameStats(){
        super(Constants.SERVER_GAME_STATS, "Game Statistics for Administration");
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> skipGuilds = new ArrayList<>();
        skipGuilds.add("847560709730730064"); //CPTI
        skipGuilds.add("1062139934745559160"); //FoW
        
        int hostedGames = 0;
        int roomForGames = 0;
        
        StringBuilder sb = new StringBuilder();
        sb.append("## __Server Game Statistics__\n");
        for (Guild guild : AsyncTI4DiscordBot.guilds) {
            if (skipGuilds.contains(guild.getId())) continue;

            sb.append("**").append(guild.getName()).append("**\n");
            int roleCount = guild.getRoles().size(); //250
            int guildRoomForGames = 250 - roleCount;
            int channelCount = guild.getChannels().size(); //500
            guildRoomForGames = Math.min(guildRoomForGames, (500 - channelCount)/2);
            long gameCount = GameManager.getInstance().getGameNameToGame().values().stream().filter(g -> g.getGuild() != null && g.getGuild().getId().equals(guild.getId())).count();
            sb.append("- hosting **").append(gameCount).append("** games  -  ");
            sb.append("space for **").append(guildRoomForGames).append("** more games\n");
            hostedGames += gameCount;
            roomForGames += guildRoomForGames;
        }
        sb.append("**Total**\n");
        sb.append("- hosting **").append(hostedGames).append("** games  -  ");
        sb.append("space for **").append(roomForGames).append("** more games\n");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }
}
