package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;
import ti4.service.leader.RefreshLeaderService;

class RefreshLeader extends GameStateSubcommand {

    public RefreshLeader() {
        super(Constants.REFRESH_LEADER, "Ready leader", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER, "Leader for which to do action").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String leaderID = event.getOption(Constants.LEADER, null, OptionMapping::getAsString);
        Player player = getPlayer();
        Leader playerLeader = player.unsafeGetLeader(leaderID);
        if (playerLeader == null) {
            MessageHelper.sendMessageToEventChannel(event, "Leader not found");
            return;
        }
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
            message.append(" - ").append(tgCount).append(MiscEmojis.getTGorNomadCoinEmoji(game)).append(" transferred from leader to player");

        }
        String msg = message.toString();
        MessageHelper.sendMessageToEventChannel(event, msg);
    }
}
