package ti4.discord.interactions.buttons.handlers.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class SvartalfheimLegendaryButtonHandler {

    private static final String PRODUCE_EXTRA_SHIP = "svartalfheimProduceExtraShip_";

    public static void offerAndvarisArtificing(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!player.hasPlanet("svartalfheim")
                || player.getExhaustedPlanetsAbilities().contains("svartalfheim")) return;
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : player.getCurrentProducedUnits().entrySet()) {
            if (entry.getValue() < 1) continue;
            int locationSeparator = entry.getKey().lastIndexOf('_');
            int tileSeparator = entry.getKey().lastIndexOf('_', locationSeparator - 1);
            if (tileSeparator < 1 || locationSeparator < 0) continue;
            String unitAlias = entry.getKey().substring(0, tileSeparator);
            String tilePosition = entry.getKey().substring(tileSeparator + 1, locationSeparator);
            UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitAlias), player.getColor());
            UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), null);
            Tile tile = game.getTileByPosition(tilePosition);
            if (unit == null || tile == null || !unit.getIsShip() || unit.getUnitType() == UnitType.Fighter) continue;
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PRODUCE_EXTRA_SHIP + unitAlias + "|" + tile.getPosition(),
                    "Produce 1 " + unit.getName() + " in " + tile.getRepresentationForButtons(game, player),
                    unit.getUnitEmoji()));
        }
        if (buttons.isEmpty()) return;
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", you may exhaust _Andvari's Artificing_ to produce 1 additional non-fighter ship type you produced at no cost.",
                buttons);
    }

    @ButtonHandler(PRODUCE_EXTRA_SHIP)
    public static void produceExtraShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(PRODUCE_EXTRA_SHIP.length()).split("\\|", 2);
        if (payload.length != 2
                || !player.hasPlanet("svartalfheim")
                || player.getExhaustedPlanetsAbilities().contains("svartalfheim")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(payload[0]), player.getColor());
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), null);
        Tile tile = game.getTileByPosition(payload[1]);
        if (unit == null || tile == null || !unit.getIsShip() || unit.getUnitType() == UnitType.Fighter) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.exhaustPlanetAbility("svartalfheim");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + payload[0] + " " + Constants.SPACE);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " exhausted _Andvari's Artificing_ to produce 1 " + unit.getName()
                        + " in " + tile.getRepresentationForButtons(game, player) + " at no cost.");
        ButtonHelper.deleteMessage(event);
    }
}
