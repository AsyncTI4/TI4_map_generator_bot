package ti4.commands.game;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.bothelper.CreateGameChannels;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public class Observer extends GameSubcommandData {
    public Observer() {
        super(Constants.OBSERVER, "Add or remove observers to game channels");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game name I.E. pbd###-xxxxxx").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player @playername").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ADD_REMOVE, "add or remove player as observer").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member user = event.getOption("player").getAsMember();
        String gameName = event.getOption("game_name", null, OptionMapping::getAsString);
        String addOrRemove = event.getOption("add_remove", "", OptionMapping::getAsString).toLowerCase();

        if (!GameManager.getInstance().isValidGame(gameName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Game not found: " + gameName + ".");
            return;
        }

        if (!"add".equals(addOrRemove) && !"remove".equals(addOrRemove)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify whether to `add` or `remove`.");
            return;
        }

        Game game = GameManager.getInstance().getGame(gameName);

        // INVITE TO GAME SERVER IF MISSING
        if (!CreateGameChannels.inviteUsersToServer(game.getGuild(), List.of(user), event.getChannel()).isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "User was not a member of the game's server (" + game.getGuild().getName() + ")."
                + "\nPlease run this command again once the user joins the server.");
            return;
        }

        List<GuildChannel> channels = new ArrayList<>(game.getGuild().getChannels());

        // ADD TO GAME's SET CHANNELS
        GuildChannel tableTalk = game.getTableTalkChannel();
        GuildChannel actionsChannel = game.getActionsChannel();
        if ("add".equals(addOrRemove)) {
            addObserver(event, user, tableTalk);
            addObserver(event, user, actionsChannel);
        } else if ("remove".equals(addOrRemove)) {
            removeObserver(event, user, tableTalk);
            removeObserver(event, user, actionsChannel);
        }

        channels.remove(tableTalk);
        channels.remove(actionsChannel);

        // ADD TO ALL OTHER CHANNELS
        for (GuildChannel channel : channels) {
            if (channel.getName().contains(gameName)) {
                if ("add".equals(addOrRemove)) {
                    addObserver(event, user, channel);
                } else if ("remove".equals(addOrRemove)) {
                    removeObserver(event, user, channel);
                }
            }
        }
    }

    private void addObserver(SlashCommandInteractionEvent event, Member user, GuildChannel channel) {
        if (channel == null) return;
        channel.getPermissionContainer().upsertPermissionOverride(user).grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND).queue();
        MessageHelper.sendMessageToEventChannel(event, "Observer permissions granted on " + user.getAsMention() + " to channel " + channel.getName() + ": " + channel.getJumpUrl() + ".");
    }

    private void removeObserver(SlashCommandInteractionEvent event, Member user, GuildChannel channel) {
        if (channel == null) return;
        // clear permissions instead of revoking permissions.
        // This resets the user's perms to the default value, 
        //   -> -> ->  SO IF THE USER IS IN THE GAME, THEY DON'T GET REMOVED
        channel.getPermissionContainer().upsertPermissionOverride(user).clear(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND).queue();
        MessageHelper.sendMessageToEventChannel(event, "Observer permissions revoked on " + user.getAsMention() + " to channel " + channel.getName() + ": " + channel.getJumpUrl() + ".");
    }
}
