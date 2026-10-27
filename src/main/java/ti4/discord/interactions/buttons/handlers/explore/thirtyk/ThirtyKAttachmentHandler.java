package ti4.discord.interactions.buttons.handlers.explore.thirtyk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thirtyk.ThirtyKTurnState;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.planet.PlanetService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class ThirtyKAttachmentHandler {

    public static final String PROFITABLE_FACTORIES = "attachment_profitablefactories30k.png";
    public static final String CARBON_CONSCIOUSNESS = "attachment_carbonconsciousness30k.png";
    public static final String INHOSPITABLE_ENVIRONMENT = "attachment_inhospitableenvironment30k.png";
    public static final String DEFENSIVE_BASTION = "attachment_defensivebastion30k.png";
    public static final String EFFICIENCY_TECHNIQUES = "attachment_efficiencytechniques30k.png";
    public static final String AFFORDABLE_MASS_PRODUCTION = "attachment_affordablemassproduction30k.png";
    public static final String PRODUCTION_RELAY = "attachment_productionrelayjmsc.png";
    public static final String OVERCHARGED_TARGETING = "attachment_overchargedtargetingsystemjmsc.png";

    private static final String PROFITABLE_EXHAUSTED_KEY = "thirtyKProfitableFactories_";
    private static final String CARBON_READY = "thirtyKCarbonReady_";
    private static final String CARBON_USED_KEY = "thirtyKCarbonUsed_";
    private static final String INHOSPITABLE_ROLL = "thirtyKInhospitableRoll_";
    private static final String RELAY_CHOOSE = "thirtyKRelayChoose_";
    private static final String BASTION_PLACE = "thirtyKBastionPlace_";

    public static void onPlanetExhausted(
            GenericInteractionCreateEvent event, Game game, Player player, String planetID) {
        Planet planet = game.getUnitHolderFromPlanet(planetID);
        if (planet == null) return;
        if (planet.getTokenList().contains(PROFITABLE_FACTORIES)) {
            resolveProfitableFactories(event, game, player, planetID);
        }
        if (planet.getTokenList().contains(CARBON_CONSCIOUSNESS)) {
            offerCarbonConsciousness(game, player, planet);
        }
    }

    static String readyCycleMarker(int round, String phase) {
        String lowerPhase = phase == null ? "" : phase.toLowerCase();
        boolean afterStatusReady = "statushomework".equals(lowerPhase) || lowerPhase.contains("agenda");
        return round + (afterStatusReady ? "s" : "");
    }

    private static void resolveProfitableFactories(
            GenericInteractionCreateEvent event, Game game, Player player, String planetID) {
        String key = PROFITABLE_EXHAUSTED_KEY + planetID;
        String marker = readyCycleMarker(game.getRound(), game.getPhaseOfGame());
        if (marker.equals(game.getStoredValue(key))) return;
        game.setStoredValue(key, marker);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                "The factories on " + Helper.getPlanetRepresentation(planetID, game) + " turn a tidy profit: "
                        + player.getRepresentationNoPing() + " gains 1 trade good and _Profitable Factories_ is"
                        + " exhausted.");
        ButtonHelperStats.gainTGs(event, game, player, 1, false);
    }

    private static void offerCarbonConsciousness(Game game, Player player, Planet planet) {
        if (carbonUsedThisTurn(game, planet.getName())) return;
        int cost = planet.getResources();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", you may spend " + cost + " influence to ready "
                        + Helper.getPlanetRepresentation(planet.getName(), game)
                        + " and place 1 infantry on it (_Carbon Consciousness_).",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + CARBON_READY + planet.getName(),
                                "Spend " + cost + " Influence To Ready"),
                        ThirtyKExploreSupport.decline(player)));
    }

    @ButtonHandler(CARBON_READY)
    public static void carbonReady(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetID = buttonID.substring(CARBON_READY.length());
        Tile tile = game.getTileFromPlanet(planetID);
        Planet planet = game.getUnitHolderFromPlanet(planetID);
        if (tile == null || planet == null) return;
        ButtonHelper.deleteMessage(event);
        if (carbonUsedThisTurn(game, planetID)) return;
        game.setStoredValue(CARBON_USED_KEY + planetID, "used");
        PlanetService.refreshPlanet(player, planetID);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 inf " + planetID);
        List<Button> buttons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "inf"));
        buttons.removeIf(button -> isSpendButtonFor(button, planetID));
        buttons.add(Buttons.red("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "The colony rallies: " + player.getRepresentationNoPing() + " readies "
                        + Helper.getPlanetRepresentation(planetID, game)
                        + " and 1 infantry joins its garrison (_Carbon Consciousness_). Please spend "
                        + planet.getResources() + " influence.",
                buttons);
    }

    private static boolean carbonUsedThisTurn(Game game, String planetID) {
        return !game.getStoredValue(CARBON_USED_KEY + planetID).isBlank();
    }

    public static void clearCarbonUsed(Game game) {
        ThirtyKTurnState.removeKeysStartingWith(game, CARBON_USED_KEY);
    }

    private static boolean isSpendButtonFor(Button button, String planetID) {
        String id = button.getCustomId();
        if (id == null || !id.startsWith("spend_")) return false;
        return planetID.equals(id.split("_")[1]);
    }

    public static void afterLanding(Game game, Player player, Tile tile, String planetID, UnitType type, int amount) {
        if (type != UnitType.Infantry || amount <= 0) return;
        offerInhospitableRoll(game, player, tile, planetID, amount);
    }

    public static void afterProduction(GenericInteractionCreateEvent event, Game game, Player player) {
        Map<String, Integer> produced = player.getCurrentProducedUnits();
        List<String> relayTiles = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : produced.entrySet()) {
            String[] parts = entry.getKey().split("_", 3);
            if (parts.length < 3) continue;
            Tile tile = game.getTileByPosition(parts[1]);
            if (tile == null) continue;
            if ("gf".equals(parts[0])) {
                offerInhospitableRoll(game, player, tile, parts[2], entry.getValue());
            }
            if ("ff".equals(parts[0])
                    && hasAttachmentInSystem(tile, PRODUCTION_RELAY)
                    && !relayTiles.contains(tile.getPosition())) {
                relayTiles.add(tile.getPosition());
                offerProductionRelay(event, game, player);
            }
        }
    }

    private static void offerInhospitableRoll(Game game, Player player, Tile tile, String planetID, int amount) {
        UnitHolder holder = tile.getUnitHolders().get(planetID);
        if (holder == null || !holder.getTokenList().contains(INHOSPITABLE_ENVIRONMENT)) return;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", " + amount + " infantry arrived on "
                        + Helper.getPlanetRepresentation(planetID, game)
                        + ", which has _Inhospitable Environment_: roll 1 die for each, and each 3 or less destroys 1"
                        + " infantry.",
                List.of(Buttons.red(
                        player.factionButtonChecker() + INHOSPITABLE_ROLL + tile.getPosition() + "_" + amount + "_"
                                + planetID,
                        "Roll " + amount + " Dice")));
    }

    @ButtonHandler(INHOSPITABLE_ROLL)
    public static void inhospitableRoll(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(INHOSPITABLE_ROLL.length()).split("_", 3);
        Tile tile = game.getTileByPosition(parts[0]);
        int amount = Integer.parseInt(parts[1]);
        String planetID = parts[2];
        if (tile == null || tile.getUnitHolders().get(planetID) == null) return;
        ButtonHelper.deleteMessage(event);
        List<Die> dice = DiceHelper.rollDice(4, amount);
        List<Integer> results = dice.stream().map(Die::getResult).toList();
        int onPlanet = tile.getUnitHolders().get(planetID).getUnitCount(UnitType.Infantry, player);
        int destroyed = Math.min(
                onPlanet, (int) results.stream().filter(result -> result <= 3).count());
        if (destroyed > 0) {
            DestroyUnitService.destroyUnits(
                    event, tile, game, player.getColor(), destroyed + " inf " + planetID, false);
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "The hostile world takes its toll: " + player.getRepresentationNoPing() + " rolled " + results
                        + " and lost " + destroyed + " infantry (_Inhospitable Environment_).");
    }

    private static void offerProductionRelay(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player other : game.getRealPlayers()) {
            buttons.add(FoWHelper.fogSafeTargetButton(
                    player.factionButtonChecker() + RELAY_CHOOSE + other.getFaction(), "green", other, player));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", you produced fighters next to _Production Relay_. Choose a player;"
                        + " they place 2 fighters in their home system if no other player's ships are there.",
                buttons);
    }

    @ButtonHandler(RELAY_CHOOSE)
    public static void relayChoose(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player chosen = game.getPlayerFromColorOrFaction(buttonID.substring(RELAY_CHOOSE.length()));
        if (chosen == null) return;
        ButtonHelper.deleteMessage(event);
        Tile home = chosen.getHomeSystemTile();
        boolean blocked = home == null || ThirtyKExploreSupport.otherPlayersHaveShips(game, chosen, home);
        if (!blocked) {
            AddUnitService.addUnits(event, home, game, chosen.getColor(), "2 ff");
        }
        String outcome = blocked
                ? "could not place fighters, because another player's ships are in their home system"
                : "placed 2 fighters in their home system";
        String chosenName =
                FoWHelper.identityOrColorIfCanSeeStats(game, chosen, player, chosen.getRepresentationNoPing());
        FoWHelper.notifyPlayerAndAffectedInFog(
                game,
                player,
                game.isFowMode() && chosen != player
                        ? chosenName + " receives the _Production Relay_ fighters if their home system is clear."
                        : chosenName + " " + outcome + " (_Production Relay_).",
                chosen,
                chosen.getRepresentationUnfogged() + ", you " + outcome.replace("their", "your")
                        + " (_Production Relay_).");
    }

    public static void beforeRetreatInto(Game game, Player player, Tile destination) {
        if (destination == null || destination.hasPlayerCC(player)) return;
        boolean ownsBastion = destination.getPlanetUnitHolders().stream()
                .anyMatch(planet -> planet.getTokenList().contains(DEFENSIVE_BASTION)
                        && player.getPlanets().contains(planet.getName()));
        if (!ownsBastion) return;
        List<Button> buttons = new ArrayList<>();
        for (int amount = 1; amount <= 4; amount++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + BASTION_PLACE + destination.getPosition() + "_" + amount,
                    "Place " + amount + " Fighter" + (amount == 1 ? "" : "s")));
        }
        buttons.add(ThirtyKExploreSupport.decline(player));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", you are retreating into a system with your _Defensive Bastion_."
                        + " You may place up to 4 fighters from your reinforcements there.",
                buttons);
    }

    @ButtonHandler(BASTION_PLACE)
    public static void bastionPlace(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(BASTION_PLACE.length()).split("_");
        Tile tile = game.getTileByPosition(parts[0]);
        int amount = Integer.parseInt(parts[1]);
        if (tile == null) return;
        ButtonHelper.deleteMessage(event);
        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " ff");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "The bastion's hangars open: " + player.getRepresentationNoPing() + " launches " + amount + " fighter"
                        + (amount == 1 ? "" : "s") + " (_Defensive Bastion_).");
    }

    public static int extraPlanetProduction(UnitHolder holder, int unitProduction) {
        if (unitProduction <= 0 || !(holder instanceof Planet)) return 0;
        return holder.getTokenList().contains(EFFICIENCY_TECHNIQUES) ? 2 : 0;
    }

    public static int productionDiscount(Game game, Map<String, Integer> producedUnits) {
        for (String produced : producedUnits.keySet()) {
            String[] parts = produced.split("_", 3);
            if (parts.length < 3 || !"ff".equals(parts[0])) continue;
            Tile tile = game.getTileByPosition(parts[1]);
            if (tile != null && hasAttachmentInSystem(tile, AFFORDABLE_MASS_PRODUCTION)) return 1;
        }
        return 0;
    }

    public static int spaceCannonBonus(UnitModel unit, UnitHolder holder) {
        if (unit == null || holder == null || unit.getSpaceCannonDieCount() <= 0) return 0;
        return holder.getTokenList().contains(OVERCHARGED_TARGETING) ? 1 : 0;
    }

    private static boolean hasAttachmentInSystem(Tile tile, String attachment) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> planet.getTokenList().contains(attachment));
    }
}
