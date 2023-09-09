package ti4.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class ShowDiscardActionCards extends ACCardsSubcommandData {
    public ShowDiscardActionCards() {
        super(Constants.SHOW_AC_DISCARD_LIST, "Show Action Card discard list");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveMap();

        StringBuilder sb = new StringBuilder();
        sb.append("Action card discard list: ").append("\n");
        int index = 1;
        for (java.util.Map.Entry<String, Integer> ac : activeGame.getDiscardActionCards().entrySet()) {
            sb.append("`").append(index).append(".").append(Helper.leftpad("("+ac.getValue(), 4)).append(")` - ");
            sb.append(Mapper.getActionCard(ac.getKey()).getRepresentation());
            index++;
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}
