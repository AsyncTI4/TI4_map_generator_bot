package ti4.discord.interactions.buttons.handlers.relics.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponentUnion;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class LostLegaciesRelicHandler {
    // Economic Boon
    private static final String USE_EBOON = "useEconomicBoon";
    // Nature's Boon
    private static final String USE_NBOON = "useNaturesBoon_";
    private static final String CHOOSE_NBOON_PLANET = "chooseNaturesBoonPlanet_";
    // Diplomatic Boon
    private static final String CHOOSE_DBOON_PLANET = "chooseDiplomaticBoonPlanet_";
    private static final String PLACE_INF_DBOON = "placeInfantryWithDiplomaticBoon_";
    private static final String PLACE_FF_DBOON = "placeFighterWithDiplomaticBoon_";
    // Cosmic Boon
    private static final String USE_CBOON = "useCosmicBoon";

    // Cosmic Boon
    public static Button getCosmicBoonButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + USE_CBOON, "Exhaust Cosmic Boon");
    }

    @ButtonHandler(USE_CBOON)
    public static void resolveCosmicBoon(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasRelicReady("cosmicboon")) {
            return;
        }

        player.addExhaustedRelic("cosmicboon");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", you may use these buttons to explore 2 planets you control.",
                ButtonHelper.getButtonsToExploreAllPlanets(player, game));

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    // Diplomatic Boon
    public static List<Button> getDiplomaticBoonPlanets(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return List.of();
        }

        List<Button> planets = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            Tile tile = game.getTileFromPlanet(planetName);
            if (planet != null
                    && !planet.isHomePlanet()
                    && tile != null
                    && !tile.isMecatol()
                    && FoWHelper.playerHasShipsInSystem(player, tile)) {
                planets.add(Buttons.green(
                        player.factionButtonChecker() + CHOOSE_DBOON_PLANET + planetName,
                        planet.getRepresentation(game)));
            }
        }

        return planets;
    }

    @ButtonHandler(CHOOSE_DBOON_PLANET)
    public static void chooseDBoonInfantryOrFighter(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String planetName = buttonID.replace(CHOOSE_DBOON_PLANET, "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find planet.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> fighterOrInf = new ArrayList<>();
        fighterOrInf.add(Buttons.green(
                player.factionButtonChecker() + PLACE_INF_DBOON + planetName,
                "Place " + planet.getInfluence() + " Infantry",
                UnitEmojis.infantry));
        fighterOrInf.add(Buttons.green(
                player.factionButtonChecker() + PLACE_FF_DBOON + planetName,
                "Place " + planet.getInfluence() + " Fighters",
                UnitEmojis.fighter));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please select wether you would like to place fighters or infantry using _Diplomatic Boon_ on "
                        + planet.getRepresentation(game) + ".",
                fighterOrInf);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_INF_DBOON)
    public static void placeInfantryUsingDiplomaticBoon(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String planetName = buttonID.replace(PLACE_INF_DBOON, "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find planet");
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(
                event,
                game.getTileFromPlanet(planetName),
                game,
                player.getColor(),
                planet.getInfluence() + " infantry " + planetName);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed " + planet.getInfluence() + " infantry on "
                        + planet.getRepresentation(game) + " using _Diplomatic Boon_");

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_FF_DBOON)
    public static void placeFightersUsingDiplomaticBoon(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String planetName = buttonID.replace(PLACE_FF_DBOON, "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null || tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find planet or tile.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), planet.getInfluence() + " fighter");

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed " + planet.getInfluence() + " fighter "
                        + (planet.getInfluence() > 1 ? "s" : "") + " in " + tile.getRepresentation()
                        + " using _Diplomatic Boon_");

        ButtonHelper.deleteMessage(event);
    }

    // Economic Boon
    public static Button getEconomicBoonCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_EBOON, "Ready Planet with Economic Boon", CardEmojis.RelicCard);
    }

    public static Button getNaturesBoonSpendButton(Player player, String whatIsItFor) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_NBOON + whatIsItFor, "Use Nature's Boon", CardEmojis.RelicCard);
    }

    @ButtonHandler(USE_EBOON)
    public static void resolveEconomicBoon(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasRelicReady("economicboon")) {
            return;
        }

        List<Button> buttons = Helper.getPlanetRefreshButtons(player, game);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        player.addExhaustedRelic("economicboon");
        buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Readying"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please choose the exhausted planet you wish to ready with _Economic Boon_.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(USE_NBOON)
    public static void offerNaturesBoonPlanets(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasRelicReady("naturesboon")) {
            return;
        }

        String whatIsItFor = buttonID.substring(USE_NBOON.length());
        String paymentMessageId = event.getMessageId();
        List<Button> buttons = getNaturesBoonPlanetButtons(player, game, whatIsItFor, paymentMessageId);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", spend a planet before using _Nature's Boon_.");
            return;
        }

        String message =
                player.getRepresentation() + ", choose a planet already spent for this payment with _Nature's Boon_.";
        String buttonPrefix =
                player.factionButtonChecker() + CHOOSE_NBOON_PLANET + whatIsItFor + "|" + paymentMessageId + "|";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
    }

    @ButtonHandler(CHOOSE_NBOON_PLANET)
    public static void resolveNaturesBoon(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasRelicReady("naturesboon")) {
            return;
        }

        String payload = buttonID.substring(CHOOSE_NBOON_PLANET.length());
        String[] data = payload.split("\\|", 3);
        if (data.length != 3) {
            return;
        }
        String whatIsItFor = data[0];
        String paymentMessageId = data[1];
        String planetName = data[2];
        List<Button> buttons = getNaturesBoonPlanetButtons(player, game, whatIsItFor, paymentMessageId);
        String message =
                player.getRepresentation() + ", choose a planet already spent for this payment with _Nature's Boon_.";
        String buttonPrefix =
                player.factionButtonChecker() + CHOOSE_NBOON_PLANET + whatIsItFor + "|" + paymentMessageId + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }
        if (!player.getSpentThingsThisWindow().contains(planetName)
                || !player.getExhaustedPlanets().contains(planetName)
                || !game.getPlanetsInfo().containsKey(planetName)) {
            return;
        }

        player.addExhaustedRelic("naturesboon");
        player.addSpentThing("naturesboon_" + planetName);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " exhausted _Nature's Boon_ for "
                        + Helper.getPlanetRepresentation(planetName, game) + ".");
        event.getChannel()
                .retrieveMessageById(paymentMessageId)
                .queue(
                        paymentMessage -> {
                            List<Button> paymentButtons = new ArrayList<>();
                            for (ActionRow row :
                                    paymentMessage.getComponentTree().findAll(ActionRow.class)) {
                                for (ActionRowChildComponentUnion component : row.getComponents()) {
                                    if (component instanceof Button button
                                            && (button.getCustomId() == null
                                                    || !button.getCustomId().contains(USE_NBOON))) {
                                        paymentButtons.add(button);
                                    }
                                }
                            }
                            paymentMessage
                                    .editMessage(Helper.buildSpentThingsMessage(player, game, whatIsItFor))
                                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(paymentButtons))
                                    .queue(Consumers.nop(), BotLogger::catchRestError);
                        },
                        BotLogger::catchRestError);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getNaturesBoonPlanetButtons(
            Player player, Game game, String whatIsItFor, String paymentMessageId) {
        List<Button> buttons = new ArrayList<>();
        for (String spentThing : player.getSpentThingsThisWindow()) {
            if (!player.getExhaustedPlanets().contains(spentThing)
                    || !game.getPlanetsInfo().containsKey(spentThing)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker()
                            + CHOOSE_NBOON_PLANET
                            + whatIsItFor
                            + "|"
                            + paymentMessageId
                            + "|"
                            + spentThing,
                    Helper.getPlanetRepresentation(spentThing, game)));
        }
        return buttons;
    }
}
