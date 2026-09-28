package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Thrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.MiscEmojis;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class ThronesThroneHandler {
    private static final String USE_SKARNATH = "useSkarnathAbility_";
    private static final String PLACE_SKARNATH = "placeSkarnathShip_";
    private static final String SKARNATH_TARGET_SYSTEM = "skarnathTargetSystem_";
    private static final String SKARNATH_PRODUCED_SHIPS = "skarnathProducedShips_";
    private static final String SELECT_CINERON_SYSTEM = "selectCineronSystem_";
    private static final String SELECT_CINERON_UNIT = "selectCineronUnit_";

    // Cineron
    public static Button getCineronButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + "planetAbilityExhaust_cineron",
                "Use Throne of Wrath",
                MiscEmojis.LegendaryPlanet);
    }

    public static List<Button> getCineronSystems(Player player, Game game) {
        List<Button> systems = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!FoWHelper.playerHasUnitsInSystem(player, tile)) {
                continue;
            }

            systems.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_CINERON_SYSTEM + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }

        return systems;
    }

    @ButtonHandler(SELECT_CINERON_SYSTEM)
    public static void getCineronUnits(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String tilePos = buttonID.replace(SELECT_CINERON_SYSTEM, "");
        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unable to find tile.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : holder.getUnits().keySet()) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker()
                                + SELECT_CINERON_UNIT
                                + tile.getPosition()
                                + "_"
                                + holder.getName()
                                + "_"
                                + unitKey.asyncID(),
                        holder.getName() + " - " + unitKey.humanReadableName(),
                        unitKey.unitEmoji()));
            }
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Select the unit you wish to destroy and add back to the board galvanized.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_CINERON_UNIT)
    public static void resolveCineron(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String[] payload = buttonID.replace(SELECT_CINERON_UNIT, "").split("_", 3);
        if (payload.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tilePos = payload[0];
        String holderName = payload[1];
        String asyncId = payload[2];

        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitHolder holder = tile.getUnitHolders().get(holderName);
        if (holder == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitKey unitKey = Mapper.getUnitKey(asyncId, player.getColorID());

        DestroyUnitService.destroyUnit(event, tile, game, unitKey, 1, holder, false);
        holder.addUnit(unitKey, 1);
        holder.addGalvanizedUnit(unitKey, 1);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                "Destroyed " + unitKey.humanReadableName() + " from " + tile.getRepresentation()
                        + " and placed it back, galvanized.");

        ButtonHelper.deleteMessage(event);
    }

    // Skarnath
    public static Button getSkarnathButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + "planetAbilityExhaust_skarnath",
                "Use Throne of Envy",
                MiscEmojis.LegendaryPlanet);
    }

    public static List<Button> getSkarnathSystems(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!FoWHelper.playerHasActualShipsInSystem(player, tile)) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + USE_SKARNATH + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }

        return buttons;
    }

    @ButtonHandler(USE_SKARNATH)
    public static void resolveSkarnath(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String tilePos = buttonID.replace(USE_SKARNATH, "");
        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null || !FoWHelper.playerHasActualShipsInSystem(player, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(SKARNATH_TARGET_SYSTEM + player.getFaction(), tilePos);
        game.removeStoredValue(SKARNATH_PRODUCED_SHIPS + player.getFaction());

        List<Button> productionButtons = new ArrayList<>();
        for (Button button : Helper.getPlaceUnitButtons(event, player, game, tile, "skarnathBuild", "place")) {
            String customId = button.getCustomId();
            int placeIndex = customId == null ? -1 : customId.indexOf("place_");
            if (placeIndex < 0) {
                continue;
            }
            String unitAndTile = customId.substring(placeIndex + "place_".length());
            int separatorIndex = unitAndTile.lastIndexOf('_');
            if (separatorIndex < 1) {
                continue;
            }
            String unitAsyncId = unitAndTile.substring(0, separatorIndex);
            UnitModel unit = player.getUnitFromUnitKey(
                    Mapper.getUnitKey(AliasHandler.resolveUnit(unitAsyncId.replace("2", "")), player.getColorID()));
            if (unit != null && unit.getIsShip() && !Character.isDigit(unitAsyncId.charAt(0))) {
                productionButtons.add(button.withCustomId(customId.replace("place_", PLACE_SKARNATH)));
            }
        }
        productionButtons.add(Buttons.red(
                player.factionButtonChecker() + "deleteButtons_skarnathBuild_" + tile.getPosition(),
                "Done Producing Units"));
        productionButtons.add(Buttons.gray(player.factionButtonChecker() + "resetProducedThings", "Reset Build"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Produce 2 different ships. Their cost is reduced by 2 if a neighbor owns both types.",
                productionButtons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_SKARNATH)
    public static void placeSkarnathShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String unitAndTile = buttonID.substring(PLACE_SKARNATH.length());
        int separatorIndex = unitAndTile.lastIndexOf('_');
        if (separatorIndex < 1) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        String unitAsyncId = unitAndTile.substring(0, separatorIndex);
        String tilePosition = unitAndTile.substring(separatorIndex + 1);
        Tile tile = game.getTileByPosition(tilePosition);
        UnitModel unit = player.getUnitFromUnitKey(
                Mapper.getUnitKey(AliasHandler.resolveUnit(unitAsyncId.replace("2", "")), player.getColorID()));
        Set<String> producedShips = new java.util.HashSet<>();
        String storedShips = game.getStoredValue(SKARNATH_PRODUCED_SHIPS + player.getFaction());
        if (!storedShips.isEmpty()) {
            producedShips.addAll(List.of(storedShips.split(",")));
        }
        if (tile == null
                || !tilePosition.equals(game.getStoredValue(SKARNATH_TARGET_SYSTEM + player.getFaction()))
                || unit == null
                || !unit.getIsShip()
                || Character.isDigit(unitAsyncId.charAt(0))
                || producedShips.contains(unitAsyncId)
                || producedShips.size() >= 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Skarnath can produce 2 different ships in its selected system.");
            return;
        }

        ButtonHelperModifyUnits.genericPlaceUnit("place_" + unitAndTile, event, game, player);
        producedShips.add(unitAsyncId);
        game.setStoredValue(SKARNATH_PRODUCED_SHIPS + player.getFaction(), String.join(",", producedShips));
    }

    public static int getSkarnathDiscount(Game game, Player player, Map<String, Integer> producedUnits) {
        if (producedUnits == null || producedUnits.isEmpty()) return 0;

        String targetSystem = game.getStoredValue(SKARNATH_TARGET_SYSTEM + player.getFaction());
        if (targetSystem.isEmpty()
                || producedUnits.keySet().stream().anyMatch(unit -> {
                    String[] unitParts = unit.split("_", 2);
                    return unitParts.length != 2 || !targetSystem.equals(unitParts[1]);
                })) {
            return 0;
        }

        Set<String> producedAliases = producedUnits.keySet().stream()
                .map(k -> k.split("_")[0])
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        if (producedAliases.size() != 2) return 0;

        for (Player neighbour : player.getNeighbouringPlayers(true)) {
            boolean neighbourHasAll = true;
            for (String alias : producedAliases) {
                int neighbourCount = ButtonHelper.getNumberOfUnitsOnTheBoard(game, neighbour, alias, false);
                if (neighbourCount < 1) {
                    neighbourHasAll = false;
                    break;
                }
            }
            if (neighbourHasAll) {
                return 2;
            }
        }
        return 0;
    }

    public static void clearSkarnathDiscount(Game game, Player player) {
        game.removeStoredValue(SKARNATH_TARGET_SYSTEM + player.getFaction());
        game.removeStoredValue(SKARNATH_PRODUCED_SHIPS + player.getFaction());
    }
}
