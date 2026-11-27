package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.vaettir;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CombatMessageHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.DiceHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class VaettirTechHandler {
    private static final String WRATH = "aovaettiryw";
    private static final String USE_SACRIFICE = "vaettirSacrifice_";
    private static final String SELECT_SACRIFICE_ROOT = "selectVaettirSacrificeRoot_";

    public static Button getSacrificeButton(Player player) {
        return Buttons.red(
                player.factionButtonChecker() + USE_SACRIFICE, "Use Yggdrasil's Sacrifice", FactionEmojis.vaettir);
    }

    public static boolean canUseYggdrasilsSacrifice(Game game, Player player) {
        return player.hasTechReady("aovaettirys")
                && game.getTileMap().values().stream()
                        .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                        .anyMatch(planet -> VaettirAbilityHandler.hasRootToken(planet)
                                && game.getRealPlayers().stream()
                                        .anyMatch(planetController -> planetController
                                                .getExhaustedPlanets()
                                                .contains(planet.getName())));
    }

    @ButtonHandler(USE_SACRIFICE)
    public static void useYggdrasilsSacrifice(ButtonInteractionEvent event, Game game, Player player) {
        offerYggdrasilsSacrifice(event, game, player);
    }

    public static void offerYggdrasilsSacrifice(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream()
                        .filter(planet -> game.getRealPlayers().stream()
                                .anyMatch(planetController ->
                                        planetController.getExhaustedPlanets().contains(planet.getName())))
                        .flatMap(planet -> planet.getTokenList().stream()
                                .filter(token -> token.startsWith("attachment_root_"))
                                .map(token -> Buttons.green(
                                        player.factionButtonChecker() + SELECT_SACRIFICE_ROOT + tile.getPosition() + "|"
                                                + planet.getName() + "|" + token,
                                        "Remove " + getRootType(token) + " Root from "
                                                + Helper.getPlanetRepresentation(planet.getName(), game),
                                        FactionEmojis.vaettir))))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
            return;
        }
        player.exhaustTech("aovaettirys");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose a Root token to remove and ready that planet.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_SACRIFICE_ROOT)
    public static void resolveYggdrasilsSacrifice(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_SACRIFICE_ROOT.length()).split("\\|", 3);
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(payload[1]);
        if (planet == null || !payload[2].startsWith("attachment_root_") || !planet.removeToken(payload[2])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        VaettirAbilityHandler.syncOldGrowthRootCards(game);
        game.getRealPlayers().stream()
                .filter(planetController -> planetController.hasPlanet(planet.getName()))
                .findFirst()
                .ifPresent(planetController -> planetController.refreshPlanet(planet.getName()));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " removed a Root token from "
                        + Helper.getPlanetRepresentation(planet.getName(), game)
                        + " and readied it with _Yggdrasil's Sacrifice_.");
        ButtonHelper.deleteMessage(event);
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }

    public static boolean resolveYggdrasilsWrath(
            GenericInteractionCreateEvent event,
            Game game,
            Player attacker,
            Player defender,
            Tile tile,
            String planetName,
            int hits) {
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (hits < 1
                || attacker == defender
                || !defender.hasTech(WRATH)
                || planet == null
                || !defender.getPlanets().contains(planetName)
                || !VaettirAbilityHandler.hasRootToken(planet)) {
            return false;
        }
        List<DiceHelper.Die> dice = DiceHelper.rollDice(5, hits);
        int redirectedHits = DiceHelper.countSuccesses(dice);
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? defender.getCorrectChannel() : event.getMessageChannel(),
                CombatMessageHelper.displayCombatSummary(defender, tile, planet, CombatRollType.bombardment)
                        + CombatMessageHelper.displayUnitRoll(
                                getYggdrasilsWrathUnit(defender), 5, 0, 1, hits, 0, dice, redirectedHits)
                        + CombatMessageHelper.displayHitResults(redirectedHits));
        if (redirectedHits == 0) {
            return false;
        }
        int remainingHits = hits - redirectedHits;
        List<Button> attackerButtons =
                ButtonHelper.getButtonsForRemovingAllUnitsInSystem(attacker, game, tile, "bombardment");
        MessageHelper.sendMessageToChannelWithButtons(
                game.isFowMode() ? attacker.getCorrectChannel() : event.getMessageChannel(),
                attacker.getRepresentationUnfogged() + ", assign " + redirectedHits + " BOMBARDMENT "
                        + (redirectedHits == 1 ? "hit" : "hits")
                        + " from _Yggdrasil's Wrath_ to your units in this system.",
                attackerButtons);
        if (remainingHits > 0) {
            sendBombardmentAssignmentButtons(event, game, attacker, defender, tile, planetName, remainingHits);
        }
        return true;
    }

    private static void sendBombardmentAssignmentButtons(
            GenericInteractionCreateEvent event,
            Game game,
            Player attacker,
            Player defender,
            Tile tile,
            String planetName,
            int hits) {
        List<Button> buttons = new ArrayList<>(
                ButtonHelper.getButtonsForRemovingAllUnitsInSystem(defender, game, tile, "bombardment").stream()
                        .filter(button -> button.getCustomId() != null
                                && button.getCustomId().contains("_" + planetName + "_"))
                        .toList());
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                game.isFowMode() ? defender.getCorrectChannel() : event.getMessageChannel(),
                defender.getRepresentationUnfogged() + ", assign " + hits + " remaining BOMBARDMENT "
                        + (hits == 1 ? "hit" : "hits") + " from " + attacker.getRepresentationNoPing() + ".",
                buttons);
    }

    private static String getRootType(String token) {
        return token.replace("attachment_root_", "").replace(".png", "");
    }

    private static UnitModel getYggdrasilsWrathUnit(Player player) {
        UnitModel unit = new UnitModel();
        unit.setName("Yggdrasil's Wrath");
        unit.setAsyncId("vaettir_yggdrasils_wrath");
        unit.setId("vaettir_yggdrasils_wrath");
        unit.setBaseType("pds");
        unit.setFaction(player.getFaction());
        return unit;
    }
}
