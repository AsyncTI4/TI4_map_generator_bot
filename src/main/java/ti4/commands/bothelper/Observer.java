package ti4.commands.bothelper;

import java.util.List;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Player;

public class Observer extends BothelperSubcommandData {
    public Observer() {
        super(Constants.OBSERVER,"Add or remove observers to game channels");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game name I.E. pbd###-xxxxxx").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER,"Player @playername").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ADD_REMOVE,"add or remove player as observer").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
       Guild guild = event.getGuild();
       Member user = guild.getMemberById(event.getOption(Constants.PLAYER, null, OptionMapping::getAsString));
       // Check if game channels exist
       
        List<GuildChannel> channels = guild.getChannels();
        sendMessage("DEBUG: Playername: " + user.getNickname() + " Add/remove: " + event.getOption(Constants.ADD_REMOVE, null, OptionMapping::getAsString));
        for(GuildChannel channel : channels) {
            if(channel.getName().contains(event.getOption(Constants.GAME_NAME, null, OptionMapping::getAsString))) {
                sendMessage("Found channel match: " + channel.getName());
                if(event.getOption(Constants.ADD_REMOVE, null, OptionMapping::getAsString) == "add") {
                    channel.getPermissionContainer().upsertPermissionOverride((IPermissionHolder) user).grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND);
                    sendMessage("Permissions granted on " + event.getOption(Constants.PLAYER).getName() + " to channel " + channel.getName());
                }

                if(event.getOption(Constants.ADD_REMOVE, null, OptionMapping::getAsString) == "remove") {
                    channel.getPermissionContainer().upsertPermissionOverride((IPermissionHolder) user).deny(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND);
                    sendMessage("Permissions revoked on " + event.getOption(Constants.PLAYER).getName() + " to channel " + channel.getName());
                }
            }
        }
    }
}

