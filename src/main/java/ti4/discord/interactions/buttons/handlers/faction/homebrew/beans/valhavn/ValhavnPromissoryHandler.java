package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.valhavn;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;
import ti4.service.transaction.SendPromissoryService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class ValhavnPromissoryHandler {
    private static final String EINHERJAR = "bepnvalhavn";
    private static final String COMMIT_EINHERJAR = "valhavnCommitEinherjar_";

    public static void offerEinherjarCommit(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, String planetName, int infantry) {
        if (infantry < 1
                || player.getPlanets().contains(planetName)
                || !player.hasPlayablePromissoryInHand(EINHERJAR)
                || player == game.getPNOwner(EINHERJAR)
                || tile == null
                || tile.getUnitHolderFromPlanet(planetName) == null) {
            return;
        }
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + COMMIT_EINHERJAR + planetName,
                        "Commit 2 Infantry with Einherjar"),
                Buttons.DECLINE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + ", you may use _Einherjar_ to commit 2 infantry from your reinforcements to "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + ".",
                buttons);
    }

    @ButtonHandler(COMMIT_EINHERJAR)
    public static void commitEinherjar(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace(COMMIT_EINHERJAR, "");
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(planetName);
        if (planet == null || !player.hasPlayablePromissoryInHand(EINHERJAR) || player == game.getPNOwner(EINHERJAR)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        PromissoryNoteHelper.resolvePNPlay(EINHERJAR, player, game, event);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "2 infantry " + planetName);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " committed 2 infantry to "
                        + Helper.getPlanetRepresentation(planetName, game) + " using _Einherjar_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void returnEinherjarAtEndOfTurn(GenericInteractionCreateEvent event, Game game, Player holder) {
        if (!holder.getPromissoryNotesInPlayArea().contains(EINHERJAR)) {
            return;
        }

        Player owner = game.getPNOwner(EINHERJAR);
        if (owner != null && owner != holder) {
            SendPromissoryService.returnPromissoryFromPlayAreaToOwner(event, game, holder, owner, EINHERJAR);
        }
    }
}
