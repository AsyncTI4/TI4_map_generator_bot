package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

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
import ti4.service.emoji.FactionEmojis;
import ti4.service.tactical.movement.RealityFieldImpactorService;

@UtilityClass
public class PharadnTechHandler {
    private static final String USE_IMPACTOR = "useRealityFieldImpactor";
    private static final String SELECT_IMPACTOR_ANOMALY = "selectRealityFieldImpactorAnomaly_";

    @ButtonHandler(USE_IMPACTOR)
    public static void useRealityFieldImpactor(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasTech("dspharb") || !player.isActivePlayer() || RealityFieldImpactorService.hasBeenUsed(game)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!FoWHelper.knowsTile(game, player, tile.getPosition()) || !tile.isAnomaly(game, null)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + SELECT_IMPACTOR_ANOMALY + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player),
                    FactionEmojis.pharadn));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You do not know of any anomalies.");
            return;
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please choose the anomaly you wish to make ineffective this tactical action.",
                buttons);
    }

    @ButtonHandler(SELECT_IMPACTOR_ANOMALY)
    public static void selectRealityFieldImpactorAnomaly(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(SELECT_IMPACTOR_ANOMALY.length()));
        if (!player.hasTech("dspharb")
                || !player.isActivePlayer()
                || RealityFieldImpactorService.hasBeenUsed(game)
                || tile == null
                || !FoWHelper.knowsTile(game, player, tile.getPosition())
                || !tile.isAnomaly(game, null)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        RealityFieldImpactorService.nullify(game, tile);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " used _Reality-Field Impactor_; "
                        + tile.getRepresentationForButtons(game, player)
                        + " has no effect until this tactical action ends.");
    }
}
