package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
class FreedomFightersAcd2ButtonHandler {

    @ButtonHandler("resolveFreedomFighters")
    public static void resolveFreedomFighters(Player player, Game game, ButtonInteractionEvent event) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null || activeSystem.getPlanetUnitHolders().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", _Freedom Fighters_ requires an active system with planets.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>(activeSystem.getPlanetUnitHolders().stream()
                .map(planet -> Buttons.green(
                        "resolveFreedomFightersStep2_" + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList());
        buttons.add(Buttons.red("deleteButtons", "Done placing infantry"));

        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", use the buttons to place up to 1 infantry from reinforcements on each planet in the active system.",
                buttons);
    }

    @ButtonHandler("resolveFreedomFightersStep2_")
    public static void resolveFreedomFightersStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("resolveFreedomFightersStep2_", "");
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not resolve _Freedom Fighters_ for that planet.");
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planet);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " placed 1 infantry on "
                        + Helper.getPlanetRepresentation(planet, game) + " via _Freedom Fighters_.");
    }
}
