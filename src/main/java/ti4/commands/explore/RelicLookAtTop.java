package ti4.commands.explore;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

public class RelicLookAtTop extends GenericRelicAction {
    public RelicLookAtTop() {
        super(Constants.RELIC_LOOK_AT_TOP, "Look at the top of the relic deck. Sends to Cards Info thread.");
    }

    public void doAction(Player player, SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        List<String> relicDeck = game.getAllRelics();
        if (relicDeck.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "Relic deck is empty");
            return;
        }
        String relicID = relicDeck.get(0);
        RelicModel relicModel = Mapper.getRelic(relicID);
        String sb = "**Relic - Look at Top**\n" + player.getRepresentation() + "\n" + relicModel.getSimpleRepresentation();
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, sb);
    }
}
