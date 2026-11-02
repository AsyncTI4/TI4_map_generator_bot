package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.deep;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.transaction.SendPromissoryService;

@UtilityClass
public class DeepPromissoryHandler {
    private static final String DARK_MATTER = "bepndeep";

    public static void resolveDarkMatter(Game game, Player holder) {
        Tile homeSystem = holder.getHomeSystemTile();
        if (homeSystem == null) {
            MessageHelper.sendMessageToChannel(
                    holder.getCorrectChannel(),
                    holder.getRepresentationNoPing() + " has no home system in which to place a frontier token.");
            return;
        }

        homeSystem.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
        MessageHelper.sendMessageToChannel(
                holder.getCorrectChannel(),
                holder.getRepresentationNoPing()
                        + " placed a frontier token in their home system using _Dark Matter_.");
    }

    public static void returnDarkMatterAtOwnerTurnStart(GenericInteractionCreateEvent event, Game game, Player owner) {
        if (game.getPNOwner(DARK_MATTER) != owner) {
            return;
        }
        for (Player holder : game.getRealPlayers()) {
            if (holder != owner && holder.getPromissoryNotesInPlayArea().contains(DARK_MATTER)) {
                SendPromissoryService.returnPromissoryFromPlayAreaToOwner(event, game, holder, owner, DARK_MATTER);
            }
        }
    }
}
