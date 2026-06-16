package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.onyxxa;

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
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;

@UtilityClass
public class OnyxxaHeroButtonHandler {

    public static void postInitialButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasActualShipsInSystem(player, tile)) {
                buttons.add(Buttons.green(
                        "moveShipToAdjacentSystemStep2_" + tile.getPosition() + "_combat",
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        buttons.add(Buttons.red("onyxxaHeroDone", "Done Resolving"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please use buttons to resolve your _Titles Are Silly_ hero ability.",
                buttons);
    }

    @ButtonHandler("onyxxaHeroDone")
    public static void onyxxaHeroDone(ButtonInteractionEvent event, Player player, Game game) {
        ButtonHelper.deleteMessage(event);
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasActualShipsInSystem(player, tile)
                    && FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                StartCombatService.combatCheck(game, event, tile);
            }
        }
    }
}
