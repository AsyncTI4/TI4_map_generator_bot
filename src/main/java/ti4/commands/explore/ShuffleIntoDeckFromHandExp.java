package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;

public class ShuffleIntoDeckFromHandExp extends ExploreSubcommandData {

    public ShuffleIntoDeckFromHandExp() {
        super(Constants.SHUFFLE_INTO_DECK_FROM_HAND, "Discard an Exploration Card from the hand to deck.");
        addOptions(idOption.setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player activePlayer = activeGame.getPlayer(getUser().getId());
        activePlayer = Helper.getGamePlayer(activeGame, activePlayer, event, null);
        if (activePlayer == null) {
            sendMessage("Player not found in game.");
            return;
        }
        String ids = event.getOption(Constants.EXPLORE_CARD_ID).getAsString().replaceAll(" ", "");
        String[] idList = ids.split(",");
        StringBuilder sb = new StringBuilder();
        for (String id : idList) {
            String card = Mapper.getExploreRepresentation(id);
            if (card != null) {
                activePlayer.removeFragment(id);
                sb.append("Fragment discarded: ").append(displayExplore(id)).append(System.lineSeparator());
                activeGame.addExplore(id);
            } else {
                sb.append("Card ID ").append(id).append(" not found, please retry").append(System.lineSeparator());
            }
        }
        sendMessage(sb.toString());
    }
}
