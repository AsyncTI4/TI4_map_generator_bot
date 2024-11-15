package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

public class ExploreShuffleIntoDeckFromHand extends ExploreSubcommandData {

    public ExploreShuffleIntoDeckFromHand() {
        super(Constants.SHUFFLE_INTO_DECK_FROM_HAND, "Discard an Exploration Card from the hand to deck.");
        addOptions(idOption.setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player not found in game.");
            return;
        }
        String ids = event.getOption(Constants.EXPLORE_CARD_ID).getAsString().replaceAll(" ", "");
        String[] idList = ids.split(",");
        StringBuilder sb = new StringBuilder();
        for (String id : idList) {
            ExploreModel explore = Mapper.getExplore(id);
            if (explore != null) {
                player.removeFragment(id);
                sb.append("Fragment discarded: ").append(explore.textRepresentation()).append(System.lineSeparator());
                game.addExplore(id);
            } else {
                sb.append("Card ID ").append(id).append(" not found, please retry").append(System.lineSeparator());
            }
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
