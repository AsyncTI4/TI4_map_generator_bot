package ti4.discord.interactions.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.revenant.RevenantTechHandler;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.leader.PlayHeroService;

class PurgeLeader extends LeaderSubcommand {

    public PurgeLeader() {
        super(Constants.PURGE_LEADER, "Purge leader");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String leaderID = event.getOption(Constants.LEADER, null, OptionMapping::getAsString);
        Player player = getPlayer();
        Leader playerLeader = player.unsafeGetLeader(leaderID);
        PlayHeroService.rememberFrankenFirmamentHero(player, playerLeader);
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            if (Constants.COMMANDER.equals(playerLeader.getType()) || Constants.HERO.equals(playerLeader.getType())) {
                RevenantTechHandler.doLazarusPodsLeaderCheck(getGame(), player);
            }
            MessageHelper.sendMessageToEventChannel(
                    event, LeaderEmojis.getLeaderEmoji(playerLeader).toString());
            String message =
                    player.getRepresentation() + " purged " + Helper.getLeaderShortRepresentation(playerLeader);
            MessageHelper.sendMessageToEventChannel(event, message);
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Leader not found");
        }
    }
}
