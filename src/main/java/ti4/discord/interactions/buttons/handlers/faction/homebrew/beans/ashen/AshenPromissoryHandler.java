package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class AshenPromissoryHandler {

    private static final String FROM_THE_ASHES_ID = "bepnashen";
    private static final String USE_FROM_THE_ASHES_PREFIX = "ashenFromTheAshes_";

    public static void addFromTheAshesButton(
            List<Button> buttons,
            Game game,
            Player hitAssignPlayer,
            Player opposingPlayer,
            Tile tile,
            UnitHolder combatOnHolder,
            int hits) {
        if (!canOfferFromTheAshes(game, hitAssignPlayer, opposingPlayer, tile, combatOnHolder, hits)) {
            return;
        }

        buttons.add(Buttons.gray(
                hitAssignPlayer.factionButtonChecker()
                        + USE_FROM_THE_ASHES_PREFIX
                        + tile.getPosition()
                        + "~"
                        + combatOnHolder.getName()
                        + "~"
                        + hits,
                "Use From the Ashes",
                FactionEmojis.ashen));
    }

    @ButtonHandler(USE_FROM_THE_ASHES_PREFIX)
    public static void useFromTheAshes(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring(USE_FROM_THE_ASHES_PREFIX.length());
        String[] parts = payload.split("~", 3);
        if (parts.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        String planet = parts[1];
        int hits = Integer.parseInt(parts[2]);
        Player owner = game.getPNOwner(FROM_THE_ASHES_ID);
        if (tile == null
                || owner == null
                || owner == player
                || !player.getPromissoryNotes().containsKey(FROM_THE_ASHES_ID)
                || player.getPromissoryNotesOwned().contains(FROM_THE_ASHES_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "_From the Ashes_ is no longer available to use here.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.removePromissoryNote(FROM_THE_ASHES_ID);
        owner.setPromissoryNote(FROM_THE_ASHES_ID);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);

        int remainingHits = Math.max(0, hits - 1);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " used _From the Ashes_ to prevent 1 unit from being destroyed"
                        + " during ground combat, then returned the note to "
                        + owner.getRepresentationNoPing() + ".");

        if (remainingHits < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + "autoAssignGroundHits_" + planet + "_" + remainingHits,
                        "Auto-assign Hit" + (remainingHits == 1 ? "" : "s")),
                Buttons.red(
                        "getDamageButtons_" + tile.getPosition() + "deleteThis_groundcombat",
                        "Manually Assign Hit" + (remainingHits == 1 ? "" : "s")));
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationUnfogged() + " you may assign "
                        + (remainingHits == 1 ? "the remaining hit." : "the remaining " + remainingHits + " hits."),
                buttons);
    }

    private static boolean canOfferFromTheAshes(
            Game game, Player hitAssignPlayer, Player opposingPlayer, Tile tile, UnitHolder combatOnHolder, int hits) {
        if (game == null
                || hitAssignPlayer == null
                || opposingPlayer == null
                || tile == null
                || combatOnHolder == null
                || hits < 1
                || tile.getPlanetUnitHolders().stream()
                        .noneMatch(planet -> planet.getName().equals(combatOnHolder.getName()))) {
            return false;
        }

        if (!hitAssignPlayer.getPromissoryNotes().containsKey(FROM_THE_ASHES_ID)
                || hitAssignPlayer.getPromissoryNotesOwned().contains(FROM_THE_ASHES_ID)) {
            return false;
        }

        Player owner = game.getPNOwner(FROM_THE_ASHES_ID);
        return owner != null && owner != hitAssignPlayer && owner != opposingPlayer;
    }
}
