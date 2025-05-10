package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.service.info.ListTurnOrderService;

class ListTurnOrder extends GameStateSubcommand {

    public ListTurnOrder() {
        super(Constants.TURN_ORDER, "List turn order with strategy card played and player passed status", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        ListTurnOrderService.turnOrder(event, game, false);
    }
}
