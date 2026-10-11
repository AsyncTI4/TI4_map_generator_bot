package ti4.discord.interactions.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;
import ti4.service.leader.RefreshLeaderService;

class RefreshLeader extends LeaderSubcommand {

    public RefreshLeader() {
        super(Constants.REFRESH_LEADER, "Ready leader");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String leaderID = event.getOption(Constants.LEADER, null, OptionMapping::getAsString);
        Player player = getPlayer();
        Leader playerLeader =
                CommandHelper.requireOrReport(event, player.unsafeGetLeader(leaderID), "Leader not found");
        if (playerLeader == null) return;
        if (playerLeader.isLocked()) {
            MessageHelper.sendMessageToEventChannel(event, "Leader is locked");
            return;
        }
        int tgCount = playerLeader.getTgCount();
        var game = getGame();
        RefreshLeaderService.refreshLeader(player, playerLeader, game);
        StringBuilder message = new StringBuilder(player.getRepresentation())
                .append(" readied ")
                .append(Helper.getLeaderShortRepresentation(playerLeader));
        if (tgCount > 0) {
            message.append(" - ")
                    .append(tgCount)
                    .append(MiscEmojis.getTGorNomadCoinEmoji(game))
                    .append(" transferred from leader to player");
        }
        String msg = message.toString();
        MessageHelper.sendMessageToEventChannel(event, msg);
    }
}
