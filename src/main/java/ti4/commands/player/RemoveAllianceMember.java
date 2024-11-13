package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.CommandHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RemoveAllianceMember extends PlayerSubcommandData {
    public RemoveAllianceMember() {
        super(Constants.REMOVE_ALLIANCE_MEMBER, "Remove an alliance member");
        addOptions(new OptionData(OptionType.STRING, Constants.OTHER_FACTION_OR_COLOR,
            "Faction or Color with which you want to remove from your alliance").setAutoComplete(true)
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        Player player_ = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (player_ == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player to remove from the alliance could not be found");
            return;
        }
        if (player_.getAllianceMembers().contains(player.getFaction())) {
            player_.removeAllianceMember(player.getFaction());
        }
        if (player.getAllianceMembers().contains(player_.getFaction())) {
            player.removeAllianceMember(player_.getFaction());
        }

        player.getCardsInfoThread().removeThreadMember(AsyncTI4DiscordBot.jda.getUserById(player_.getUserID())).queue();
        player_.getCardsInfoThread().removeThreadMember(AsyncTI4DiscordBot.jda.getUserById(player.getUserID())).queue();

        MessageHelper.sendMessageToEventChannel(event, "Removed " + player_.getFaction() + " as part of " + player.getFaction()
            + "'s alliance. This worked both ways. You will have to /franken leader_remove to remove the commanders");
    }
}
