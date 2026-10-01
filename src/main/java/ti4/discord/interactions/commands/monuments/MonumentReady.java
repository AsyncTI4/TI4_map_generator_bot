package ti4.discord.interactions.commands.monuments;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.game.MonumentsService;

class MonumentReady extends GameStateSubcommand {

    MonumentReady() {
        super("ready", "Ready your monument", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        UnitModel monument = player.getUnitByBaseType("monument");
        if (monument != null && MonumentsService.readyMonument(getGame(), player, monument.getId())) {
            MessageHelper.sendMessageToEventChannel(
                    event, player.getRepresentationNoPing() + " readied _" + monument.getName() + "_.");
        } else {
            MessageHelper.sendMessageToEventChannel(event, "You do not have an exhausted monument on the board.");
        }
    }
}
