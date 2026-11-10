package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.bloom;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thurviali.ThurvialiAbilityHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.transaction.SendPromissoryService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class BloomPromissoryHandler {
    private static final String STARTER_CULTURE = "bepnbloom";
    private static final String SELECT_BLOOM_PLANET = "selectStarterCultureBloomPlanet_";
    private static final String SELECT_HOLDER_PLANET = "selectStarterCultureHolderPlanet_";

    public static void offerStarterCulture(Game game, Player holder) {
        Player bloomPlayer = game.getPNOwner(STARTER_CULTURE);
        List<Button> buttons = getBloomPlanetButtons(game, holder, bloomPlayer);
        if (buttons.isEmpty()
                || getHolderPlanetButtons(game, holder, bloomPlayer, "").isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    holder.getCorrectChannel(),
                    holder.getRepresentationNoPing()
                            + " cannot resolve _Starter Culture_ because there is no eligible planet for both players.");
            return;
        }

        NewStuffHelper.sendPaginated(
                holder.getCorrectChannel(),
                getBloomPlanetPrompt(holder),
                buttons,
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                getBloomPlanetPrefix(holder));
    }

    public static void returnStarterCultureAtStatusStart(GenericInteractionCreateEvent event, Game game) {
        Player owner = game.getPNOwner(STARTER_CULTURE);
        if (owner == null) return;
        for (Player holder : game.getRealPlayers()) {
            if (holder != owner && holder.getPromissoryNotesInPlayArea().contains(STARTER_CULTURE)) {
                SendPromissoryService.returnPromissoryFromPlayAreaToOwner(event, game, holder, owner, STARTER_CULTURE);
            }
        }
    }

    @ButtonHandler(SELECT_BLOOM_PLANET)
    public static void selectBloomPlanet(ButtonInteractionEvent event, Game game, Player holder, String buttonID) {
        Player bloomPlayer = game.getPNOwner(STARTER_CULTURE);
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                getBloomPlanetPrompt(holder),
                () -> getBloomPlanetButtons(game, holder, bloomPlayer),
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                getBloomPlanetPrefix(holder))) {
            return;
        }

        String bloomPlanet = buttonID.substring(SELECT_BLOOM_PLANET.length());
        if (!isEligiblePlanet(game, bloomPlayer, holder, bloomPlanet)
                || getHolderPlanetButtons(game, holder, bloomPlayer, bloomPlanet)
                        .isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String holderName = game.isFowMode() ? "A player" : holder.getRepresentationNoPing();
        NewStuffHelper.sendPaginated(
                bloomPlayer.getCorrectChannel(),
                getHolderPlanetPrompt(game, bloomPlayer, holder, bloomPlanet, holderName),
                getHolderPlanetButtons(game, holder, bloomPlayer, bloomPlanet),
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                getHolderPlanetPrefix(bloomPlayer, holder, bloomPlanet));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_HOLDER_PLANET)
    public static void selectHolderPlanet(
            ButtonInteractionEvent event, Game game, Player bloomPlayer, String buttonID) {
        String[] payload = buttonID.substring(SELECT_HOLDER_PLANET.length()).split("\\|", 3);
        Player holder = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        String bloomPlanet = payload.length == 3 ? payload[1] : null;
        String holderPlanet = payload.length == 3 ? payload[2] : null;
        if (holder == null || bloomPlayer == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String holderName = game.isFowMode() ? "the other player" : holder.getRepresentationNoPing();
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                getHolderPlanetPrompt(game, bloomPlayer, holder, bloomPlanet, holderName),
                () -> getHolderPlanetButtons(game, holder, bloomPlayer, bloomPlanet),
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                getHolderPlanetPrefix(bloomPlayer, holder, bloomPlanet))) {
            return;
        }

        if (!isEligiblePlanet(game, bloomPlayer, holder, bloomPlanet)
                || !isEligiblePlanet(game, holder, bloomPlayer, holderPlanet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        addInfantryIntoCoexistence(event, game, holder, bloomPlanet);
        addInfantryIntoCoexistence(event, game, bloomPlayer, holderPlanet);
        ButtonHelperAbilities.oceanBoundCheck(game);
        ThurvialiAbilityHandler.checkRadiantGrafting(game);
        BloomAbilityHandler.syncCoexistingAbilities(game, holder);
        BloomAbilityHandler.syncCoexistingAbilities(game, bloomPlayer);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                bloomPlayer.getRepresentationNoPing() + " and "
                        + (game.isFowMode() ? "another player" : holder.getRepresentationNoPing())
                        + " placed 1 infantry each into coexistence with _Starter Culture_.");
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    holder.getCorrectChannel(),
                    "You placed 1 infantry into coexistence on "
                            + Helper.getPlanetRepresentation(bloomPlanet, game)
                            + " with _Starter Culture_.");
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getBloomPlanetButtons(Game game, Player holder, Player bloomPlayer) {
        if (holder == null || bloomPlayer == null) {
            return List.of();
        }
        return bloomPlayer.getPlanets().stream()
                .filter(planetName -> isEligiblePlanet(game, bloomPlayer, holder, planetName))
                .sorted()
                .map(planetName -> Buttons.green(
                        holder.factionButtonChecker() + SELECT_BLOOM_PLANET + planetName,
                        Helper.getPlanetRepresentation(planetName, game)))
                .toList();
    }

    private static List<Button> getHolderPlanetButtons(
            Game game, Player holder, Player bloomPlayer, String bloomPlanet) {
        if (holder == null || bloomPlayer == null) {
            return List.of();
        }
        return holder.getPlanets().stream()
                .filter(planetName -> isEligiblePlanet(game, holder, bloomPlayer, planetName))
                .sorted()
                .map(planetName -> Buttons.green(
                        bloomPlayer.factionButtonChecker() + SELECT_HOLDER_PLANET + holder.getFaction() + "|"
                                + bloomPlanet + "|" + planetName,
                        Helper.getPlanetRepresentation(planetName, game)))
                .toList();
    }

    private static boolean isEligiblePlanet(Game game, Player controller, Player infantryOwner, String planetName) {
        if (controller == null
                || infantryOwner == null
                || controller == infantryOwner
                || !controller.getPlanets().contains(planetName)) {
            return false;
        }
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        return planet != null && tile != null && !tile.isHomeSystem(game) && planet.hasGroundForces(controller);
    }

    private static void addInfantryIntoCoexistence(
            ButtonInteractionEvent event, Game game, Player infantryOwner, String planetName) {
        Tile tile = game.getTileFromPlanet(planetName);
        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, tile, game, infantryOwner.getColor(), "1 infantry " + planetName);
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", coexistenceFlag);
            }
        }
    }

    private static String getBloomPlanetPrompt(Player holder) {
        return holder.getRepresentationNoPing()
                + ", choose a J'nahfa-controlled planet containing their ground forces for _Starter Culture_.";
    }

    private static String getHolderPlanetPrompt(
            Game game, Player bloomPlayer, Player holder, String bloomPlanet, String holderName) {
        return bloomPlayer.getRepresentationNoPing() + ", choose a planet controlled by " + holderName
                + " that contains their ground forces. This will complete _Starter Culture_ on "
                + Helper.getPlanetRepresentation(bloomPlanet, game) + ".";
    }

    private static String getBloomPlanetPrefix(Player holder) {
        return holder.factionButtonChecker() + SELECT_BLOOM_PLANET;
    }

    private static String getHolderPlanetPrefix(Player bloomPlayer, Player holder, String bloomPlanet) {
        return bloomPlayer.factionButtonChecker() + SELECT_HOLDER_PLANET + holder.getFaction() + "|" + bloomPlanet
                + "|";
    }
}
