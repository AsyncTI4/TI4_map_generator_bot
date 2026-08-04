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
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
public class ThronesThroneHandler {
    private static final String USE_SKARNATH = "useSkarnathAbility_";
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
                "Select the unit you wish to remove and add back to the board galvanized.",
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

        holder.removeUnit(unitKey, 1);
        holder.addUnit(unitKey, 1);
        holder.addGalvanizedUnit(unitKey, 1);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                "Removed " + unitKey.humanReadableName() + " from " + tile.getRepresentation()
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

        game.setStoredValue("skarnathTargetSystem_" + player.getFaction(), tilePos);

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "You may produce 2 different units. Their cost is discounted by the number of units your neighbor owns of both those types.",
                Helper.getPlaceUnitButtons(event, player, game, tile, "skarnathBuild", "place"));

        ButtonHelper.deleteMessage(event);
    }

    public static int getSkarnathDiscount(Game game, Player player, Map<String, Integer> producedUnits) {
        if (producedUnits == null || producedUnits.isEmpty()) return 0;

        Set<String> producedAliases = producedUnits.keySet().stream()
                .map(k -> k.split("_")[0])
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        if (producedAliases.isEmpty()) return 0;

        int discount = 0;
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
                discount++;
            }
        }
        return discount;
    }
}
