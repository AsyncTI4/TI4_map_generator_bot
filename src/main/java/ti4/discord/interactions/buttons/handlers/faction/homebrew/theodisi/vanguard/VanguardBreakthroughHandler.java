package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.vanguard;

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
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class VanguardBreakthroughHandler {
    private static final String TRAINING_DUMMIES = "vanguardbt";
    private static final String TRAINING_DUMMIES_TG = "trainingDummiesTg_";
    private static final String USE_TRAINING_DUMMIES_TG = "useTrainingDummiesTg";
    private static final String PLACE_TRAINING_DUMMIES_INFANTRY = "placeTrainingDummiesInfantry_";
    private static final String RESOLVE_TRAINING_DUMMIES_INFANTRY = "resolveTrainingDummiesInfantry_";

    public static void offerTrainingDummiesGroundCombatReward(Player player) {
        if (!player.hasUnlockedBreakthrough(TRAINING_DUMMIES)) return;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", if you win this ground combat, you may gain 2 commodities due to _Training Programs_.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + USE_TRAINING_DUMMIES_TG,
                                "Gain 2 Commodities (On Win)",
                                FactionEmojis.vanguard),
                        Buttons.DECLINE_DELETE_BUTTONS));
    }

    public static void offerTrainingDummiesInfantry(Game game, Player player, int strategyCard) {
        Player strategyCardHolder = game.getPlayerFromSC(strategyCard);
        if (!player.hasUnlockedBreakthrough(TRAINING_DUMMIES)
                || strategyCardHolder == null
                || strategyCardHolder == player) {
            return;
        }

        List<Button> buttons = getTrainingDummiesInfantryButtons(game, player, strategyCardHolder);
        if (buttons.isEmpty()) {
            return;
        }
        List<Button> extraButtons = List.of(Buttons.DECLINE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                getTrainingDummiesInfantryMessage(player, strategyCardHolder),
                NewStuffHelper.buttonPagination(
                        buttons, extraButtons, trainingDummiesPrefix(player, strategyCardHolder), 25, 0, false));
    }

    @ButtonHandler(USE_TRAINING_DUMMIES_TG)
    public static void useTrainingDummiesGroundCombatReward(ButtonInteractionEvent event, Game game, Player player) {
        String key = TRAINING_DUMMIES_TG + player.getFaction();
        if (!player.hasUnlockedBreakthrough(TRAINING_DUMMIES)
                || !game.getStoredValue(key).isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(key, "used");
        int commodities = player.getCommodities();
        player.gainCommodities(2);
        String message = player.getRepresentationNoPing() + " gained " + (player.getCommodities() - commodities)
                + " commodities due to _Training Programs_.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getTrainingDummiesInfantryButtons(Game game, Player player, Player strategyCardHolder) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (planet.getUnitKeysForPlayer(player).stream()
                        .map(player::getUnitFromUnitKey)
                        .anyMatch(unit -> unit != null && unit.getIsGroundForce())) {
                    buttons.add(Buttons.green(
                            trainingDummiesPrefix(player, strategyCardHolder) + tile.getPosition() + "|"
                                    + planet.getName(),
                            "Place Infantry on " + Helper.getPlanetRepresentation(planet.getName(), game),
                            FactionEmojis.vanguard));
                }
            }
        }
        return buttons;
    }

    private static String getTrainingDummiesInfantryMessage(Player player, Player strategyCardHolder) {
        return player.getRepresentation()
                + ", you spent a strategy token to resolve " + strategyCardHolder.getRepresentationNoPing()
                + "'s strategy card. You may place 1 of their infantry on a planet containing your ground forces due to _Training Programs_.";
    }

    @ButtonHandler(PLACE_TRAINING_DUMMIES_INFANTRY)
    public static void placeTrainingDummiesInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(PLACE_TRAINING_DUMMIES_INFANTRY.length()).split("\\|", 3);
        Player strategyCardHolder = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        if (strategyCardHolder != null
                && NewStuffHelper.checkAndHandlePaginationChange(
                        event,
                        event.getMessageChannel(),
                        getTrainingDummiesInfantryButtons(game, player, strategyCardHolder),
                        List.of(Buttons.DECLINE_DELETE_BUTTONS),
                        getTrainingDummiesInfantryMessage(player, strategyCardHolder),
                        trainingDummiesPrefix(player, strategyCardHolder),
                        buttonID)) {
            return;
        }
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[1]) : null;
        Planet planet = tile == null || payload.length != 3
                ? null
                : tile.getUnitHolders().get(payload[2]) instanceof Planet p ? p : null;
        if (!player.hasUnlockedBreakthrough(TRAINING_DUMMIES)
                || planet == null
                || strategyCardHolder == null
                || strategyCardHolder == player
                || planet.getUnitKeysForPlayer(player).stream()
                        .map(player::getUnitFromUnitKey)
                        .noneMatch(unit -> unit != null && unit.getIsGroundForce())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = List.of(
                Buttons.red(
                        player.factionButtonChecker() + RESOLVE_TRAINING_DUMMIES_INFANTRY
                                + strategyCardHolder.getFaction() + "|" + tile.getPosition() + "|" + planet.getName()
                                + "|combat",
                        "Resolve Ground Combat",
                        FactionEmojis.vanguard),
                Buttons.green(
                        player.factionButtonChecker() + RESOLVE_TRAINING_DUMMIES_INFANTRY
                                + strategyCardHolder.getFaction() + "|" + tile.getPosition() + "|" + planet.getName()
                                + "|coexist",
                        "Place into Coexistence",
                        FactionEmojis.vanguard));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", choose how to resolve the infantry placed by _Training Programs_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RESOLVE_TRAINING_DUMMIES_INFANTRY)
    public static void resolveTrainingDummiesInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(RESOLVE_TRAINING_DUMMIES_INFANTRY.length()).split("\\|", 4);
        Player strategyCardHolder = payload.length == 4 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile tile = payload.length == 4 ? game.getTileByPosition(payload[1]) : null;
        Planet planet = tile == null || payload.length != 4
                ? null
                : tile.getUnitHolders().get(payload[2]) instanceof Planet p ? p : null;
        boolean coexist = payload.length == 4 && "coexist".equals(payload[3]);
        if (!player.hasUnlockedBreakthrough(TRAINING_DUMMIES)
                || strategyCardHolder == null
                || strategyCardHolder == player
                || planet == null
                || planet.getUnitKeysForPlayer(player).stream()
                        .map(player::getUnitFromUnitKey)
                        .noneMatch(unit -> unit != null && unit.getIsGroundForce())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String coexistenceFlag = game.getStoredValue("coexistFlag");
        if (coexist) {
            game.setStoredValue("coexistFlag", "yes");
        }
        try {
            AddUnitService.addUnits(event, tile, game, strategyCardHolder.getColor(), "1 infantry " + planet.getName());
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", coexistenceFlag);
            }
        }
        if (!coexist) {
            StartCombatService.startGroundCombat(player, strategyCardHolder, game, event, planet, tile);
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 infantry belonging to "
                        + strategyCardHolder.getRepresentationNoPing() + (coexist ? " into coexistence on " : " on ")
                        + planet.getRepresentation(game) + " due to _Training Programs_.");
        ButtonHelper.deleteMessage(event);
    }

    private static String trainingDummiesPrefix(Player player, Player strategyCardHolder) {
        return player.factionButtonChecker() + PLACE_TRAINING_DUMMIES_INFANTRY + strategyCardHolder.getFaction() + "|";
    }

    public static void clearTrainingDummiesState(Game game) {
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(TRAINING_DUMMIES_TG))
                .toList()
                .forEach(game::removeStoredValue);
    }
}
