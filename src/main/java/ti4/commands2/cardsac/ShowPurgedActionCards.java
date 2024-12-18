package ti4.commands2.cardsac;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.message.MessageHelper;

class ShowPurgedActionCards extends GameStateSubcommand {

    public ShowPurgedActionCards() {
        super(Constants.SHOW_AC_PURGED_LIST, "Show action card purged list", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        showPurged( getGame(), event);
    }

    private static void showPurged(Game game, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Action card purge list: ").append("\n");
        int index = 1;
        for (Map.Entry<String, Integer> ac : game.getPurgedActionCards().entrySet()) {
            sb.append("`").append(index).append(".").append(Helper.leftpad("(" + ac.getValue(), 4)).append(")` - ");
            if (Mapper.getActionCard(ac.getKey()) != null) {
                sb.append(Mapper.getActionCard(ac.getKey()).getRepresentation());
            }

            index++;
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }
}
