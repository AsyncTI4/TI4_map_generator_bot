package ti4.commands.bothelper;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;

public class Observer extends BothelperSubcommandData {
    public Observer() {
        super(Constants.OBSERVER,"Add or remove observers to game channels");
        addOptions(new OptionData(OptionType.STRING, Constants.PBD_NUMBER, "The game number I.E. pbd###").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER,"Player @playername").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ADD_REMOVE,"Add or remove player as observer").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
       Guild guild = event.getGuild();
       
       // Check if game channels exist
       
        List<GuildChannel> channels = guild.getChannels();
        for(GuildChannel channel : channels) {
            if(channel.getName().contains(event.getOption(Constants.PBD_NUMBER, null, OptionMapping::getAsString))) {
                sendMessage("Found: " + channel.getName());
            }
       }
    }
}
