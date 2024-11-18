package ti4.commands2.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class RemoveAllianceMember extends GameStateSubcommand {

    public RemoveAllianceMember() {
        super(Constants.REMOVE_ALLIANCE_MEMBER, "Remove an alliance member", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR,
            "Faction or Color with which you want to remove from your alliance").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR,
            "Faction or Color (defaults you))").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        Player targetPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (targetPlayer.getAllianceMembers().contains(player.getFaction())) {
            targetPlayer.removeAllianceMember(player.getFaction());
        }
        if (player.getAllianceMembers().contains(targetPlayer.getFaction())) {
            player.removeAllianceMember(targetPlayer.getFaction());
        }

        player.getCardsInfoThread().removeThreadMember(AsyncTI4DiscordBot.jda.getUserById(targetPlayer.getUserID())).queue();
        targetPlayer.getCardsInfoThread().removeThreadMember(AsyncTI4DiscordBot.jda.getUserById(player.getUserID())).queue();

        MessageHelper.sendMessageToEventChannel(event, "Removed " + targetPlayer.getFaction() + " as part of " + player.getFaction()
            + "'s alliance. This worked both ways. You will have to /franken leader_remove to remove the commanders");
    }
}
