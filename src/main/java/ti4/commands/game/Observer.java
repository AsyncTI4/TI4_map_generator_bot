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
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.jda.MemberHelper;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameService;

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

        if (!GameManager.isValid(gameName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Game not found: " + gameName);
            return;
        }

        if (!"add".equals(addOrRemove) && !"remove".equals(addOrRemove)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify whether to 'add' or 'remove'");
            return;
        }

        ManagedGame game = GameManager.getManagedGame(gameName);
        Guild guild = game.getGuild();
        Member member = MemberHelper.getMember(guild, user.getId());

        // INVITE TO GAME SERVER IF MISSING
        if (!CreateGameService.inviteUsersToServer(guild, List.of(member), event.getChannel()).isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "User was not a member of the Game's server (" + guild.getName() + ")\nPlease run this command again once the user joins the server.");
            return;
        }

        List<GuildChannel> channels = new ArrayList<>(guild.getChannels());

        // ADD TO GAME's SET CHANNELS
        GuildChannel tableTalk = game.getTableTalkChannel();
        GuildChannel actionsChannel = game.getActionsChannel();
        if ("add".equals(addOrRemove)) {
            addObserver(event, member.getUser().getId(), tableTalk, false);
            addObserver(event, member.getUser().getId(), actionsChannel, false);
        } else {
            removeObserver(event, member.getUser().getId(), tableTalk, false);
            removeObserver(event, member.getUser().getId(), actionsChannel, false);
        }

        channels.remove(tableTalk);
        channels.remove(actionsChannel);

        // ADD TO ALL OTHER CHANNELS
        for (GuildChannel channel : channels) {
            if (channel.getName().contains(gameName)) {
                if ("add".equals(addOrRemove)) {
                    addObserver(event, member.getUser().getId(), channel, game.isFowMode());
                } else {
                    removeObserver(event, member.getUser().getId(), channel, game.isFowMode());
                }
            }
        }
    }

    private void addObserver(SlashCommandInteractionEvent event, String userID, GuildChannel channel, boolean skipMessage) {
        if (channel == null) return;
        Guild guild = channel.getGuild();
        Member user = MemberHelper.getMember(guild, userID);
        channel.getPermissionContainer().upsertPermissionOverride(user).grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND).queue();
        if (!skipMessage) {
            MessageHelper.sendMessageToEventChannel(event, "Observer permissions granted on " + user.getAsMention() + " to channel " + channel.getName() + ": " + channel.getJumpUrl());
        }
    }

    private void removeObserver(SlashCommandInteractionEvent event, String userID, GuildChannel channel, boolean skipMessage) {
        if (channel == null) return;
        // clear permissions instead of revoking permissions.
        // This resets the member's perms to the default value,
        //   -> -> ->  SO IF THE USER IS IN THE GAME, THEY DON'T GET REMOVED
        Guild guild = channel.getGuild();
        Member user = MemberHelper.getMember(guild, userID);
        channel.getPermissionContainer().upsertPermissionOverride(user).clear(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND).queue();
        if (!skipMessage) {
            MessageHelper.sendMessageToEventChannel(event, "Observer permissions revoked on " + user.getAsMention() + " to channel " + channel.getName() + ": " + channel.getJumpUrl());
        }
    }
}
