package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SetupGameChannels extends GameSubcommandData {
    public SetupGameChannels() {
        super(Constants.GAME_CHANNEL_SETUP, "Setup channels and roles for non-standard games");
        addOptions(new OptionData(OptionType.CHANNEL, Constants.MAIN_GAME_CHANNEL, "Main game channel").setRequired(true));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.TABLE_TALK_CHANNEL, "Table talk channel").setRequired(false));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Main player for Community/Fog mode").setRequired(false));
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE1, "Community Mode role").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL1, "Private channel for player/role").setRequired(false));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Main player for Community/Fog mode").setRequired(false));
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE2, "Community Mode role").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL2, "Private channel for player/role").setRequired(false));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Main player for Community/Fog mode").setRequired(false));
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE3, "Community Mode role").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL3, "Private channel for player/role").setRequired(false));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Main player for Community/Fog mode").setRequired(false));
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE4, "Community Mode role").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL4, "Private channel for player/role").setRequired(false));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Main player for Community/Fog mode").setRequired(false));
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE5, "Community Mode role").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL5, "Private channel for player/role").setRequired(false));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Main player for Community/Fog mode").setRequired(false));
        addOptions(new OptionData(OptionType.ROLE, Constants.ROLE6, "Community Mode role").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL6, "Private channel for player/role").setRequired(false));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Main player for Community/Fog mode").setRequired(false));
        //addOptions(new OptionData(OptionType.ROLE, Constants.ROLE7, "Community Mode role").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL7, "Private channel for player/role").setRequired(false));

        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Main player for Community/Fog mode").setRequired(false));
        //addOptions(new OptionData(OptionType.ROLE, Constants.ROLE8, "Community Mode role").setRequired(false));
        addOptions(new OptionData(OptionType.CHANNEL, Constants.CHANNEL8, "Private channel for player/role").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        // Set main channel where SC's get played
        OptionMapping channel = event.getOption(Constants.MAIN_GAME_CHANNEL);
        if (channel == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify main game channel");
            return;
        }
        if (channel.getChannelType() != ChannelType.TEXT) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify text channel");
            return;
        }

        game.setMainChannelID(channel.getAsChannel().asTextChannel().getId());
        OptionMapping channel2 = event.getOption(Constants.TABLE_TALK_CHANNEL);
        if (channel2 != null && channel2.getChannelType() == ChannelType.TEXT) {
            game.setTableTalkChannelID(channel2.getAsChannel().asTextChannel().getId());
        }
        if (game.isCommunityMode() || game.isFowMode()) {
            setRoleAndChannel(event, game, Constants.PLAYER1, Constants.ROLE1, Constants.CHANNEL1);
            setRoleAndChannel(event, game, Constants.PLAYER2, Constants.ROLE2, Constants.CHANNEL2);
            setRoleAndChannel(event, game, Constants.PLAYER3, Constants.ROLE3, Constants.CHANNEL3);
            setRoleAndChannel(event, game, Constants.PLAYER4, Constants.ROLE4, Constants.CHANNEL4);
            setRoleAndChannel(event, game, Constants.PLAYER5, Constants.ROLE5, Constants.CHANNEL5);
            setRoleAndChannel(event, game, Constants.PLAYER6, Constants.ROLE6, Constants.CHANNEL6);
            setRoleAndChannel(event, game, Constants.PLAYER7, Constants.ROLE7, Constants.CHANNEL7);
            setRoleAndChannel(event, game, Constants.PLAYER8, Constants.ROLE8, Constants.CHANNEL8);
        }
    }

    private void setRoleAndChannel(SlashCommandInteractionEvent event, Game game, String playerConstant, String roleConstant, String channelConstant) {
        OptionMapping player = event.getOption(playerConstant);
        OptionMapping role = event.getOption(roleConstant);
        OptionMapping channel = event.getOption(channelConstant);

        if (player == null && channel == null) {
            return;
        }
        if (player != null && channel != null) {
            User asUser = player.getAsUser();
            Player player_ = game.getPlayer(asUser.getId());
            if (player_ == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify game player: " + playerConstant + " is invalid.");
                return;
            }

            //set community mode data
            if (game.isCommunityMode()) {
                if (role == null) {
                    //MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify role for community mode: " + roleConstant + " is missing");
                    //return;
                } else {
                    player_.setRoleIDForCommunity(role.getAsRole().getId());
                }
            }

            //set private channel data
            if (channel.getChannelType() != ChannelType.TEXT) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify text channel for " + channelConstant);
                return;
            }
            player_.setPrivateChannelID(channel.getAsChannel().getId());
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify player and channel");
        }
    }
}
