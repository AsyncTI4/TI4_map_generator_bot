package ti4.commands.bothelper;

import java.util.List;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;

public class Observer extends BothelperSubcommandData {
    public Observer() {
        super(Constants.OBSERVER, "Add or remove observers to game channels");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game name I.E. pbd###-xxxxxx").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player @playername").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ADD_REMOVE, "add or remove player as observer").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member user = guild.getMemberById(event.getOption("player").getAsString());

        List<GuildChannel> channels = guild.getChannels();

        for (GuildChannel channel : channels) {
            if (channel.getName().contains(event.getOption("game_name").getAsString())) {

                if ("add".equals(event.getOption("add_remove").getAsString())) {
                    channel.getPermissionContainer().upsertPermissionOverride(user).grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND).queue();
                    sendMessage("Permissions granted on " + user.getAsMention() + " to channel " + channel.getName());
                }

                if ("remove".equals(event.getOption("add_remove").getAsString())) {
                    channel.getPermissionContainer().upsertPermissionOverride(user).deny(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND).queue();
                    sendMessage("Permissions revoked on " + user.getAsMention() + " to channel " + channel.getName());
                }
            }
        }
    }
}
