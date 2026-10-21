package ti4.discord.interactions.buttons.handlers.explore.thirtyk;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.ExploreHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.thirtyk.ThirtyKCards;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.service.emoji.UnitEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class ThirtyKHazardousExploreHandler {

    private static final String MECH_OR_INFANTRY = "thirtyKMechOrInf_";
    private static final String IMITATE = "thirtyKImitate_";
    private static final String IMITATION_CARD_KEY = "thirtyKImitationCard";
    private static final String EMERGENT_DEFENSES = "emergent";
    private static final String PILOT_RECOVERY = "pilot";
    private static final String LOST_AND_FOUND = "lostfound";
    private static final String MECH = "mech";
    private static final String INFANTRY = "inf";

    public static void resolveEmergentDefenses(
            GenericInteractionCreateEvent event, Game game, Player player, String planetID) {
        offerMechOrInfantry(event, game, player, planetID, EMERGENT_DEFENSES, "Place 1 PDS");
    }

    public static void resolvePilotRecovery(
            GenericInteractionCreateEvent event, Game game, Player player, String planetID) {
        offerMechOrInfantry(event, game, player, planetID, PILOT_RECOVERY, "Gain 1 Fleet Token");
    }

    public static void resolveLostAndFound(
            GenericInteractionCreateEvent event, Game game, Player player, String planetID) {
        offerMechOrInfantry(event, game, player, planetID, LOST_AND_FOUND, "Place 1 Destroyer");
    }

    private static void offerMechOrInfantry(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            String planetID,
            String effect,
            String effectLabel) {
        if (planetID == null || game.getTileFromPlanet(planetID) == null) return;
        String planetName = Helper.getPlanetRepresentation(planetID, game);
        List<Button> buttons = new ArrayList<>();
        if (ExploreHelper.checkForMech(planetID, game, player)) {
            buttons.add(Buttons.green(
                            player.factionButtonChecker() + MECH_OR_INFANTRY + effect + "_" + MECH + "_" + planetID,
                            effectLabel + " With A Mech On " + planetName)
                    .withEmoji(UnitEmojis.mech.asEmoji()));
        }
        if (ExploreHelper.checkForInf(planetID, game, player)) {
            buttons.add(Buttons.blue(
                            player.factionButtonChecker() + MECH_OR_INFANTRY + effect + "_" + INFANTRY + "_" + planetID,
                            effectLabel + " By Removing 1 Infantry")
                    .withEmoji(UnitEmojis.infantry.asEmoji()));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "You have no mech or infantry on " + planetName + ", so this exploration does nothing.");
            return;
        }
        buttons.add(ThirtyKExploreSupport.decline(player));
        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                player.getRepresentation() + ", you have "
                        + ExploreHelper.getUnitListEmojisOnPlanetForHazardousExplorePurposes(game, player, planetID)
                        + ". Use a mech, or remove 1 infantry, to resolve this exploration.",
                buttons);
    }

    @ButtonHandler(MECH_OR_INFANTRY)
    public static void mechOrInfantry(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(MECH_OR_INFANTRY.length()).split("_", 3);
        String effect = parts[0];
        boolean removeInfantry = INFANTRY.equals(parts[1]);
        String planetID = parts[2];
        Tile tile = game.getTileFromPlanet(planetID);
        if (tile == null) return;
        if (removeInfantry) {
            if (!ExploreHelper.checkForInf(planetID, game, player)) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You have no infantry left there.");
                return;
            }
            RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 inf " + planetID);
        }
        String outcome =
                switch (effect) {
                    case EMERGENT_DEFENSES -> {
                        AddUnitService.addUnits(event, tile, game, player.getColor(), "pds " + planetID);
                        yield "brought a buried defense battery online: 1 PDS placed on "
                                + Helper.getPlanetRepresentation(planetID, game) + " (_Emergent Defenses_)";
                    }
                    case PILOT_RECOVERY -> {
                        player.setFleetCC(player.getFleetCC() + 1);
                        yield "recovered the downed pilots: 1 command token placed in their fleet pool (_Pilot"
                                + " Recovery Program_)";
                    }
                    case LOST_AND_FOUND -> placeLostAndFoundDestroyer(event, game, player, tile);
                    default -> "resolved the exploration";
                };
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + (removeInfantry ? " removed 1 infantry and " : " ") + outcome + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static String placeLostAndFoundDestroyer(
            ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        if (ThirtyKExploreSupport.otherPlayersHaveShips(game, player, tile)) {
            return "found a working derelict, but another player's ships hold the space area, so no destroyer is"
                    + " placed (_Lost and Found_)";
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "dd");
        return "restored a derelict to service: 1 destroyer placed in the space area (_Lost and Found_)";
    }

    public static void resolveImitationFactories(
            GenericInteractionCreateEvent event, Game game, Player player, String cardID, String planetID) {
        List<Button> buttons = imitationButtons(game, player, cardID, planetID);
        if (buttons.isEmpty() || planetID == null) {
            game.purgeExplore(cardID);
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "The factories find nothing in the hazardous discard pile to copy and shut down for good"
                            + " (_Imitation Factories_ purged).");
            return;
        }
        game.setStoredValue(IMITATION_CARD_KEY + player.getFaction(), cardID);
        NewStuffHelper.sendPaginated(
                event.getMessageChannel(),
                imitationMessage(game, player, planetID),
                buttons,
                imitationDecline(player, planetID),
                imitationNav(player, planetID));
    }

    private static List<Button> imitationButtons(Game game, Player player, String cardID, String planetID) {
        List<Button> buttons = new ArrayList<>();
        for (String discarded : new LinkedHashSet<>(game.getExploreDiscard("hazardous"))) {
            if (ThirtyKCards.baseId(discarded).equals(ThirtyKCards.baseId(cardID))) continue;
            ExploreModel model = Mapper.getExplore(discarded);
            if (model == null) continue;
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + IMITATE + discarded + "_" + planetID, model.getName()));
        }
        return buttons;
    }

    private static String imitationMessage(Game game, Player player, String planetID) {
        return player.getRepresentation()
                + ", choose a hazardous exploration card from the discard pile to resolve on "
                + Helper.getPlanetRepresentation(planetID, game) + ". _Imitation Factories_ is then purged.";
    }

    private static List<Button> imitationDecline(Player player, String planetID) {
        return List.of(Buttons.red(player.factionButtonChecker() + IMITATE + "none_" + planetID, "Decline"));
    }

    private static String imitationNav(Player player, String planetID) {
        return player.factionButtonChecker() + IMITATE + planetID + "_";
    }

    @ButtonHandler(IMITATE)
    public static void imitate(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(IMITATE.length()).split("_", 2);
        String cardKey = IMITATION_CARD_KEY + player.getFaction();
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                imitationMessage(game, player, parts[0]),
                () -> imitationButtons(game, player, game.getStoredValue(cardKey), parts[0]),
                imitationDecline(player, parts[0]),
                imitationNav(player, parts[0]))) {
            return;
        }
        if (parts.length < 2) return;
        String chosen = parts[0];
        String planetID = parts[1];
        String key = IMITATION_CARD_KEY + player.getFaction();
        game.purgeExplore(game.getStoredValue(key));
        game.removeStoredValue(key);
        ButtonHelper.deleteMessage(event);
        if ("none".equals(chosen) || !game.getExploreDiscard("hazardous").contains(chosen)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "The factories shut down without copying anything (_Imitation Factories_ purged).");
            return;
        }
        Tile tile = game.getTileFromPlanet(planetID);
        String message = "The factories churn out a perfect copy of _"
                + Mapper.getExplore(chosen).getName()
                + "_ for " + player.getRepresentationNoPing() + " on " + Helper.getPlanetRepresentation(planetID, game)
                + " (_Imitation Factories_):";
        ExploreService.resolveExplore(event, chosen, tile, planetID, message, player, game);
    }

    public static void resolveCorrosiveDiscovery(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, String planetID) {
        List<Die> dice = DiceHelper.rollDice(5, 3);
        List<Integer> results = dice.stream().map(Die::getResult).toList();
        int tradeGoods = DiceHelper.countSuccesses(dice);
        int hits = (int) results.stream().filter(result -> result <= 2).count();
        StringBuilder message = new StringBuilder(player.getRepresentationNoPing())
                .append("'s prospectors brave the acid for _Corrosive Discovery_: ")
                .append(results)
                .append(". ")
                .append(tradeGoods)
                .append(" trade good")
                .append(tradeGoods == 1 ? "" : "s")
                .append(", ")
                .append(hits)
                .append(" hit")
                .append(hits == 1 ? "" : "s")
                .append(" against your ground forces on ")
                .append(Helper.getPlanetRepresentation(planetID, game))
                .append('.');
        if (hits > 0 && tile != null) {
            MessageHelper.sendMessageToEventChannelWithButtons(
                    event,
                    message.toString(),
                    List.of(Buttons.red(
                            player.factionButtonChecker() + "getDamageButtons_" + tile.getPosition() + "_groundcombat",
                            "Assign Hit" + (hits == 1 ? "" : "s"))));
        } else {
            MessageHelper.sendMessageToEventChannel(event, message.toString());
        }
        ButtonHelperStats.gainTGs(event, game, player, tradeGoods, false);
    }
}
