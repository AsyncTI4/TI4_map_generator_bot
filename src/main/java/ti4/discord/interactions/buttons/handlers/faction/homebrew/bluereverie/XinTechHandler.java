package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

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
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.agenda.IsPlayerElectedService;

@UtilityClass
public class XinTechHandler {
    private static final String ASTROMANTIC_CLOAK_STAR = "astromanticCloakStar_";
    private static final String USE_VEILED_NETWORKING_STEEL = "useVeiledNetworkingSteel_";
    private static final String DECLINE_VEILED_NETWORKING_STEEL = "declineVeiledNetworkingSteel_";
    private static final String VEILED_NETWORKING_STAR_PRODUCE = "xinVeiledNetworkingStarProduce_";
    private static final String VEILED_NETWORKING_STAR_CONTEXT = "veiledNetworkingStarContext";

    public static boolean hasAstromanticCloakSteel(Player player) {
        return player != null && player.hasTech("dsxingsteel");
    }

    public static boolean hasAstromanticCloakStar(Player player) {
        return player != null && player.hasTech("dsxingstar");
    }

    public static boolean hasVeiledNetworkingSteel(Player player) {
        return player != null && player.hasTech("dsxinysteel");
    }

    public static boolean hasVeiledNetworkingStar(Player player) {
        return player != null && player.hasTech("dsxinystar");
    }

    public static List<Button> getVeiledNetworkingStarProductionButtons(
            Game game, Player player, Tile source, String productionContext, String placePrefix) {
        if (!hasBlockadedProduction(player, game, source)) {
            return List.of();
        }

        List<Button> buttons = new ArrayList<>();
        for (String destinationPosition : FoWHelper.getAdjacentTiles(game, source.getPosition(), player, false, true)) {
            Tile destination = game.getTileByPosition(destinationPosition);
            if (destination == null
                    || !destination.hasPlayerCC(player)
                    || FoWHelper.otherPlayersHaveShipsInSystem(player, destination, game)) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + VEILED_NETWORKING_STAR_PRODUCE + source.getPosition() + "|"
                            + destination.getPosition(),
                    "Produce Ships in " + destination.getRepresentationForButtons(game, player)));
        }
        if (!buttons.isEmpty()) {
            game.setStoredValue(
                    VEILED_NETWORKING_STAR_CONTEXT + player.getFaction(),
                    source.getPosition() + "|" + productionContext + "|" + placePrefix);
        } else {
            clearVeiledNetworkingStarProduction(game, player);
        }
        return buttons;
    }

    public static int getVeiledNetworkingStarProductionValue(Player player, Game game, Tile destination) {
        String[] context = getVeiledNetworkingStarProductionContext(game, player);
        String sourcePosition = context.length == 3 ? context[0] : "";
        Tile source = game.getTileByPosition(sourcePosition);
        if (source == null
                || destination == null
                || !isEligibleVeiledNetworkingStarProduction(game, player, source, destination)) {
            return 0;
        }
        return Helper.getProductionValue(player, game, source, false);
    }

    public static void clearVeiledNetworkingStarProduction(Game game, Player player) {
        game.removeStoredValue(VEILED_NETWORKING_STAR_CONTEXT + player.getFaction());
    }

    @ButtonHandler(VEILED_NETWORKING_STAR_PRODUCE)
    public static void produceWithVeiledNetworkingStar(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] positions =
                buttonID.substring(VEILED_NETWORKING_STAR_PRODUCE.length()).split("\\|", 2);
        Tile source = positions.length == 2 ? game.getTileByPosition(positions[0]) : null;
        Tile destination = positions.length == 2 ? game.getTileByPosition(positions[1]) : null;
        if (!isEligibleVeiledNetworkingStarProduction(game, player, source, destination)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That Veiled Networking production is no longer eligible.");
            return;
        }

        String[] context = getVeiledNetworkingStarProductionContext(game, player);
        if (context.length != 3 || !source.getPosition().equals(context[0])) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That Veiled Networking production has expired.");
            return;
        }
        List<Button> buttons =
                Helper.getPlaceUnitButtons(event, player, game, destination, context[1], context[2]).stream()
                        .filter(button -> isShipProductionButton(player, button, destination, context[2])
                                || "Done Producing Units".equals(button.getLabel())
                                || "Reset Build".equals(button.getLabel()))
                        .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", use the blockaded PRODUCTION in "
                        + source.getRepresentationForButtons(game, player) + " to produce ships in "
                        + destination.getRepresentationForButtons(game, player) + ". You have "
                        + Helper.getProductionValue(player, game, source, false) + " PRODUCTION value.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    private static boolean isEligibleVeiledNetworkingStarProduction(
            Game game, Player player, Tile source, Tile destination) {
        return hasVeiledNetworkingStar(player)
                && source != null
                && destination != null
                && destination.hasPlayerCC(player)
                && hasBlockadedProduction(player, game, source)
                && !FoWHelper.otherPlayersHaveShipsInSystem(player, destination, game)
                && FoWHelper.getAdjacentTiles(game, source.getPosition(), player, false, true)
                        .contains(destination.getPosition());
    }

    private static boolean hasBlockadedProduction(Player player, Game game, Tile tile) {
        return hasVeiledNetworkingStar(player)
                && tile != null
                && FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)
                && tile.containsPlayersUnitsWithModelCondition(
                        player, unit -> unit.getProductionValue() > 0 || unit.getBasicProduction() != null)
                && Helper.getProductionValue(player, game, tile, false) > 0;
    }

    private static String[] getVeiledNetworkingStarProductionContext(Game game, Player player) {
        return game.getStoredValue(VEILED_NETWORKING_STAR_CONTEXT + player.getFaction())
                .split("\\|", 3);
    }

    private static boolean isShipProductionButton(Player player, Button button, Tile destination, String placePrefix) {
        String suffix = "_" + destination.getPosition();
        String id = button.getCustomId();
        String prefix = placePrefix + "_";
        int placeIndex = id.indexOf(prefix);
        if (placeIndex < 0 || !id.endsWith(suffix)) return false;
        String unit = id.substring(placeIndex + prefix.length(), id.length() - suffix.length());
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit.replace("2", "")), player.getColorID());
        UnitModel model = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), null);
        return model != null && model.getIsShip();
    }

    public static Player getVeiledNetworkingSteelPlayer(Game game, String winner) {
        List<Player> losingPlayers = AgendaHelper.getLosers(winner, game);
        return game.getRealPlayers().stream()
                .filter(XinTechHandler::hasVeiledNetworkingSteel)
                .filter(candidate -> candidate.getStrategicCC() > 0)
                .filter(losingPlayers::contains)
                .findFirst()
                .orElse(null);
    }

    public static Button getVeiledNetworkingSteelButton(Player player, String winner) {
        return Buttons.red(
                player.factionButtonChecker() + USE_VEILED_NETWORKING_STEEL + winner,
                "Spend 1 Strategy Token: Resolve No Effect");
    }

    public static Button getDeclineVeiledNetworkingSteelButton(
            Player player, String winner, boolean includeFilibuster) {
        return Buttons.gray(
                player.factionButtonChecker() + DECLINE_VEILED_NETWORKING_STEEL + winner + "|" + includeFilibuster,
                "Resolve Agenda Normally");
    }

    @ButtonHandler(USE_VEILED_NETWORKING_STEEL)
    public static void useVeiledNetworkingSteel(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!hasVeiledNetworkingSteel(player) || player.getStrategicCC() < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.setStrategicCC(player.getStrategicCC() - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " spent 1 token from their strategy pool to resolve _Veiled Networking_ (Steel)."
                        + " The agenda is resolved with no effect.");
        AgendaHelper.resolveWithNoEffect(event, game);
    }

    @ButtonHandler(DECLINE_VEILED_NETWORKING_STEEL)
    public static void declineVeiledNetworkingSteel(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!hasVeiledNetworkingSteel(player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String[] parts =
                buttonID.substring(DECLINE_VEILED_NETWORKING_STEEL.length()).split("\\|", 2);
        String winner = parts[0];
        boolean includeFilibuster = parts.length == 2 && Boolean.parseBoolean(parts[1]);
        List<Button> resolutions = AgendaHelper.getAgendaResolutionButtons(game, winner, includeFilibuster, false);
        MessageHelper.editMessageWithButtons(
                event,
                event.getMessage().getContentRaw()
                        + "\n"
                        + player.getRepresentationNoPing()
                        + " declined to use _Veiled Networking_ (Steel).",
                resolutions);
    }

    public static void sendAstromanticStarButtons(Player owner, Player endingPlayer, Game game) {
        if (!hasAstromanticCloakStar(owner) || endingPlayer == null || owner == endingPlayer) {
            return;
        }
        List<Button> buttons = List.of(
                Buttons.green(
                        owner.factionButtonChecker() + ASTROMANTIC_CLOAK_STAR + "ac_" + endingPlayer.getFaction(),
                        "Look at Action Cards (" + endingPlayer.getAcCount() + ")"),
                Buttons.green(
                        owner.factionButtonChecker() + ASTROMANTIC_CLOAK_STAR + "pn_" + endingPlayer.getFaction(),
                        "Look at Promissory Notes (" + endingPlayer.getPnCount() + ")"),
                Buttons.green(
                        owner.factionButtonChecker() + ASTROMANTIC_CLOAK_STAR + "so_" + endingPlayer.getFaction(),
                        "Look at"
                                + (IsPlayerElectedService.isPlayerElected(game, endingPlayer, "censure")
                                        ? " (Not So)"
                                        : "")
                                + " Secret Objectives (" + endingPlayer.getSo() + ")"),
                Buttons.red(owner.factionButtonChecker() + "deleteButtons", "Decline Astromantic Cloak"));
        MessageHelper.sendMessageToChannelWithButtons(
                owner.getCorrectChannel(),
                owner.getRepresentationUnfogged() + ", " + endingPlayer.getRepresentationNoPing()
                        + " ended coexistence with your units. You may resolve _Astromantic Cloak (Star)_.",
                buttons);
    }

    public static void offerAstromanticCloakStarButtons(
            Game game, Player endingPlayer, UnitHolder planet, String planetName) {
        for (Player coexistenceOwner : ButtonHelper.getPlayersWithUnitsOnAPlanet(game, planet)) {
            if (coexistenceOwner != endingPlayer
                    && hasAstromanticCloakStar(coexistenceOwner)
                    && game.getPlanetsPlayerIsCoexistingOn(coexistenceOwner).contains(planetName)) {
                sendAstromanticStarButtons(coexistenceOwner, endingPlayer, game);
            }
        }
    }

    @ButtonHandler(ASTROMANTIC_CLOAK_STAR)
    public static void resolveAstromanticCloakStar(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(ASTROMANTIC_CLOAK_STAR.length()).split("_", 2);
        Player endingPlayer = parts.length == 2 ? game.getPlayerFromColorOrFaction(parts[1]) : null;
        if (!hasAstromanticCloakStar(player) || endingPlayer == null || endingPlayer == player) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        switch (parts[0]) {
            case "ac" -> ActionCardHelper.showAll(endingPlayer, player, game);
            case "pn" -> PromissoryNoteHelper.showAll(endingPlayer, player, game);
            case "so" -> SecretObjectiveHelper.showAll(endingPlayer, player, game);
            default -> {
                ButtonHelper.deleteMessage(event);
                return;
            }
        }
        ButtonHelper.deleteMessage(event);
    }
}
