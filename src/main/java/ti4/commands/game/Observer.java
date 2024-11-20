package ti4.commands.game;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.GameCreationHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

class Observer extends Subcommand {

    public Observer() {
        super(Constants.OBSERVER, "Add or remove observers to game channels");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game name I.E. pbd###-xxxxxx").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player @playername").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ADD_REMOVE, "add or remove player as observer").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User user = event.getOption(Constants.PLAYER).getAsUser();
        String gameName = event.getOption("game_name", null, OptionMapping::getAsString);
        String addOrRemove = event.getOption("add_remove", "", OptionMapping::getAsString).toLowerCase();

        if (!GameManager.isValidGame(gameName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Game not found: " + gameName);
            return;
        }

        if (!"add".equals(addOrRemove) && !"remove".equals(addOrRemove)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify whether to 'add' or 'remove'");
            return;
        }

        Game game = GameManager.getGame(gameName);
        Guild guild = game.getGuild();
        Member member = guild.getMemberById(user.getId());

        // INVITE TO GAME SERVER IF MISSING
        if (!GameCreationHelper.inviteUsersToServer(guild, List.of(member), event.getChannel()).isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "User was not a member of the Game's server (" + guild.getName() + ")\nPlease run this command again once the user joins the server.");
            return;
        }

        List<GuildChannel> channels = new ArrayList<>(guild.getChannels());

        // ADD TO GAME's SET CHANNELS
        GuildChannel tableTalk = game.getTableTalkChannel();
        GuildChannel actionsChannel = game.getActionsChannel();
        if ("add".equals(addOrRemove)) {
            addObserver(event, member.getUser().getId(), tableTalk);
            addObserver(event, member.getUser().getId(), actionsChannel);
        } else {
            removeObserver(event, member.getUser().getId(), tableTalk);
            removeObserver(event, member.getUser().getId(), actionsChannel);
        }

        channels.remove(tableTalk);
        channels.remove(actionsChannel);

        // ADD TO ALL OTHER CHANNELS
        for (GuildChannel channel : channels) {
            if (channel.getName().contains(gameName)) {
                if ("add".equals(addOrRemove)) {
                    addObserver(event, member.getUser().getId(), channel);
                } else {
                    removeObserver(event, member.getUser().getId(), channel);
                }
            }
        }
    }

    private void addObserver(SlashCommandInteractionEvent event, String userID, GuildChannel channel) {
        if (channel == null) return;
        Guild guild = channel.getGuild();
        Member user = guild.getMemberById(userID);
        channel.getPermissionContainer().upsertPermissionOverride(user).grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND).queue();
        MessageHelper.sendMessageToEventChannel(event, "Observer permissions granted on " + user.getAsMention() + " to channel " + channel.getName() + ": " + channel.getJumpUrl());
    }

    private void removeObserver(SlashCommandInteractionEvent event, String userID, GuildChannel channel) {
        if (channel == null) return;
        // clear permissions instead of revoking permissions.
        // This resets the member's perms to the default value, 
        //   -> -> ->  SO IF THE USER IS IN THE GAME, THEY DON'T GET REMOVED
        Guild guild = channel.getGuild();
        Member user = guild.getMemberById(userID);
        channel.getPermissionContainer().upsertPermissionOverride(user).clear(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND).queue();
        MessageHelper.sendMessageToEventChannel(event, "Observer permissions revoked on " + user.getAsMention() + " to channel " + channel.getName() + ": " + channel.getJumpUrl());
    }
}
