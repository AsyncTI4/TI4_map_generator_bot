package ti4.discord.interactions.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class ResetLeader extends GameStateSubcommand {

    public ResetLeader() {
        super(Constants.RESET, "Reset all leaders", true, true);
        addOptions(CommonOptions.factionColorForStats());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        player.initLeaders();
        MessageHelper.sendMessageToEventChannel(event, "Leaders were reset: " + player.getLeaderIDs());
    }
}
