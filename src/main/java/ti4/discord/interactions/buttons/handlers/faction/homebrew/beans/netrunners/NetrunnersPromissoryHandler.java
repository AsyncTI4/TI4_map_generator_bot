package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;

@UtilityClass
public class NetrunnersPromissoryHandler {

    private static final String SHARED_NETWORK_ACCESS = "bepnnetrunners";

    public static boolean canUseSharedNetworkAccess(Player player, TechnologyModel technology) {
        return player.hasPlayablePromissoryInHand(SHARED_NETWORK_ACCESS)
                && !technology.isUnitUpgrade()
                && technology.getFaction().orElse("").isBlank()
                && technology.getRequirements().orElse("").length() > 0;
    }

    public static void sendSharedNetworkAccessReminder(Game game, Player player) {
        Player owner = game.getPNOwner(SHARED_NETWORK_ACCESS);
        if (owner == null
                || owner.getUserID().equals(player.getUserID())
                || !player.hasPlayablePromissoryInHand(SHARED_NETWORK_ACCESS)) {
            return;
        }
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", you have _Shared Network Access_. You may research a non-unit,"
                        + " non-faction technology with 1 additional prerequisite by using **Get Other Technology**.");
    }

    @ButtonHandler("useSharedNetworkAccess_")
    public static void useSharedNetworkAccess(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String techId = AliasHandler.resolveTech(buttonID.replace("useSharedNetworkAccess_", ""));
        TechnologyModel technology = Mapper.getTech(techId);
        if (technology == null || !canUseSharedNetworkAccess(player, technology)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You cannot use _Shared Network Access_ for that technology.");
            return;
        }
        PromissoryNoteHelper.resolvePNPlay(SHARED_NETWORK_ACCESS, player, game, event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " is ignoring 1 prerequisite for "
                        + technology.getNameRepresentation()
                        + " with _Shared Network Access_.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static void returnSharedNetworkAccess(Game game) {
        for (Player holder : game.getRealPlayers()) {
            if (!holder.getPromissoryNotesInPlayArea().contains(SHARED_NETWORK_ACCESS)) {
                continue;
            }
            Player owner = game.getPNOwner(SHARED_NETWORK_ACCESS);
            if (owner == null) {
                continue;
            }
            holder.removePromissoryNote(SHARED_NETWORK_ACCESS);
            owner.setPromissoryNote(SHARED_NETWORK_ACCESS);
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, holder, false);
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
            MessageHelper.sendMessageToChannel(
                    holder.getCorrectChannel(),
                    "_Shared Network Access_ was returned to " + owner.getRepresentationNoPing() + ".");
        }
    }
}
