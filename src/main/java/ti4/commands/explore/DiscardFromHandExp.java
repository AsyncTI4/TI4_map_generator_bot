package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DiscardFromHandExp extends ExploreSubcommandData {

    public DiscardFromHandExp() {
        super(Constants.DISCARD_FROM_HAND, "Discard an Exploration Card from the hand to deck.");
        addOptions(idOption.setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player activePlayer = activeMap.getPlayer(getUser().getId());
        activePlayer = Helper.getPlayer(activeMap, activePlayer, event);
        if (activePlayer == null) {
            MessageHelper.replyToMessage(event, "Player not found in game.");
            return;
        }
        String ids = event.getOption(Constants.EXPLORE_CARD_ID).getAsString().replaceAll(" ", "");
        String[] idList = ids.split(",");
        StringBuilder sb = new StringBuilder();
        for (String id : idList) {
            String card = Mapper.getExplore(id);
            if (card != null) {
                activePlayer.removeFragment(id);
                sb.append("Fragment discarded: ").append(displayExplore(id)).append(System.lineSeparator());
                activeMap.addExplore(id);
            } else {
                sb.append("Card ID ").append(id).append(" not found, please retry").append(System.lineSeparator());
            }
        }
        MessageHelper.replyToMessage(event, sb.toString());
    }
}
