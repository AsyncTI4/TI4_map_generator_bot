package ti4.discord.interactions.buttons.handlers.explore.thirtyk;

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
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.PromissoryNoteModel;
import ti4.service.planet.PlanetService;
import ti4.service.transaction.SendPromissoryService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class ThirtyKCulturalExploreHandler {

    private static final String SHARED_WEALTH_DRAW = "thirtyKSharedWealthDraw_";
    private static final String DIRECT_IMPORT_MOVE = "thirtyKDirectImportMove_";
    private static final String INVESTMENT_PAY = "thirtyKInvestmentPay_";
    private static final String FAUX_GIVE = "thirtyKFauxGive_";

    public static void resolveSharedWealth(GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        List<Player> eligible = playersWithUnitsInOrAdjacent(game, player, tile);
        if (eligible.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "The colony opens its stores, but no player has units adjacent to this planet.");
            return;
        }
        MessageHelper.sendMessageToEventChannel(
                event,
                "The colony throws its stores open: each player with units adjacent to this planet may draw 1 action"
                        + " card (_Shared Wealth_).");
        for (Player other : eligible) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green(
                    other.factionButtonChecker() + SHARED_WEALTH_DRAW + player.getFaction(), "Draw 1 Action Card"));
            buttons.add(ThirtyKExploreSupport.decline(other));
            String message = other.getRepresentationUnfogged() + ", "
                    + FoWHelper.actorOrAnon(game, player, "a player") + " resolved _Shared Wealth_ next to your units."
                    + " You may draw 1 action card; if you do, they gain 1 trade good.";
            MessageHelper.sendMessageToChannelWithButtons(other.getCorrectChannel(), message, buttons);
        }
    }

    @ButtonHandler(SHARED_WEALTH_DRAW)
    public static void sharedWealthDraw(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player explorer = game.getPlayerFromColorOrFaction(buttonID.substring(SHARED_WEALTH_DRAW.length()));
        ActionCardHelper.drawActionCards(player, 1);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " accepts a share of the colony's stores and draws 1 action card (_Shared Wealth_).");
        if (explorer != null) {
            MessageHelper.sendMessageToChannel(
                    explorer.getCorrectChannel(),
                    explorer.getRepresentationUnfogged() + ", " + FoWHelper.actorOrAnon(game, player, "a player")
                            + " accepted your hospitality and drew 1 action card; you gain 1 trade good"
                            + " (_Shared Wealth_).");
            ButtonHelperStats.gainTGs(event, game, explorer, 1, false);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveTourismBureau(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, String planetID) {
        int count = 0;
        for (Tile adjacent : ThirtyKExploreSupport.tilesInOrAdjacent(game, player, tile)) {
            count += adjacent.getWormholes(game).size();
            if (adjacent.isAnomaly(game, player)) count++;
            for (Planet planet : adjacent.getPlanetUnitHolders()) {
                if (planet.isLegendary() && !planet.getName().equals(planetID)) count++;
            }
        }
        MessageHelper.sendMessageToEventChannel(event, tourismMessage(count));
        if (count > 0) ButtonHelperStats.gainComms(event, game, player, count, false);
    }

    public static void resolveDirectImport(GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        ButtonHelperStats.gainComms(event, game, player, 1, false);
        if (tile == null) return;
        List<Button> buttons = directImportButtons(game, player, tile);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "The shipment arrives unescorted: you have no ships in adjacent systems to move.");
            return;
        }
        NewStuffHelper.sendPaginated(
                event.getMessageChannel(),
                directImportMessage(player),
                buttons,
                List.of(ThirtyKExploreSupport.decline(player)),
                directImportNav(player, tile));
    }

    private static List<Button> directImportButtons(Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        for (Tile adjacent : ThirtyKExploreSupport.tilesInOrAdjacent(game, player, tile)) {
            if (adjacent == tile) continue;
            for (UnitKey key : adjacent.getSpaceUnitHolder().getUnitKeysForPlayer(player)) {
                if (!ThirtyKExploreSupport.isShip(player, key)) continue;
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + DIRECT_IMPORT_MOVE + adjacent.getPosition() + "_"
                                + tile.getPosition() + "_" + key.unitType().getValue(),
                        "Move 1 " + key.unitType().humanReadableName() + " From "
                                + adjacent.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    private static String directImportMessage(Player player) {
        return player.getRepresentation()
                + ", you may move up to 1 of your ships from an adjacent system into this system.";
    }

    private static String directImportNav(Player player, Tile tile) {
        return player.factionButtonChecker() + DIRECT_IMPORT_MOVE + tile.getPosition() + "_";
    }

    @ButtonHandler(DIRECT_IMPORT_MOVE)
    public static void directImportMove(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(DIRECT_IMPORT_MOVE.length()).split("_");
        Tile anchor = game.getTileByPosition(parts[0]);
        if (anchor != null
                && NewStuffHelper.turnPage(
                        event,
                        buttonID,
                        directImportMessage(player),
                        () -> directImportButtons(game, player, anchor),
                        List.of(ThirtyKExploreSupport.decline(player)),
                        directImportNav(player, anchor))) {
            return;
        }
        if (parts.length < 3) return;
        Tile from = game.getTileByPosition(parts[0]);
        Tile to = game.getTileByPosition(parts[1]);
        UnitType type = Units.findUnitType(parts[2]);
        if (from == null || to == null || type == null) return;
        List<RemovedUnit> removed =
                RemoveUnitService.removeUnit(event, from, game, player, from.getSpaceUnitHolder(), type, 1, false);
        if (removed.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "That ship is no longer there.");
            return;
        }
        AddUnitService.addUnits(
                event,
                game,
                removed.stream()
                        .map(r -> r.onUnitHolder(to.getSpaceUnitHolder()))
                        .toList());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " escorts the import convoy in: 1 " + type.humanReadableName()
                        + " moves into " + to.getRepresentationForButtons(game, player) + " (_Direct Import_).");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveInitialInvestmentSurplus(
            GenericInteractionCreateEvent event, Game game, Player player, String planetID) {
        List<Button> buttons = new ArrayList<>();
        if (player.getCommodities() > 0) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + INVESTMENT_PAY + "comm_" + planetID, "Spend 1 Commodity"));
        }
        if (player.getTg() > 0) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + INVESTMENT_PAY + "tg_" + planetID, "Spend 1 Trade Good"));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "The investors are willing, but you have no commodities or trade goods to spend (_Initial"
                            + " Investment Surplus_).");
            return;
        }
        buttons.add(ThirtyKExploreSupport.decline(player));
        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                player.getRepresentation() + ", you may spend 1 commodity or trade good to ready "
                        + Helper.getPlanetRepresentation(planetID, game) + ".",
                buttons);
    }

    @ButtonHandler(INVESTMENT_PAY)
    public static void investmentPay(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(INVESTMENT_PAY.length()).split("_", 2);
        boolean commodity = "comm".equals(parts[0]);
        String planetID = parts[1];
        if (commodity && player.getCommodities() > 0) {
            player.setCommodities(player.getCommodities() - 1);
        } else if (!commodity && player.getTg() > 0) {
            player.setTg(player.getTg() - 1);
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You have nothing left to spend.");
            return;
        }
        PlanetService.refreshPlanet(player, planetID);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " reinvests 1 " + (commodity ? "commodity" : "trade good")
                        + " and " + Helper.getPlanetRepresentation(planetID, game)
                        + " is back at work (_Initial Investment Surplus_).");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveCrossSpeciesMingling(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, String planetID) {
        int planets = 0;
        for (Tile nearby : ThirtyKExploreSupport.tilesInOrAdjacent(game, player, tile)) {
            for (Planet planet : nearby.getPlanetUnitHolders()) {
                if (hasForeignInfantry(player, planet)) planets++;
            }
        }
        int infantry = planets + 1;
        if (tile == null || planetID == null) return;
        AddUnitService.addUnits(event, tile, game, player.getColor(), infantry + " inf " + planetID);
        MessageHelper.sendMessageToEventChannel(
                event,
                "Settlers of many species join the colony: " + player.getRepresentationNoPing() + " places "
                        + infantry + " infantry on " + Helper.getPlanetRepresentation(planetID, game)
                        + " (_Cross-species Mingling_).");
    }

    private static String tourismMessage(int count) {
        if (count == 0) return "No wonders nearby draw a single tourist (_Tourism Bureau_).";
        return "Tourists flock to " + count + " nearby wonder" + (count == 1 ? "" : "s")
                + " (wormholes, anomalies and legendary planets): gain " + count + " commodit"
                + (count == 1 ? "y" : "ies") + " (_Tourism Bureau_).";
    }

    private static boolean hasForeignInfantry(Player player, Planet planet) {
        for (UnitKey key : planet.getUnitKeys()) {
            if (key.unitType() == UnitType.Infantry
                    && !player.unitBelongsToPlayer(key)
                    && planet.getUnitCount(key) > 0) {
                return true;
            }
        }
        return false;
    }

    public static void resolveFauxAltruisticFestivals(GenericInteractionCreateEvent event, Game game, Player player) {
        for (Player neighbor : player.getNeighbouringPlayers(false)) {
            List<Button> buttons = new ArrayList<>();
            for (Map.Entry<String, Integer> note : neighbor.getPromissoryNotes().entrySet()) {
                if (neighbor.getPromissoryNotesInPlayArea().contains(note.getKey())) continue;
                PromissoryNoteModel model = Mapper.getPromissoryNote(note.getKey());
                if (model == null) continue;
                buttons.add(Buttons.green(
                        neighbor.factionButtonChecker() + FAUX_GIVE + player.getFaction() + "_" + note.getValue(),
                        "Give " + model.getName()));
            }
            if (buttons.isEmpty()) continue;
            List<Button> choices = new ArrayList<>(buttons);
            choices.add(ThirtyKExploreSupport.decline(neighbor));
            MessageHelper.sendMessageToChannelWithButtons(
                    neighbor.getCardsInfoThread(),
                    neighbor.getRepresentationUnfogged() + ", " + FoWHelper.actorOrAnon(game, player, "a neighbor")
                            + " resolved _Faux Altruistic Festivals_. You may give them a promissory note from your"
                            + " hand; if you do, you draw 1 action card.",
                    choices);
        }
        ActionCardHelper.drawActionCards(player, 1);
        MessageHelper.sendMessageToEventChannel(
                event,
                "The festival opens to great fanfare: " + player.getRepresentationNoPing()
                        + " draws 1 action card, and each neighbor may bring a promissory note as a gift (_Faux"
                        + " Altruistic Festivals_).");
    }

    @ButtonHandler(FAUX_GIVE)
    public static void fauxGive(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(FAUX_GIVE.length()).split("_");
        Player explorer = game.getPlayerFromColorOrFaction(parts[0]);
        int number = Integer.parseInt(parts[1]);
        String noteID = player.getPromissoryNotes().entrySet().stream()
                .filter(entry -> entry.getValue() == number)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (explorer == null || noteID == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "That promissory note is no longer in hand.");
            return;
        }
        SendPromissoryService.sendPromissoryToPlayer(event, game, player, explorer, noteID);
        ActionCardHelper.drawActionCards(player, 1);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " brings a promissory note to the festival and draws 1 action card in return (_Faux"
                        + " Altruistic Festivals_).");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Player> playersWithUnitsInOrAdjacent(Game game, Player player, Tile tile) {
        List<Tile> tiles = ThirtyKExploreSupport.tilesInOrAdjacent(game, player, tile);
        List<Player> players = new ArrayList<>();
        for (Player other : game.getRealPlayers()) {
            if (tiles.stream().anyMatch(nearby -> FoWHelper.playerHasUnitsInSystem(other, nearby))) {
                players.add(other);
            }
        }
        return players;
    }
}
