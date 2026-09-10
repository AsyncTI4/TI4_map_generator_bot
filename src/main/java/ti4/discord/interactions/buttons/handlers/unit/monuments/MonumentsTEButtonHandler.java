package ti4.discord.interactions.buttons.handlers.unit.monuments;

import java.util.Comparator;
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
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.combat.CombatRollService;
import ti4.service.game.MonumentsService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class MonumentsTEButtonHandler {
    private static final String PLACE_SDC = "placeSeraphDataCenter_";
    private static final String SERAPH_DATA_CENTER = "seraphdatacenter";

    public static List<Button> getSDCPlacementButtons(Game game, Player player) {
        if (!game.isMonumentsMode() || !player.hasUnit("bastion_monument")) {
            return List.of();
        }
        return game.getTileMap().values().stream()
                .filter(tile ->
                        tile.getTileModel() != null && !tile.getTileModel().isHyperlane())
                .filter(tile -> tile.containsPlayersUnits(player))
                .filter(tile -> tile.getPlanetUnitHolders().size() == 1)
                .filter(tile -> !tile.hasLegendary())
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + PLACE_SDC + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    @ButtonHandler(PLACE_SDC)
    public static void placeSDC(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getSDCPlacementButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", please choose the system in which to place _Seraph Data Center_ in space.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_SDC;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }
        String position = buttonID.replace(PLACE_SDC, "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null
                || buttons.stream().noneMatch(button -> button.getCustomId().endsWith(PLACE_SDC + position))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 monument");
        tile.getUnitHolders().put(SERAPH_DATA_CENTER, new Planet(SERAPH_DATA_CENTER, Constants.SPACE_CENTER_POSITION));
        game.clearPlanetsCache();
        player.addPlanet(SERAPH_DATA_CENTER);
        player.refreshPlanet(SERAPH_DATA_CENTER);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed _Seraph Data Center_ in the space area of "
                        + tile.getRepresentationForButtons(game, player) + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static void checkSeraphDataCenterBlockade(GenericInteractionCreateEvent event, Game game) {
        if (!game.isMonumentsMode()) {
            return;
        }
        for (Player owner : game.getRealPlayers()) {
            Tile tile = MonumentsService.getMonumentTile(game, owner, "bastion_monument");
            if (tile == null
                    || FoWHelper.playerHasActualShipsInSystem(owner, tile)
                    || game.getRealPlayers().stream()
                            .noneMatch(
                                    other -> other != owner && FoWHelper.playerHasActualShipsInSystem(other, tile))) {
                continue;
            }
            DestroyUnitService.destroyUnits(event, tile, game, owner.getColor(), "1 monument", false);
            tile.getUnitHolders().remove(SERAPH_DATA_CENTER);
            game.clearPlanetsCache();
            owner.removePlanet(SERAPH_DATA_CENTER);
            MessageHelper.sendMessageToChannel(
                    owner.getCorrectChannel(),
                    owner.getRepresentationNoPing() + " lost control of _Seraph Data Center_; it was destroyed.");
            for (Player other : game.getRealPlayers()) {
                if (other != owner && FoWHelper.playerHasActualShipsInSystem(other, tile)) {
                    CombatRollService.sendSpaceAssignHitsButtons(event, game, other, tile, 2);
                }
            }
        }
    }
}
