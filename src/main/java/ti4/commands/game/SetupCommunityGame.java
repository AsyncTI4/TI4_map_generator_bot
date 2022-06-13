package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SetupCommunityGame extends GameSubcommandData {
    public SetupCommunityGame() {
        super(Constants.COMMUNITY_SETUP, "Community Game Setup");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Specify group main player").setRequired(true));
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE1, "Specify group ROLE").setRequired(true));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL1, "Specify group CHANNEL").setRequired(true));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Specify group main player").setRequired(false));
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE2, "Specify group ROLE").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL2, "Specify group CHANNEL").setRequired(false));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Specify group main player").setRequired(false));
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE3, "Specify group ROLE").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL3, "Specify group CHANNEL").setRequired(false));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Specify group main player").setRequired(false));
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE4, "Specify group ROLE").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL4, "Specify group CHANNEL").setRequired(false));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Specify group main player").setRequired(false));
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE5, "Specify group ROLE").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL5, "Specify group CHANNEL").setRequired(false));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Specify group main player").setRequired(false));
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE6, "Specify group ROLE").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL6, "Specify group CHANNEL").setRequired(false));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Specify group main player").setRequired(false));
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE7, "Specify group ROLE").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL7, "Specify group CHANNEL").setRequired(false));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Specify group main player").setRequired(false));
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE8, "Specify group ROLE").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL8, "Specify group CHANNEL").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        if (!activeMap.isCommunityMode()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Not COMMUNITY mode game, can't setup");
        }
        setRoleAndChannel(event, activeMap, Constants.PLAYER1, Constants.ROLE1, Constants.CHANNEL1);
        setRoleAndChannel(event, activeMap, Constants.PLAYER2, Constants.ROLE2, Constants.CHANNEL2);
        setRoleAndChannel(event, activeMap, Constants.PLAYER3, Constants.ROLE3, Constants.CHANNEL3);
        setRoleAndChannel(event, activeMap, Constants.PLAYER4, Constants.ROLE4, Constants.CHANNEL4);
        setRoleAndChannel(event, activeMap, Constants.PLAYER5, Constants.ROLE5, Constants.CHANNEL5);
        setRoleAndChannel(event, activeMap, Constants.PLAYER6, Constants.ROLE6, Constants.CHANNEL6);
        setRoleAndChannel(event, activeMap, Constants.PLAYER7, Constants.ROLE7, Constants.CHANNEL7);
        setRoleAndChannel(event, activeMap, Constants.PLAYER8, Constants.ROLE8, Constants.CHANNEL8);
    }

    private void setRoleAndChannel(SlashCommandInteractionEvent event, Map activeMap, String player1, String role1, String channel1) {
        OptionMapping player = event.getOption(player1);
        OptionMapping role = event.getOption(role1);
        OptionMapping channel = event.getOption(channel1);
        if (player == null && role == null && channel == null) {
            return;
        }
        if (player != null && role != null && channel != null) {
            User asUser = player.getAsUser();
            Player player_ = activeMap.getPlayer(asUser.getId());
            if (player_ == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify game player");
                return;
            }
            player_.setRoleForCommunity(role.getAsRole());
            player_.setChannelForCommunity(channel.getAsGuildChannel());
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify group player, role and channel");
            return;
        }
    }
}
