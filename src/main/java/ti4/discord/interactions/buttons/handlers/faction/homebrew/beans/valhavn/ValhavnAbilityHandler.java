package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.valhavn;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.vanguard.VanguardAbilitiesHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.vanguard.VanguardBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.vanguard.VanguardLeadersHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.planet.AddPlanetToPlayAreaService;
import ti4.service.tech.PlayerTechService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;
import ti4.spring.service.gameevent.GameEventDraft;
import ti4.spring.service.gameevent.GameSubEvent;

@UtilityClass
public class ValhavnAbilityHandler {

    private static final String REAVE = "reave";
    private static final String REAVE_USED = "reaveUsed_";
    private static final String VALHOLLS_CALL = "valholls_call";
    private static final String VALHOLLS_CALL_PLACE = "valhollsCallPlace_";
    private static final String BLOODLUST = "bloodlust";
    private static final String BLOODLUST_USED = "bloodlustUsed_";

    private static boolean canOfferBloodlust(Game game, Player player, Planet planet) {
        return player.hasAbility(BLOODLUST)
                && game.getStoredValue(getBloodlustUsedKey(player)).isEmpty()
                && !player.hasPlanet(planet.getName())
                && planet.getUnitKeys().stream().allMatch(player::unitBelongsToPlayer);
    }

    public static boolean offerBloodlust(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, Planet planet) {
        if (!canOfferBloodlust(game, player, planet)) {
            return false;
        }
        List<Button> buttons = List.of(
                Buttons.green(player.factionButtonChecker() + "useBloodlust_" + planet.getName(), "Use Bloodlust"),
                Buttons.gray(
                        player.factionButtonChecker() + "declineBloodlust_" + planet.getName(), "Continue Normally"));
        MessageHelper.sendMessageToChannelWithButtons(
                getResolutionChannel(event, game, player),
                player.getRepresentationNoPing() + ", you may use **Bloodlust** to resolve a ground combat on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + "."
                        + "\n-# This may only be done once per action.",
                buttons);
        return true;
    }

    public static void resolveBloodlust(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, Planet planet, boolean eligible) {
        if (!eligible
                || !player.hasAbility(BLOODLUST)
                || !game.getStoredValue(getBloodlustUsedKey(player)).isEmpty()
                || player.hasPlanet(planet.getName())) {
            return;
        }
        game.setStoredValue(getBloodlustUsedKey(player), "used");
        GameEventDraft.stage(game, new GameSubEvent.Combat("ground", tile.getPosition(), planet.getName(), null));
        game.setStoredValue(
                "currentActionSummary" + player.getFaction(),
                game.getStoredValue("currentActionSummary" + player.getFaction()) + " Won a ground combat on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + " with _Bloodlust_.");
        MessageHelper.sendMessageToChannel(
                getResolutionChannel(event, game, player),
                player.getRepresentationNoPing() + " won a ground combat on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + " with **Bloodlust**.");
        offerGroundCombatWinEffects(event, game, player, planet);
    }

    private static void offerGroundCombatWinEffects(
            GenericInteractionCreateEvent event, Game game, Player player, Planet planet) {
        if (player.hasTech("dxa")) {
            MessageHelper.sendMessageToChannelWithButtons(
                    getResolutionChannel(event, game, player),
                    player.getRepresentationNoPing() + ", you may use _Dacxive Animators_ to place 1 infantry on "
                            + Helper.getPlanetRepresentation(planet.getName(), game) + ".",
                    ButtonHelper.getDacxiveButtons(planet.getName(), player));
        }
        if (player.hasTech("dxa_br") && !player.hasTechReady("dxa_br")) {
            player.refreshTech("dxa_br");
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getRepresentation() + " readied _Daxcive Animators_.");
        }
        if (player.hasTech("bevalhavng") && !player.hasTechReady("bevalhavng")) {
            PlayerTechService.refreshTech(event, player, "bevalhavng");
        }
        Button commanderUnlock = VanguardLeadersHandler.getCommanderUnlockButton(player);
        if (commanderUnlock != null) {
            MessageHelper.sendMessageToChannelWithButton(
                    getResolutionChannel(event, game, player),
                    player.getRepresentationNoPing()
                            + ", you may unlock _Shieldbrother Michael_ after winning this ground combat.",
                    commanderUnlock);
        }
        VanguardBreakthroughHandler.offerTrainingDummiesGroundCombatReward(player);
        VanguardAbilitiesHandler.sendFundamentalsButton(player);
        if (player.hasAbility("war_stories")) {
            MessageHelper.sendMessageToChannelWithButtons(
                    getResolutionChannel(event, game, player),
                    player.getRepresentationNoPing()
                            + ", you may use **War Stories** to explore a planet you control after winning this combat.",
                    List.of(
                            Buttons.green("warStoriesPlanetExplore", "Explore a Planet You Control"),
                            Buttons.DECLINE_DELETE_BUTTONS));
        }
        if (player.hasTech("thvanguardy")
                && game.getStoredValue("vanguardReinforce" + player.getFaction())
                        .isEmpty()
                && !game.getActiveSystem().isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    getResolutionChannel(event, game, player),
                    player.getRepresentationNoPing()
                            + ", you must use _Reinforce_ to give the active system PRODUCTION 3 until the end of this action.",
                    List.of(Buttons.green(
                            player.factionButtonChecker() + "useVanguardReinforce",
                            "Use Reinforce",
                            FactionEmojis.vanguard)));
        }
    }

    public static void offerValhollsCall(GenericInteractionCreateEvent event, Game game, List<RemovedUnit> units) {
        for (Player player : game.getRealPlayers()) {
            if (!player.hasAbility(VALHOLLS_CALL) || !lostInfantryInGroundCombat(game, player, units)) {
                continue;
            }
            List<Button> buttons = getValhollsCallButtons(game, player);
            if (buttons.isEmpty()) {
                continue;
            }
            buttons.add(Buttons.DECLINE_DELETE_BUTTONS);
            MessageHelper.sendMessageToChannelWithButtons(
                    getResolutionChannel(event, game, player),
                    player.getRepresentation() + ", you may use **Valhöll's Call** to place 1 infantry from your "
                            + "reinforcements on a planet you control that contains your PDS."
                            + "\n-# You cannot place it back on the same planet.",
                    buttons);
        }
    }

    @ButtonHandler(VALHOLLS_CALL_PLACE)
    public static void resolveValhollsCall(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace(VALHOLLS_CALL_PLACE, "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null
                || tile == null
                || !player.hasAbility(VALHOLLS_CALL)
                || !player.getPlanets().contains(planetName)
                || planet.getUnitCount(UnitType.Pds, player) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planetName);
        MessageHelper.sendMessageToChannel(
                getResolutionChannel(event, game, player),
                player.getRepresentationNoPing() + " used **Valhöll's Call** to place 1 infantry on "
                        + Helper.getPlanetRepresentation(planetName, game) + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean offerReave(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, Planet planet) {
        if (!canOfferReave(game, player, tile, planet)) {
            return false;
        }

        List<Button> buttons = List.of(
                Buttons.green(player.factionButtonChecker() + "useReave_" + planet.getName(), "Use Reave"),
                Buttons.gray(player.factionButtonChecker() + "declineReave_" + planet.getName(), "Gain Control"));
        MessageHelper.sendMessageToChannelWithButtons(
                getResolutionChannel(event, game, player),
                player.getRepresentationUnfogged() + ", you may use **Reave** on "
                        + Helper.getPlanetRepresentation(planet.getName(), game)
                        + " instead of gaining control of it.",
                buttons);
        return true;
    }

    private static boolean offerReaveForBloodlust(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, Planet planet) {
        if (!player.hasAbility(REAVE)
                || !game.getStoredValue(getUsedKey(player)).isEmpty()) {
            return false;
        }
        List<Button> buttons = List.of(
                Buttons.green(player.factionButtonChecker() + "useReaveBloodlust_" + planet.getName(), "Use Reave"),
                Buttons.gray(
                        player.factionButtonChecker() + "declineReaveBloodlust_" + planet.getName(), "Gain Control"));
        MessageHelper.sendMessageToChannelWithButtons(
                getResolutionChannel(event, game, player),
                player.getRepresentationUnfogged() + ", you may use **Reave** on "
                        + Helper.getPlanetRepresentation(planet.getName(), game)
                        + " instead of gaining control of it.",
                buttons);
        return true;
    }

    @ButtonHandler("useBloodlust_")
    public static void useBloodlust(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace("useBloodlust_", "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null
                || tile == null
                || !player.hasAbility(BLOODLUST)
                || !game.getStoredValue(getBloodlustUsedKey(player)).isEmpty()
                || player.hasPlanet(planetName)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!offerReaveForBloodlust(event, game, player, tile, planet)) {
            resolveBloodlust(event, game, player, tile, planet, true);
            AddPlanetToPlayAreaService.gainControl(event, tile, planet, game, player);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("declineBloodlust_")
    public static void declineBloodlust(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace("declineBloodlust_", "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet != null && tile != null && !player.hasPlanet(planetName)) {
            AddPlanetToPlayAreaService.gainControl(event, tile, planet, game, player);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("useReave_")
    public static void useReave(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace("useReave_", "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (!player.hasAbility(REAVE)
                || !game.getStoredValue(getUsedKey(player)).isEmpty()
                || player.hasPlanet(planetName)
                || planet == null
                || tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(getUsedKey(player), "used");
        DestroyUnitService.destroyAllPlayerUnits(event, game, player, tile, planet, false);
        int tradeGoods = planet.getResources();
        String tradeGoodGain = tradeGoods > 0 ? player.gainTG(tradeGoods, true) : "";
        if (tradeGoods > 0) {
            ButtonHelperAgents.resolveArtunoCheck(player, tradeGoods);
        }
        MessageHelper.sendMessageToChannel(
                getResolutionChannel(event, game, player),
                player.getRepresentationNoPing() + " used **Reave**, destroyed their units on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + ", and gained "
                        + tradeGoods
                        + " trade "
                        + (tradeGoods == 1 ? "good " : "goods ")
                        + tradeGoodGain
                        + ". The ground combat ended in a draw.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("useReaveBloodlust_")
    public static void useReaveBloodlust(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace("useReaveBloodlust_", "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null
                || tile == null
                || !player.hasAbility(REAVE)
                || !player.hasAbility(BLOODLUST)
                || !game.getStoredValue(getUsedKey(player)).isEmpty()
                || !game.getStoredValue(getBloodlustUsedKey(player)).isEmpty()
                || player.hasPlanet(planetName)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(getBloodlustUsedKey(player), "used");
        game.setStoredValue(getUsedKey(player), "used");
        DestroyUnitService.destroyAllPlayerUnits(event, game, player, tile, planet, false);
        int tradeGoods = planet.getResources();
        String tradeGoodGain = tradeGoods > 0 ? player.gainTG(tradeGoods, true) : "";
        if (tradeGoods > 0) {
            ButtonHelperAgents.resolveArtunoCheck(player, tradeGoods);
        }
        MessageHelper.sendMessageToChannel(
                getResolutionChannel(event, game, player),
                player.getRepresentationNoPing() + " used **Reave**, destroyed their units on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + ", and gained "
                        + tradeGoods
                        + " trade "
                        + (tradeGoods == 1 ? "good " : "goods ")
                        + tradeGoodGain
                        + ". The ground combat ended in a draw.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("declineReaveBloodlust_")
    public static void declineReaveBloodlust(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace("declineReaveBloodlust_", "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet != null && tile != null && !player.hasPlanet(planetName)) {
            resolveBloodlust(event, game, player, tile, planet, true);
            AddPlanetToPlayAreaService.gainControl(event, tile, planet, game, player);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("declineReave_")
    public static void declineReave(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace("declineReave_", "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet != null && tile != null) {
            AddPlanetToPlayAreaService.gainControl(event, tile, planet, game, player);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void clearReaveTurnUse(Game game, Player player) {
        game.removeStoredValue(getUsedKey(player));
    }

    public static void clearBloodlustActionUse(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(getBloodlustUsedKey(player));
        }
    }

    private static boolean canOfferReave(Game game, Player player, Tile tile, Planet planet) {
        return player.hasAbility(REAVE)
                && !player.hasPlanet(planet.getName())
                && game.getStoredValue(getUsedKey(player)).isEmpty()
                && (!game.getStoredValue(
                                "combatRoundTracker" + player.getFaction() + tile.getPosition() + planet.getName())
                        .isEmpty());
    }

    private static boolean lostInfantryInGroundCombat(Game game, Player player, List<RemovedUnit> units) {
        return units.stream()
                .anyMatch(unit -> player.unitBelongsToPlayer(unit.unitKey())
                        && unit.unitKey().unitType() == UnitType.Infantry
                        && unit.uh() instanceof Planet planet
                        && game.getStoredValueMap()
                                .containsKey("combatRoundTracker" + player.getFaction()
                                        + unit.tile().getPosition() + planet.getName()));
    }

    private static List<Button> getValhollsCallButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null || planet.getUnitCount(UnitType.Pds, player) < 1) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + VALHOLLS_CALL_PLACE + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }
        return buttons;
    }

    private static String getUsedKey(Player player) {
        return REAVE_USED + player.getFaction();
    }

    private static String getBloodlustUsedKey(Player player) {
        return BLOODLUST_USED + player.getFaction();
    }

    private static MessageChannel getResolutionChannel(GenericInteractionCreateEvent event, Game game, Player player) {
        return game.isFowMode() || event == null ? player.getCorrectChannel() : event.getMessageChannel();
    }
}
