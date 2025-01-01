package ti4.commands.bothelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.MessageHelper;

class ServerGameStats extends Subcommand {

    public ServerGameStats() {
        super(Constants.SERVER_GAME_STATS, "Game Statistics for Administration");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_HUB, "Include the HUB server in these stats"));
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> skipGuilds = new ArrayList<>();
        skipGuilds.add("847560709730730064"); //CPTI
        skipGuilds.add("1062139934745559160"); //FoW

        boolean includeHub = event.getOption(Constants.INCLUDE_HUB, false, OptionMapping::getAsBoolean);
        if (!includeHub) skipGuilds.add(Constants.ASYNCTI4_HUB_SERVER_ID);

        int hostedGames = 0;
        int roomForGames = 0;

        List<Guild> guilds = AsyncTI4DiscordBot.guilds.stream()
            .filter(g -> !skipGuilds.contains(g.getId()))
            .sorted(Comparator.comparing(Guild::getIdLong)) // Sort by creation date
            .toList();

        Map<String, Integer> guildToGameCount = new HashMap<>();

        for (Guild guild : guilds) {
            guildToGameCount.putIfAbsent(guild.getId(), 0);
        }

        GameManager.getManagedGames().stream()
            .map(ManagedGame::getMainGameChannel)
            .filter(Objects::nonNull)
            .filter(channel -> channel.getParentCategory() != null && !channel.getParentCategory().getName().equals("The in-limbo PBD Archive"))
            .forEach(channel -> guildToGameCount.merge(channel.getGuild().getId(), 1, Integer::sum));

        StringBuilder sb = new StringBuilder();
        sb.append("## __Server Game Statistics__\n");
        for (Guild guild : guilds) {
            sb.append("**").append(guild.getName()).append("**\n");
            int roleCount = guild.getRoles().size(); //250
            int guildRoomForGames = 250 - roleCount;
            int channelCount = guild.getChannels().size(); //500
            guildRoomForGames = Math.min(guildRoomForGames, (500 - channelCount) / 2);
            int gameCount = guildToGameCount.get(guild.getId());
            sb.append("> hosting **").append(gameCount).append("** games  -  ");
            sb.append("space for **").append(guildRoomForGames).append("** more games\n");
            hostedGames += gameCount;
            roomForGames += guildRoomForGames;
        }
        sb.append("\n**Total**\n");
        sb.append("> hosting **").append(hostedGames).append("** games  -  ");
        sb.append("space for **").append(roomForGames).append("** more games\n");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }
}
