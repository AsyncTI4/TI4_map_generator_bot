package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Thrones;

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
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.service.combat.CombatRollType;
import ti4.service.commodities.CommodityConversionService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;

@UtilityClass
public class ThronesUnitHandler {
    private static final String PLACE_AURELION = "placeAurelion_";
    private static final String CONVERT_COMMODITIES_WITH_AURELION = "convertCommoditiesWithAurelion";
    private static final String AURELION = "aurelion";
    private static final String AURELION_STATION = "aurelionstation";
    private static final String THRONES_AURELION = "thrones_aurelion";
    private static final String THRONES_MECH = "thrones_mech";
    private static final String USE_GHOLA = "useGhola_";
    private static final String DESTROY_GHOLA = "destroyGhola_";
    private static final String GHOLA_ROLL_BONUS = "thronesGholaRollBonus_";

    public static Button getAurelionCommodityConversionButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + CONVERT_COMMODITIES_WITH_AURELION,
                "Convert Commodities With Aurelion",
                UnitEmojis.flagship);
    }

    @ButtonHandler(CONVERT_COMMODITIES_WITH_AURELION)
    public static void convertCommoditiesWithAurelion(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasPlanet(AURELION_STATION) || player.getExhaustedPlanets().contains(AURELION_STATION)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int commodities = player.getCommodities();
        player.exhaustPlanet(AURELION_STATION);
        CommodityConversionService.convertAllComm(event, player, game);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " exhausted " + Helper.getPlanetRepresentation(AURELION_STATION, game)
                        + ", washing their commodit"
                        + (commodities == 1 ? "y" : "ies")
                        + ".");
    }

    public static void syncAurelionStation(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        if (isAurelionOnBoard(game, player)) {
            player.addPlanet(AURELION_STATION);
        } else {
            player.removePlanet(AURELION_STATION);
        }
    }

    public static void offerAurelionPlacement(Game game, Player player) {
        List<Button> buttons = getAurelionPlacementButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " has no system containing a planet they control to place _Aurelion_.");
            return;
        }

        String message = player.getRepresentation()
                + ", please choose a system containing a planet you control on which to place an Aurelion.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_AURELION;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
    }

    @ButtonHandler(PLACE_AURELION)
    public static void placeAurelion(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.ownsUnit(THRONES_AURELION) || isAurelionOnBoard(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getAurelionPlacementButtons(game, player);
        String message = player.getRepresentation()
                + ", please choose a system containing a planet you control on which to place an Aurelion.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_AURELION;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        String position = buttonID.substring(PLACE_AURELION.length());
        Tile tile = game.getTileByPosition(position);
        boolean controlsPlanetInTile =
                player.getPlanets().stream().anyMatch(planet -> tile == game.getTileFromPlanet(planet));
        if (tile == null || !controlsPlanetInTile) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), AURELION);
        ButtonHelper.deleteMessage(event);
    }

    private static boolean isAurelionOnBoard(Game game, Player player) {
        UnitKey aurelion = Units.getUnitKey(UnitType.Aurelion, player.getColorID());
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .anyMatch(unitHolder -> unitHolder.getUnitCount(aurelion) > 0);
    }

    private static List<Button> getAurelionPlacementButtons(Game game, Player player) {
        List<Tile> eligibleTiles = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Tile tile = game.getTileFromPlanet(planet);
            if (tile != null && !eligibleTiles.contains(tile)) {
                eligibleTiles.add(tile);
            }
        }

        return eligibleTiles.stream()
                .map(tile -> Buttons.red(
                        player.factionButtonChecker() + PLACE_AURELION + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player),
                        UnitEmojis.flagship))
                .toList();
    }

    public static void addGholaButton(List<Button> buttons, Game game, Player player, Tile tile, String groundOrSpace) {
        if (buttons == null
                || game == null
                || player == null
                || tile == null
                || !player.ownsUnit(THRONES_MECH)
                || !game.getStoredValue(GHOLA_ROLL_BONUS + player.getFaction()).isEmpty()) {
            return;
        }

        if (!hasGholaOnBoard(game, player)) {
            return;
        }

        buttons.add(Buttons.red(
                player.factionButtonChecker() + USE_GHOLA + tile.getPosition() + "|" + groundOrSpace,
                "Use Ghola",
                FactionEmojis.thrones));
    }

    @ButtonHandler(USE_GHOLA)
    public static void offerGholaDestructionButtons(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }
        String[] payload = buttonID.substring(USE_GHOLA.length()).split("\\|", 2);
        Tile combatTile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        if (game == null
                || player == null
                || !player.ownsUnit(THRONES_MECH)
                || combatTile == null
                || (!Constants.SPACE.equalsIgnoreCase(payload.length == 2 ? payload[1] : "")
                        && !"ground".equalsIgnoreCase(payload.length == 2 ? payload[1] : ""))
                || !hasGholaOnBoard(game, player)
                || !game.getStoredValue(GHOLA_ROLL_BONUS + player.getFaction()).isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Tile mechTile : game.getTileMap().values()) {
            for (UnitHolder mechHolder : mechTile.getUnitHolders().values()) {
                for (UnitKey unitKey : mechHolder.getUnitKeysForPlayer(player)) {
                    if (unitKey.unitType() != UnitType.Mech) {
                        continue;
                    }
                    for (UnitState state : mechHolder.getNonZeroUnitStates(unitKey)) {
                        int count = mechHolder.getUnitCountForState(unitKey, state);
                        String stateText =
                                switch (state) {
                                    case dmg -> "damaged ";
                                    case glv -> "galvanized ";
                                    case dmg_glv -> "damaged galvanized ";
                                    default -> "";
                                };
                        buttons.add(Buttons.red(
                                player.factionButtonChecker() + DESTROY_GHOLA + combatTile.getPosition() + "|"
                                        + payload[1] + "|" + mechTile.getPosition() + "|"
                                        + mechHolder.getName() + "|" + unitKey.asyncID() + "|" + state,
                                "Destroy 1 " + stateText + "Ghola on "
                                        + Helper.getUnitHolderRepresentation(
                                                mechTile, mechHolder.getName(), game, player)
                                        + " (" + count + ")",
                                unitKey.unitEmoji()));
                    }
                }
            }
        }
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", choose a Ghola to destroy. Their next roll in this combat gains +3 to its best die.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(DESTROY_GHOLA)
    public static void destroyGholaForRollBonus(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }
        String[] payload = buttonID.substring(DESTROY_GHOLA.length()).split("\\|", 6);
        Tile combatTile = payload.length == 6 ? game.getTileByPosition(payload[0]) : null;
        Tile mechTile = payload.length == 6 ? game.getTileByPosition(payload[2]) : null;
        UnitHolder mechHolder =
                mechTile == null ? null : mechTile.getUnitHolders().get(payload[3]);
        UnitKey mechKey = mechHolder == null
                ? null
                : mechHolder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.unitType() == UnitType.Mech)
                        .filter(key -> key.asyncID().equals(payload[4]))
                        .findFirst()
                        .orElse(null);
        UnitState state = payload.length == 6 ? Units.findUnitState(payload[5]) : null;
        if (game == null
                || player == null
                || !player.ownsUnit(THRONES_MECH)
                || combatTile == null
                || (!Constants.SPACE.equalsIgnoreCase(payload.length == 6 ? payload[1] : "")
                        && !"ground".equalsIgnoreCase(payload.length == 6 ? payload[1] : ""))
                || mechHolder == null
                || mechKey == null
                || state == null
                || mechHolder.getUnitCountForState(mechKey, state) < 1
                || !game.getStoredValue(GHOLA_ROLL_BONUS + player.getFaction()).isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        DestroyUnitService.destroyUnit(
                event, mechTile, game, new ParsedUnit(mechKey, 1, mechHolder.getName()), true, state);
        game.setStoredValue(GHOLA_ROLL_BONUS + player.getFaction(), combatTile.getPosition() + "|" + payload[1]);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " destroyed a Ghola. +3 will apply to the best die of their next roll in this combat.");
        ButtonHelper.deleteMessage(event);
    }

    public static void addGholaNextRollModifier(
            List<NamedCombatModifierModel> modifiers,
            Game game,
            Player player,
            Tile tile,
            UnitHolder holder,
            CombatRollType rollType) {
        if (modifiers == null || game == null || player == null || tile == null || holder == null || rollType == null) {
            return;
        }
        String key = GHOLA_ROLL_BONUS + player.getFaction();
        String[] context = game.getStoredValue(key).split("\\|", 2);
        if (context.length != 2
                || !tile.getPosition().equals(context[0])
                || (Constants.SPACE.equalsIgnoreCase(context[1]) != Constants.SPACE.equals(holder.getName()))) {
            return;
        }

        CombatModifierModel modifier = new CombatModifierModel();
        modifier.setAlias("thrones_ghola");
        modifier.setType(Constants.COMBAT_MODIFIERS);
        modifier.setValue(3);
        modifier.setMaxDice(1);
        modifier.setPersistenceType("ALWAYS");
        modifier.setScope("");
        modifier.setRelated(List.of());
        modifier.setForCombatAbility(rollType);
        modifiers.add(new NamedCombatModifierModel(modifier, "_Ghola_"));
        game.removeStoredValue(key);
    }

    public static void clearGholaRollBonus(Game game) {
        if (game == null) {
            return;
        }
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(GHOLA_ROLL_BONUS))
                .toList()
                .forEach(game::removeStoredValue);
    }

    private static boolean hasGholaOnBoard(Game game, Player player) {
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .anyMatch(holder -> holder.getUnitCount(UnitType.Mech, player) > 0);
    }
}
