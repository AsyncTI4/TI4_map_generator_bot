package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

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
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class SarcosaBreakthroughHandler {
    private static final String USE_GRAVEHOLD = "useGravehold";
    private static final String SELECT_GRAVEHOLD_PLANET = "selectGraveholdPlanet_";
    private static final String PLACE_GRAVEHOLD_STRUCTURE = "placeGraveholdStructure_";

    public static Button getGraveholdButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + USE_GRAVEHOLD, "Use Gravehold", FactionEmojis.sarcosa);
    }

    @ButtonHandler(USE_GRAVEHOLD)
    public static void useGravehold(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasReadyBreakthrough("sarcosabt")) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        List<Button> planetButtons = getGraveholdPlanetButtons(game, player);
        if (planetButtons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You have no units on an eligible planet.");
            return;
        }

        String message = player.getRepresentationNoPing() + ", choose a planet containing your units for _Gravehold_.";
        String prefix = player.factionButtonChecker() + SELECT_GRAVEHOLD_PLANET;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(planetButtons, prefix, 0));
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_GRAVEHOLD_PLANET)
    public static void selectGraveholdPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> planetButtons = getGraveholdPlanetButtons(game, player);
        String message = player.getRepresentationNoPing() + ", choose a planet containing your units for _Gravehold_.";
        String prefix = player.factionButtonChecker() + SELECT_GRAVEHOLD_PLANET;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), planetButtons, message, prefix, buttonID)) {
            return;
        }

        String planetName = buttonID.substring(SELECT_GRAVEHOLD_PLANET.length());
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (!player.hasReadyBreakthrough("sarcosabt")
                || planet == null
                || !FoWHelper.playerHasUnitsOnPlanet(player, planet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + PLACE_GRAVEHOLD_STRUCTURE + planetName + "|pds",
                        "Place Neutral PDS",
                        UnitType.Pds.getUnitTypeEmoji()),
                Buttons.green(
                        player.factionButtonChecker() + PLACE_GRAVEHOLD_STRUCTURE + planetName + "|sd",
                        "Place Neutral Space Dock",
                        UnitType.Spacedock.getUnitTypeEmoji()));

        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose the neutral structure to place on "
                        + Helper.getPlanetRepresentation(planetName, game) + ".",
                buttons);
    }

    @ButtonHandler(PLACE_GRAVEHOLD_STRUCTURE)
    public static void placeGraveholdStructure(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(PLACE_GRAVEHOLD_STRUCTURE.length()).split("\\|", 2);
        if (payload.length != 2 || !List.of("pds", "sd").contains(payload[1])) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String planetName = payload[0];
        String structure = payload[1];
        Player neutral = game.getNeutral();
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        UnitType unitType = "pds".equals(structure) ? UnitType.Pds : UnitType.Spacedock;

        if (!player.hasReadyBreakthrough("sarcosabt")
                || neutral == null
                || planet == null
                || tile == null
                || !FoWHelper.playerHasUnitsOnPlanet(player, planet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitKey neutralStructure = new UnitKey(unitType, neutral.getColorID());
        int before = planet.getUnitCount(neutralStructure);
        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, tile, game, neutral.getColor(), structure + " " + planetName);
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", coexistenceFlag);
            }
        }

        if (planet.getUnitCount(neutralStructure) <= before) {
            return;
        }

        player.setBreakthroughExhausted("sarcosabt", true);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed a neutral "
                        + (unitType == UnitType.Pds ? "PDS" : "space dock")
                        + " into coexistence on " + Helper.getPlanetRepresentation(planetName, game)
                        + " using _Gravehold_.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean canControlNeutralStructure(Game game, Player player, UnitHolder holder, UnitKey unitKey) {
        Player neutral = game.getNeutral();
        if (!hasGraveholdUnlocked(game)
                || game.getActivePlayer() != player
                || neutral == null
                || !(holder instanceof Planet)
                || !neutral.getColorID().equals(unitKey.colorID())
                || !FoWHelper.playerHasUnitsOnPlanet(player, holder)) {
            return false;
        }

        UnitModel unit = neutral.getUnitFromUnitKey(unitKey);
        return unit != null && unit.getIsStructure();
    }

    private static boolean hasGraveholdUnlocked(Game game) {
        return game.getRealPlayers().stream().anyMatch(player -> player.hasUnlockedBreakthrough("sarcosabt"));
    }

    public static UnitModel getControlledNeutralStructureModel(
            Game game, Player player, UnitHolder holder, UnitKey unitKey) {
        if (!canControlNeutralStructure(game, player, holder, unitKey)) {
            return null;
        }
        return player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
    }

    public static boolean hasGraveholdSpaceCannonCoverage(Game game, Player player, Tile targetTile) {
        if (game == null || player == null || targetTile == null || game.getActivePlayer() != player) {
            return false;
        }
        for (String position : FoWHelper.getAdjacentTiles(game, targetTile.getPosition(), player, false, true)) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null || tile.isScar(game)) {
                continue;
            }
            boolean sameTile = targetTile.getPosition().equals(position);
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (var entry : holder.getUnits().entrySet()) {
                    if (entry.getValue() < 1 || !canControlNeutralStructure(game, player, holder, entry.getKey())) {
                        continue;
                    }
                    UnitModel unit = getControlledNeutralStructureModel(game, player, holder, entry.getKey());
                    if (unit != null
                            && unit.getSpaceCannonDieCount(player) > 0
                            && (sameTile
                                    || unit.getDeepSpaceCannon(player)
                                    || game.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<Button> getGraveholdPlanetButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (FoWHelper.playerHasUnitsOnPlanet(player, planet)) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + SELECT_GRAVEHOLD_PLANET + planet.getName(),
                            Helper.getPlanetRepresentation(planet.getName(), game)));
                }
            }
        }
        return buttons;
    }
}
