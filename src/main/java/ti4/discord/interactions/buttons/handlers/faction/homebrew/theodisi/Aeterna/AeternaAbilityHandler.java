package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Aeterna;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class AeternaAbilityHandler {
    private static final String FULL_MOON = "full_moonphase";
    private static final String WAXING_MOON = "waxing_moonphase";
    private static final String WANING_MOON = "waning_moonphase";
    private static final String NEW_MOON = "new_moonphase";
    private static final String LUNAR_ECLIPSE = "lunar_eclipse_moonphase";
    private static final String MOON_RETURN_COST = "moonReturnCost_";

    public static Button getWaxingMoonButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + "startMoonReturn_" + WAXING_MOON, "Use Waxing Moon");
    }

    public static Button getFullMoonButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + "startMoonReturn_" + FULL_MOON, "Use Full Moon");
    }

    public static Button getWaningMoonButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + "startMoonReturn_" + WANING_MOON, "Use Waning Moon");
    }

    public static Button getLunarEclipseButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + "startMoonReturn_" + LUNAR_ECLIPSE, "Use Lunar Eclipse");
    }

    public static Button getNewMoonButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + "newMoonChoose", "Use New Moon");
    }

    public static boolean canReturnCapturedNeutralUnits(Game game, Player player, int minimumCost) {
        return getCapturedNeutralUnits(game, player).stream()
                        .mapToInt(unitKey -> getUnitCost(game, unitKey)
                                * player.getNomboxTile().getSpaceUnitHolder().getUnitCount(unitKey))
                        .sum()
                >= minimumCost * 2;
    }

    @ButtonHandler("startMoonReturn_")
    public static void startMoonReturn(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String relic = buttonID.substring("startMoonReturn_".length());
        int minimumCost = getMoonReturnMinimumCost(relic);
        if (minimumCost == 0
                || !player.hasRelicReady(relic)
                || !canReturnCapturedNeutralUnits(game, player, minimumCost)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "This Moon Phase relic cannot be used right now.");
            return;
        }

        player.addExhaustedRelic(relic);
        game.setStoredValue(getMoonReturnCostKey(player, relic), "0");
        sendMoonReturnButtons(event, game, player, relic);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("returnMoonNeutral_")
    public static void returnMoonNeutral(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring("returnMoonNeutral_".length()).split("\\|", 2);
        if (parts.length != 2) return;

        String relic = parts[0];
        String storedCost = game.getStoredValue(getMoonReturnCostKey(player, relic));
        if (storedCost.isEmpty() || !player.getExhaustedRelics().contains(relic)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That Moon Phase effect is no longer pending.");
            return;
        }

        UnitKey unitKey = getCapturedNeutralUnits(game, player).stream()
                .filter(unit -> unit.asyncID().equals(parts[1]))
                .findFirst()
                .orElse(null);
        if (unitKey == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That captured neutral unit is no longer available.");
            return;
        }

        player.getNomboxTile().getSpaceUnitHolder().removeUnit(unitKey, 1);
        int totalCost = Integer.parseInt(storedCost) + getUnitCost(game, unitKey);
        int minimumCost = getMoonReturnMinimumCost(relic);
        if (totalCost < minimumCost * 2) {
            game.setStoredValue(getMoonReturnCostKey(player, relic), Integer.toString(totalCost));
            sendMoonReturnButtons(event, game, player, relic);
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.removeStoredValue(getMoonReturnCostKey(player, relic));
        ButtonHelper.deleteMessage(event);
        resolveMoonReturnEffect(event, game, player, relic);
    }

    @ButtonHandler("cancelMoonReturn_")
    public static void cancelMoonReturn(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String relic = buttonID.substring("cancelMoonReturn_".length());
        String paid = game.getStoredValue(getMoonReturnCostKey(player, relic));
        if (paid.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (Integer.parseInt(paid) > 0) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You have already returned a unit; finish returning the required combined cost.");
            return;
        }
        game.removeStoredValue(getMoonReturnCostKey(player, relic));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("fullMoonStructure_")
    public static void chooseFullMoonStructurePlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String structure = buttonID.substring("fullMoonStructure_".length());
        if (!List.of("sd", "pds").contains(structure)) return;

        List<Button> buttons = player.getPlanets().stream()
                .filter(planet -> game.getUnitHolderFromPlanet(planet) != null)
                .filter(planet -> !game.getUnitHolderFromPlanet(planet).isSpaceStation(game))
                .filter(planet -> !game.getUnitHolderFromPlanet(planet).getTokenList().stream()
                        .anyMatch(token -> token.contains("dmz")))
                .filter(planet -> "pds".equals(structure)
                        || game.getUnitHolderFromPlanet(planet).getUnitCount(UnitType.Spacedock, player) == 0)
                .sorted()
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "placeOneNDone_skipbuild_" + structure + "_" + planet,
                        Helper.getPlanetRepresentation(planet, game)))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "There are no eligible planets for that structure.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Choose a planet for the structure:", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("newMoonChoose")
    public static void chooseNewMoon(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.isActivePlayer() || !player.hasRelicReady(NEW_MOON)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "New Moon may only be used during your action.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        if (!getCapturedNeutralUnits(game, player).isEmpty()) {
            buttons.add(Buttons.green(player.factionButtonChecker() + "newMoonPlace", "Place a Captured Neutral Unit"));
        }
        buttons.add(Buttons.green(player.factionButtonChecker() + "newMoonCapture", "Capture 1 Neutral Cruiser"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Choose an effect for _New Moon_:", buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("newMoonCapture")
    public static void captureNeutralCruiser(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.isActivePlayer() || !player.hasRelicReady(NEW_MOON)) return;
        player.addExhaustedRelic(NEW_MOON);
        AddUnitService.addUnits(
                event, player.getNomboxTile(), game, game.getNeutral().getColor(), "1 cruiser");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " exhausted _New Moon_ and captured 1 neutral cruiser.");
        ButtonHelper.deleteMessage(event);
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }

    @ButtonHandler("newMoonPlace")
    public static void chooseNewMoonUnit(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.isActivePlayer() || !player.hasRelicReady(NEW_MOON)) return;
        List<Button> buttons = getCapturedNeutralUnits(game, player).stream()
                .map(unitKey -> Buttons.green(
                        player.factionButtonChecker() + "newMoonUnit_" + unitKey.asyncID(),
                        "Place " + unitKey.humanReadableName()))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Choose the captured neutral unit to place:", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("newMoonUnit_")
    public static void chooseNewMoonSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.isActivePlayer() || !player.hasRelicReady(NEW_MOON)) return;
        String unitID = buttonID.substring("newMoonUnit_".length());
        if (getCapturedNeutralUnits(game, player).stream()
                .noneMatch(unit -> unit.asyncID().equals(unitID))) return;

        List<Button> buttons = getNewMoonSystemButtons(game, player, unitID);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no eligible systems.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Choose a system for the neutral unit:",
                NewStuffHelper.buttonPagination(
                        buttons, player.factionButtonChecker() + "newMoonSystem_" + unitID + "|", 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("newMoonSystem_")
    public static void placeNewMoonUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.isActivePlayer() || !player.hasRelicReady(NEW_MOON)) return;
        String[] parts = buttonID.substring("newMoonSystem_".length()).split("\\|", 2);
        if (parts.length != 2) return;
        String message = "Choose a system for the neutral unit:";
        List<Button> systemButtons = getNewMoonSystemButtons(game, player, parts[0]);
        String buttonPrefix = player.factionButtonChecker() + "newMoonSystem_" + parts[0] + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), systemButtons, message, buttonPrefix, buttonID)) {
            return;
        }
        UnitKey unitKey = getCapturedNeutralUnits(game, player).stream()
                .filter(unit -> unit.asyncID().equals(parts[0]))
                .findFirst()
                .orElse(null);
        Tile tile = game.getTileByPosition(parts[1]);
        if (unitKey == null || tile == null || hasOtherPlayersShips(tile, game, player)) return;

        player.addExhaustedRelic(NEW_MOON);
        player.getNomboxTile().getSpaceUnitHolder().removeUnit(unitKey, 1);
        AddUnitService.addUnits(event, tile, game, game.getNeutral().getColor(), "1 " + unitKey.unitName());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " exhausted _New Moon_ and placed 1 neutral " + unitKey.humanReadableName()
                        + " in " + tile.getRepresentationForButtons(game, player) + ".");
        ButtonHelper.deleteMessage(event);
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }

    private static List<Button> getNewMoonSystemButtons(Game game, Player player, String unitID) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.getTileModel().isHyperlane())
                .filter(tile -> !hasOtherPlayersShips(tile, game, player))
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + "newMoonSystem_" + unitID + "|" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    public static void offerCycleOfReclamationCapture(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits, boolean combat) {
        if (destroyedUnits.isEmpty()) return;
        for (Player aeterna : game.getRealPlayers()) {
            if (!aeterna.hasAbility("cycle_of_reclamation") || (combat && aeterna == game.getActivePlayer())) continue;
            boolean nearby =
                    destroyedUnits.stream().anyMatch(unit -> isInOrAdjacentToAeternaUnits(game, aeterna, unit));
            if (!nearby) continue;

            AddUnitService.addUnits(
                    event, aeterna.getNomboxTile(), game, game.getNeutral().getColor(), "1 destroyer");
            MessageHelper.sendMessageToChannel(
                    aeterna.getCorrectChannel(),
                    aeterna.getRepresentation() + " captured 1 neutral destroyer with _Cycle of Reclamation_.");
        }
    }

    private static void sendMoonReturnButtons(ButtonInteractionEvent event, Game game, Player player, String relic) {
        List<Button> buttons = getCapturedNeutralUnits(game, player).stream()
                .map(unitKey -> Buttons.green(
                        player.factionButtonChecker() + "returnMoonNeutral_" + relic + "|" + unitKey.asyncID(),
                        "Return " + unitKey.humanReadableName() + " (" + formatCost(getUnitCost(game, unitKey)) + ")"))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        int paid = Integer.parseInt(game.getStoredValue(getMoonReturnCostKey(player, relic)));
        if (paid == 0) {
            buttons.add(Buttons.red(player.factionButtonChecker() + "cancelMoonReturn_" + relic, "Cancel"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", return captured neutral units worth " + getMoonReturnMinimumCost(relic)
                        + "+ total cost (currently " + formatCost(paid) + "): ",
                buttons);
    }

    private static void resolveMoonReturnEffect(ButtonInteractionEvent event, Game game, Player player, String relic) {
        switch (relic) {
            case FULL_MOON -> {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentation() + ", choose the structure to place due to _Full Moon_:",
                        List.of(
                                Buttons.green(
                                        player.factionButtonChecker() + "fullMoonStructure_sd", "Place 1 Space Dock"),
                                Buttons.green(player.factionButtonChecker() + "fullMoonStructure_pds", "Place 1 PDS")));
            }
            case WAXING_MOON -> {
                ActionCardHelper.drawActionCards(player, 2);
                ActionCardHelper.sendACDiscardButtons(player);
            }
            case WANING_MOON ->
                MessageHelper.sendMessageToChannelWithButton(
                        player.getCardsInfoThread(),
                        player.getRepresentation() + ", research 1 technology due to _Waning Moon_:",
                        Buttons.green(
                                player.factionButtonChecker() + "getAllTechOfType_allTechResearchable_noPay",
                                "Research a Technology"));
            case LUNAR_ECLIPSE ->
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentation() + ", gain 1 command token due to _Lunar Eclipse_:",
                        ButtonHelper.getGainCCButtons(player));
            default -> {}
        }
    }

    private static List<UnitKey> getCapturedNeutralUnits(Game game, Player player) {
        return player.getNomboxTile().getSpaceUnitHolder().getUnitKeys().stream()
                .filter(unitKey -> game.getPlayerFromColorOrFaction(unitKey.colorID()) == game.getNeutral())
                .filter(unitKey -> player.getNomboxTile().getSpaceUnitHolder().getUnitCount(unitKey) > 0)
                .sorted(Comparator.comparing(UnitKey::asyncID))
                .toList();
    }

    public static void clearMoonReturnStoredValues(Game game) {
        for (Player player : game.getRealPlayers()) {
            for (String relic : List.of(FULL_MOON, WAXING_MOON, WANING_MOON, LUNAR_ECLIPSE)) {
                game.removeStoredValue(getMoonReturnCostKey(player, relic));
            }
        }
    }

    private static int getUnitCost(Game game, UnitKey unitKey) {
        UnitModel unitModel = game.getNeutral().getUnitFromUnitKey(unitKey);
        if (unitModel != null) return Math.round(unitModel.getCost() * 2);
        return switch (unitKey.unitType()) {
            case Fighter, Infantry -> 1;
            case Destroyer -> 2;
            case Cruiser, Mech -> 4;
            case Carrier -> 6;
            case Dreadnought, Spacedock -> 8;
            case Pds -> 10;
            case Flagship -> 16;
            case Warsun -> 24;
            default -> 0;
        };
    }

    private static String formatCost(int halfCost) {
        return halfCost % 2 == 0 ? Integer.toString(halfCost / 2) : halfCost / 2 + ".5";
    }

    private static int getMoonReturnMinimumCost(String relic) {
        return switch (relic) {
            case FULL_MOON -> 3;
            case WAXING_MOON, LUNAR_ECLIPSE -> 2;
            case WANING_MOON -> 4;
            default -> 0;
        };
    }

    private static String getMoonReturnCostKey(Player player, String relic) {
        return MOON_RETURN_COST + player.getFaction() + "_" + relic;
    }

    private static boolean hasOtherPlayersShips(Tile tile, Game game, Player player) {
        return tile.getSpaceUnitHolder().getUnitKeys().stream().anyMatch(unitKey -> {
            Player owner = game.getPlayerFromColorOrFaction(unitKey.colorID());
            UnitModel model = owner == null ? null : owner.getUnitFromUnitKey(unitKey);
            return owner != null && owner != player && model != null && model.getIsShip();
        });
    }

    private static boolean isInOrAdjacentToAeternaUnits(Game game, Player player, RemovedUnit destroyedUnit) {
        Tile tile = destroyedUnit.tile();
        if (tile.containsPlayersUnits(player) || player.unitBelongsToPlayer(destroyedUnit.unitKey())) return true;
        return FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true).stream()
                .map(game::getTileByPosition)
                .anyMatch(adjacent -> adjacent != null && adjacent.containsPlayersUnits(player));
    }
}
