package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Arcanum;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class ArcanumLeadersHandler {
    private static final String PLACE_COMMANDER_FIGHTER = "arcanumCommanderPlaceFighter_";

    public static void offerArcanumTechExhaustCommanderButtons(Player player) {
        if (player == null
                || player.getGame() == null
                || !player.getGame().playerHasLeaderUnlockedOrAlliance(player, "arcanumcommander")) {
            return;
        }

        Game game = player.getGame();
        List<Button> buttons = getCommanderFighterButtons(player, game);

        if (buttons.isEmpty()) {
            return;
        }

        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        List<Button> displayedButtons = buttons.size() <= 24
                ? new ArrayList<>(buttons)
                : NewStuffHelper.buttonPagination(
                        buttons, extraButtons, player.factionButtonChecker() + PLACE_COMMANDER_FIGHTER, 25, 0, false);
        if (buttons.size() <= 24) {
            displayedButtons.addAll(extraButtons);
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), getCommanderFighterMessage(player), displayedButtons);
    }

    @ButtonHandler(PLACE_COMMANDER_FIGHTER)
    public static void placeCommanderFighter(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !game.playerHasLeaderUnlockedOrAlliance(player, "arcanumcommander")) {
            return;
        }

        List<Button> buttons = getCommanderFighterButtons(player, game);
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                extraButtons,
                getCommanderFighterMessage(player),
                player.factionButtonChecker() + PLACE_COMMANDER_FIGHTER,
                buttonID)) {
            return;
        }

        String tilePosition = buttonID.substring(PLACE_COMMANDER_FIGHTER.length());
        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null
                || !ButtonHelper.getTilesWithShipsInTheSystem(player, game).contains(tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelperModifyUnits.placeUnitAndDeleteButton(
                "placeOneNDone_skipbuild_ff_" + tilePosition, event, game, player);
    }

    private static List<Button> getCommanderFighterButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesWithShipsInTheSystem(player, game)) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + PLACE_COMMANDER_FIGHTER + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }

    private static String getCommanderFighterMessage(Player player) {
        return player.getRepresentation()
                + ", you may use **Orthis Lithon, the Rune-Smith** to place 1 fighter in a system that contains 1 or more of your ships.";
    }
}
