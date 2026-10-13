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
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.ExploreHelper;
import ti4.helpers.Helper;
import ti4.helpers.thirtyk.ThirtyKCards;
import ti4.helpers.thirtyk.ThirtyKExploreMath;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
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
                        yield "placed 1 PDS on " + Helper.getPlanetRepresentation(planetID, game);
                    }
                    case PILOT_RECOVERY -> {
                        player.setFleetCC(player.getFleetCC() + 1);
                        yield "placed 1 command token in their fleet pool";
                    }
                    case LOST_AND_FOUND -> placeLostAndFoundDestroyer(event, game, player, tile);
                    default -> "resolved the exploration";
                };
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + (removeInfantry ? " removed 1 infantry and " : " ") + outcome + ".");
        ThirtyKExploreSupport.deleteMessage(event);
    }

    private static String placeLostAndFoundDestroyer(
            ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        if (ThirtyKExploreSupport.otherPlayersHaveShips(game, player, tile)) {
            return "could not place a destroyer, because another player's ships are in that space area";
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "dd");
        return "placed 1 destroyer in the space area";
    }

    public static void resolveImitationFactories(
            GenericInteractionCreateEvent event, Game game, Player player, String cardID, String planetID) {
        List<Button> buttons = new ArrayList<>();
        for (String discarded : new LinkedHashSet<>(game.getExploreDiscard("hazardous"))) {
            if (ThirtyKCards.baseId(discarded).equals(ThirtyKCards.baseId(cardID))) continue;
            ExploreModel model = Mapper.getExplore(discarded);
            if (model == null) continue;
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + IMITATE + discarded + "_" + planetID, model.getName()));
        }
        if (buttons.isEmpty() || planetID == null) {
            game.purgeExplore(cardID);
            MessageHelper.sendMessageToEventChannel(
                    event, "The hazardous discard pile has nothing to imitate; _Imitation Factories_ was purged.");
            return;
        }
        game.setStoredValue(IMITATION_CARD_KEY + player.getFaction(), cardID);
        List<Button> choices = ThirtyKExploreSupport.capped(buttons);
        choices.add(Buttons.red(player.factionButtonChecker() + IMITATE + "none_" + planetID, "Decline"));
        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                player.getRepresentation()
                        + ", choose a hazardous exploration card from the discard pile to resolve on "
                        + Helper.getPlanetRepresentation(planetID, game) + ". _Imitation Factories_ is then purged.",
                choices);
    }

    @ButtonHandler(IMITATE)
    public static void imitate(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(IMITATE.length()).split("_", 2);
        String chosen = parts[0];
        String planetID = parts[1];
        String key = IMITATION_CARD_KEY + player.getFaction();
        game.purgeExplore(game.getStoredValue(key));
        game.removeStoredValue(key);
        ThirtyKExploreSupport.deleteMessage(event);
        if ("none".equals(chosen) || !game.getExploreDiscard("hazardous").contains(chosen)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "_Imitation Factories_ was purged without imitating a card.");
            return;
        }
        Tile tile = game.getTileFromPlanet(planetID);
        String message = player.getRepresentationNoPing() + " used _Imitation Factories_ to resolve _"
                + Mapper.getExplore(chosen).getName() + "_ on " + Helper.getPlanetRepresentation(planetID, game)
                + ":";
        ExploreService.resolveExplore(event, chosen, tile, planetID, message, player, game);
    }

    public static void resolveCorrosiveDiscovery(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, String planetID) {
        List<Die> dice = DiceHelper.rollDice(5, 3);
        List<Integer> results = dice.stream().map(Die::getResult).toList();
        int tradeGoods = ThirtyKExploreMath.countAtLeast(results, 5);
        int hits = ThirtyKExploreMath.countAtMost(results, 2);
        StringBuilder message = new StringBuilder(player.getRepresentationNoPing())
                .append(" rolled for _Corrosive Discovery_: ")
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
