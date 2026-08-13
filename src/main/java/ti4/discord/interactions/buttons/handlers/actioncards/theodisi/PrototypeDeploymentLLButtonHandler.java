package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

@UtilityClass
public class PrototypeDeploymentLLButtonHandler {
    private static final String RESOLVE = "resolvePrototypeDeployment";
    private static final String SELECT = "prototypeDeploymentUnit_";
    private static final String PLACE = "prototypeDeploymentPlace_";
    private static final String STATE = "prototypeDeployment_";

    @ButtonHandler(RESOLVE)
    public static void resolvePrototypeDeployment(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getUnitUpgradeButtons(player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no eligible unit upgrade for _Prototype Deployment_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose the researched unit upgrade for _Prototype Deployment_.",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + SELECT, 0));
    }

    @ButtonHandler(SELECT)
    public static void selectPrototypeUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> unitButtons = getUnitUpgradeButtons(player);
        String message =
                player.getRepresentationNoPing() + ", choose the researched unit upgrade for _Prototype Deployment_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                unitButtons,
                message,
                player.factionButtonChecker() + SELECT,
                buttonID)) {
            return;
        }
        String techId = buttonID.substring(SELECT.length());
        TechnologyModel tech = Mapper.getTech(techId);
        UnitModel unit = tech == null || !player.hasTech(techId) || !tech.isUnitUpgrade()
                ? null
                : player.getUnitFromUnitKey(
                        Mapper.getUnitKey(tech.getBaseUpgrade().orElse(techId), player.getColorID()));
        if (unit == null || "warsun".equalsIgnoreCase(unit.getBaseType())) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That unit upgrade is no longer eligible.");
            return;
        }
        game.setStoredValue(STATE + player.getFaction(), techId);
        List<Button> buttons = getPlacementButtons(game, player, unit);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "No legal placement is currently available for that unit.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", place 1 " + unit.getName()
                        + " from reinforcements for _Prototype Deployment_.",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + PLACE, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE)
    public static void changePlacementPage(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String techId = game.getStoredValue(STATE + player.getFaction());
        TechnologyModel tech = Mapper.getTech(techId);
        UnitModel unit = tech == null || !player.hasTech(techId) || !tech.isUnitUpgrade()
                ? null
                : player.getUnitFromUnitKey(
                        Mapper.getUnitKey(tech.getBaseUpgrade().orElse(techId), player.getColorID()));
        if (unit == null || "warsun".equalsIgnoreCase(unit.getBaseType())) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That unit upgrade is no longer eligible.");
            return;
        }
        String message = player.getRepresentationNoPing() + ", place 1 " + unit.getName()
                + " from reinforcements for _Prototype Deployment_.";
        NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                getPlacementButtons(game, player, unit),
                message,
                player.factionButtonChecker() + PLACE,
                buttonID);
    }

    private static List<Button> getUnitUpgradeButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String techId : player.getTechs()) {
            TechnologyModel tech = Mapper.getTech(techId);
            if (tech == null || !tech.isUnitUpgrade()) continue;
            UnitModel unit = player.getUnitFromUnitKey(
                    Mapper.getUnitKey(tech.getBaseUpgrade().orElse(techId), player.getColorID()));
            if (unit == null || "warsun".equalsIgnoreCase(unit.getBaseType())) continue;
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + SELECT + techId, "Deploy " + unit.getName(), unit.getUnitEmoji()));
        }
        return buttons;
    }

    private static List<Button> getPlacementButtons(Game game, Player player, UnitModel unit) {
        List<Button> buttons = new ArrayList<>();
        String unitType = unit.getBaseType();
        if (unit.getIsGroundForce() || unit.getIsStructure()) {
            for (String planetName : player.getPlanetsAllianceMode()) {
                Planet planet = game.getUnitHolderFromPlanet(planetName);
                if (planet == null
                        || planet.isSpaceStation(game)
                        || planet.getTokenList().stream().anyMatch(token -> token.contains("dmz"))
                        || ("spacedock".equalsIgnoreCase(unitType)
                                && planet.getUnitCount(UnitType.Spacedock, player) > 0)) {
                    continue;
                }
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "placeOneNDone_skipbuild_" + unitType + "_" + planetName,
                        Helper.getPlanetRepresentation(planetName, game)));
            }
            return buttons;
        }
        for (Tile tile : game.getTileMap().values()) {
            UnitHolder space = tile.getSpaceUnitHolder();
            if (space.getUnitCount(UnitType.Spacedock, player) > 0) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "placeOneNDone_skipbuild_" + unitType + "_"
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }
}
