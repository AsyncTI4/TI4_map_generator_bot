package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
import ti4.helpers.NewStuffHelper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class BelkoseaBreakthroughHandler {
    private static final String TRINITY_STOCKPILE = "belkoseabt";
    private static final String SELECT_TARGET = "selectTrinityStockpileTarget_";

    public static Button getTrinityStockpileButton(Game game, Player player) {
        if (!canUseTrinityStockpile(game, player)) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + "belkoseaTrinityStockpile",
                "Use Trinity Stockpile",
                FactionEmojis.belkosea);
    }

    @ButtonHandler("belkoseaTrinityStockpile")
    public static void chooseTrinityStockpileTarget(ButtonInteractionEvent event, Game game, Player player) {
        if (!canUseTrinityStockpile(game, player)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        String message = player.getRepresentationNoPing()
                + ", choose a planet for _Trinity Stockpile_. All units on it will be destroyed and its controller "
                + "will exhaust it, if able.";
        List<Button> buttons = getTrinityStockpileTargetButtons(game, player);
        String prefix = player.factionButtonChecker() + SELECT_TARGET;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, prefix, 0));
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_TARGET)
    public static void resolveTrinityStockpile(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String message = player.getRepresentationNoPing()
                + ", choose a planet for _Trinity Stockpile_. All units on it will be destroyed and its controller "
                + "will exhaust it, if able.";
        List<Button> buttons = getTrinityStockpileTargetButtons(game, player);
        String prefix = player.factionButtonChecker() + SELECT_TARGET;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        String planet = buttonID.substring(SELECT_TARGET.length());
        Tile tile = game.getTileFromPlanet(planet);
        UnitHolder unitHolder = game.getUnitHolderFromPlanet(planet);
        if (!canUseTrinityStockpile(game, player)
                || !getEligiblePlanetNames(game, player).contains(planet)
                || tile == null
                || unitHolder == null
                || game.isFowMode() && !FoWHelper.knowsTile(game, player, tile.getPosition())) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        reduceCommandTokenLimit(game, player);
        BreakthroughCommandHelper.exhaustBreakthrough(player, TRINITY_STOCKPILE);
        DestroyUnitService.destroyAllUnits(event, tile, game, unitHolder, false);

        Player controller = game.getPlayerThatControlsPlanet(planet, true);
        if (controller != null) {
            controller.exhaustPlanet(planet);
        }

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " purged 1 command token from their strategy pool and resolved _Trinity Stockpile_ on "
                        + Helper.getPlanetRepresentation(planet, game)
                        + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static boolean canUseTrinityStockpile(Game game, Player player) {
        return player.hasReadyBreakthrough(TRINITY_STOCKPILE)
                && player.getStrategicCC() > 0
                && !getEligiblePlanetNames(game, player).isEmpty();
    }

    private static void reduceCommandTokenLimit(Game game, Player player) {
        player.setStrategicCC(player.getStrategicCC() - 1);
        int relicBonus = player.hasRelic("endurance_steroids") ? 2 : 0;
        int reducedBaseLimit = player.getCommandTokenLimit() - relicBonus - 1;
        game.setStoredValue("ccLimit" + player.getColor(), Integer.toString(reducedBaseLimit));
    }

    private static List<Button> getTrinityStockpileTargetButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : getEligiblePlanetNames(game, player)) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + SELECT_TARGET + planet,
                    "Destroy units on " + Helper.getPlanetRepresentation(planet, game)));
        }
        return buttons;
    }

    private static Set<String> getEligiblePlanetNames(Game game, Player player) {
        Set<String> adjacentToHome = new LinkedHashSet<>();
        Tile homeSystem = player.getHomeSystemTile();
        if (homeSystem != null) {
            adjacentToHome.addAll(FoWHelper.getAdjacentTiles(game, homeSystem.getPosition(), player, false));
        }

        Set<String> planets = new LinkedHashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            if (game.isFowMode() && !FoWHelper.knowsTile(game, player, tile.getPosition())) {
                continue;
            }
            boolean isAdjacentToHome = adjacentToHome.contains(tile.getPosition());
            for (UnitHolder unitHolder : tile.getPlanetUnitHolders()) {
                boolean hasSuperweapon =
                        unitHolder.getTokenList().stream().anyMatch(token -> token.contains("superweapon"));
                if (isAdjacentToHome || hasSuperweapon) {
                    planets.add(unitHolder.getName());
                }
            }
        }
        return planets;
    }
}
