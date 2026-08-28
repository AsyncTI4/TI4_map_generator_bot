package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta.TaUnitHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ActionCardHelper.ACStatus;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

@UtilityClass
public class TheodisiOutpostActionCardHandler {
    private static final String INITIATIVE_OUTPOST = "attachment_initiativeoutpost.png";
    private static final String EXPLORATION_OUTPOST = "attachment_explorationoutpost.png";
    private static final String ASSEMBLY_OUTPOST = "attachment_assemblyoutpost.png";
    private static final String MARKET_OUTPOST = "attachment_marketoutpost.png";

    @ButtonHandler("resolveTheodisiOutpost_")
    public static void resolveOutpost(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String outpost = buttonID.substring("resolveTheodisiOutpost_".length());
        List<Button> buttons = new ArrayList<>();

        for (String planetName : player.getPlanets()) {
            Planet planet = game.getPlanetsInfo().get(planetName);
            if (planet == null || planet.isHomePlanet(game)) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "placeTheodisiOutpost_" + outpost + "|" + planetName,
                    "Attach to " + Helper.getPlanetRepresentation(planetName, game)));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", you do not control an eligible non-home planet.");
            return;
        }

        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose a non-home planet for this outpost.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("placeTheodisiOutpost_")
    public static void placeOutpost(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring("placeTheodisiOutpost_".length());
        int separator = payload.indexOf('|');
        if (separator < 1) {
            return;
        }

        String outpost = payload.substring(0, separator);
        String planetName = payload.substring(separator + 1);
        Planet planet = game.getPlanetsInfo().get(planetName);
        String attachment = getOutpostAttachment(outpost);
        if (planet == null
                || attachment == null
                || game.getDiscardACStatus().get(outpost) == ACStatus.purged
                || !player.getPlanets().contains(planetName)) {
            return;
        }
        planet.addToken(attachment);
        TaUnitHandler.offerTaMechDeploy(event, player, game, game.getTileFromPlanet(planetName), planetName);
        game.getDiscardACStatus().put(outpost, ACStatus.purged);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation()
                        + " attached _"
                        + outpost.replace('_', ' ')
                        + "_ to "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static void offerOutpostEffects(Game game, Player player, String exhaustedPlanet) {
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            return;
        }

        Planet planet = game.getPlanetsInfo().get(exhaustedPlanet);
        if (planet == null) {
            return;
        }
        if (planet.getTokenList().contains(INITIATIVE_OUTPOST)) {
            offerOutpostEffect(game, player, exhaustedPlanet, "initiative_outpost");
        }
        if (planet.getTokenList().contains(EXPLORATION_OUTPOST)) {
            offerOutpostEffect(game, player, exhaustedPlanet, "exploration_outpost");
        }
        if (planet.getTokenList().contains(ASSEMBLY_OUTPOST)) {
            offerOutpostEffect(game, player, exhaustedPlanet, "assembly_outpost");
        }
        if (planet.getTokenList().contains(MARKET_OUTPOST)) {
            offerOutpostEffect(game, player, exhaustedPlanet, "market_outpost");
        }
    }

    private static String getOutpostAttachment(String outpost) {
        return switch (outpost) {
            case "initiative_outpost" -> INITIATIVE_OUTPOST;
            case "exploration_outpost" -> EXPLORATION_OUTPOST;
            case "assembly_outpost" -> ASSEMBLY_OUTPOST;
            case "market_outpost" -> MARKET_OUTPOST;
            default -> null;
        };
    }

    private static void offerOutpostEffect(Game game, Player player, String planet, String outpost) {
        List<Button> buttons = new ArrayList<>();
        String message;

        switch (outpost) {
            case "initiative_outpost" -> {
                message = player.getRepresentation()
                        + ", _Initiative Outpost_ was exhausted. You may draw 1 action card.";
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "resolveInitiativeOutpost", "Draw 1 Action Card"));
            }
            case "exploration_outpost" -> {
                message = player.getRepresentation()
                        + ", _Exploration Outpost_ was exhausted. Choose another planet you control to explore.";

                for (String otherPlanet : player.getPlanets()) {
                    if (!planet.equals(otherPlanet)) {
                        Planet planetModel = game.getPlanetsInfo().get(otherPlanet);
                        if (planetModel != null && !planetModel.getPlanetTypes().isEmpty()) {
                            buttons.add(Buttons.green(
                                    player.factionButtonChecker() + "resolveExplorationOutpost_" + otherPlanet,
                                    "Explore " + Helper.getPlanetRepresentation(otherPlanet, game)));
                        }
                    }
                }
            }
            case "assembly_outpost" -> {
                message = player.getRepresentation()
                        + ", _Assembly Outpost_ was exhausted. You may produce 1 unit in this system.";
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "resolveAssemblyOutpost_" + planet, "Produce 1 Unit"));
            }
            case "market_outpost" -> {
                message = player.getRepresentation() + ", _Market Outpost_ was exhausted. Choose another player.";

                for (Player otherPlayer : game.getRealPlayers()) {
                    if (otherPlayer != player) {
                        buttons.add(FoWHelper.fogSafeTargetButton(
                                player.factionButtonChecker() + "resolveMarketOutpost_" + otherPlayer.getFaction(),
                                "green",
                                otherPlayer));
                    }
                }
            }
            default -> {
                return;
            }
        }

        if (buttons.isEmpty()) {
            return;
        }

        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("resolveInitiativeOutpost")
    public static void resolveInitiativeOutpost(ButtonInteractionEvent event, Game game, Player player) {
        ActionCardHelper.drawActionCards(player, 1);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveExplorationOutpost_")
    public static void resolveExplorationOutpost(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring("resolveExplorationOutpost_".length());
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (planet == null || !player.getPlanets().contains(planetName)) {
            return;
        }

        List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);
        if (buttons == null || buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", that planet cannot currently be explored.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose how to explore "
                        + Helper.getPlanetRepresentation(planetName, game) + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveAssemblyOutpost_")
    public static void resolveAssemblyOutpost(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring("resolveAssemblyOutpost_".length());
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null || !player.getPlanets().contains(planetName)) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", produce up to 1 unit in "
                        + tile.getRepresentationForButtons(game, player)
                        + " due to _Assembly Outpost_.",
                Helper.getPlaceUnitButtons(event, player, game, tile, "assemblyOutpost", "place"));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveMarketOutpost_")
    public static void resolveMarketOutpost(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String faction = buttonID.substring("resolveMarketOutpost_".length());
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (target == null || target == player) {
            return;
        }

        String playerGain = player.gainTG(1, true);
        String targetGain = target.gainTG(1, true);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        ButtonHelperAgents.resolveArtunoCheck(target, 1);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " and " + target.getRepresentation()
                        + " resolved _Market Outpost_: "
                        + playerGain + "; " + targetGain
                        + ". They may now perform a transaction.");
        ButtonHelper.deleteMessage(event);
    }
}
