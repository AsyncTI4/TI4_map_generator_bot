package ti4.discord.interactions.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

class RemoveAllianceMember extends GameStateSubcommand {

    RemoveAllianceMember() {
        super(Constants.REMOVE_ALLIANCE_MEMBER, "Remove an alliance member", true, true);
        addOptions(CommonOptions.targetFactionOrColor(
                "Faction or Color with which you wish to remove from your alliance"));
        addOptions(CommonOptions.factionColor("Faction or Color (defaults you))"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        Player targetPlayer = CommandHelper.getOtherPlayerOrReply(game, event);
        if (targetPlayer == null) return;
        if (targetPlayer.getAllianceMembers().contains(player.getFaction())) {
            targetPlayer.removeAllianceMember(player.getFaction());
        }
        if (player.getAllianceMembers().contains(targetPlayer.getFaction())) {
            player.removeAllianceMember(targetPlayer.getFaction());
        }

        player.getCardsInfoThread()
                .removeThreadMember(JdaService.jda.getUserById(targetPlayer.getUserID()))
                .queue(Consumers.nop(), BotLogger::catchRestError);
        targetPlayer
                .getCardsInfoThread()
                .removeThreadMember(JdaService.jda.getUserById(player.getUserID()))
                .queue(Consumers.nop(), BotLogger::catchRestError);

        MessageHelper.sendMessageToEventChannel(
                event,
                "Removed " + targetPlayer.getFaction() + " as part of " + player.getFaction()
                        + "'s alliance. This worked both ways. You will have to /franken leader_remove to remove the commanders");
    }
}
