package ti4.discord.interactions.buttons.handlers.explore.bluereverie;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Expeditions;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.MiscEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.fow.PlanetTargetService;
import ti4.service.leader.UnlockLeaderService;
import ti4.service.objectives.DrawSecretService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class BlueReverieExploreHandler {
    private static final String BEGIN_NEIGHBOR_PLACEMENT = "brExploreCoexistenceBegin";
    private static final String SELECT_NEIGHBOR = "brExploreCoexistenceNeighbor_";
    private static final String PLACE_NEIGHBOR_UNIT = "brExploreCoexistence_";
    private static final String PLACE_FORGOTTEN_LEGION = "brExploreForgottenLegion_";
    private static final String CLAIM_FORGOTTEN_LEGION_TG = "brExploreForgottenLegionTg";
    private static final String DECLINE_FORGOTTEN_LEGION_TG = "brExploreForgottenLegionDecline";
    private static final String EXTRACTION_SECRET = "extractionSecret_";
    private static final String EXTRACTION_COMMANDER = "extractionCommander_";
    private static final String EXTRACTION_SELECT_COMMANDER = "extractionSelectCommander_";
    private static final String EXTRACTION_SPEND_CC = "extractionSpendCc_";
    private static final String EXTRACTION_CHOICE = "extractionRunChoice_";
    private static final String EXPLORE_FRONTIER = "localInstabilityFrontier_";
    private static final String ADD_LOCAL_INSTABILITY_INGRESS = "localInstabilityIngress_";
    private static final String SPEND_FOR_STRUCTURE = "spendForStruct_";
    private static final String PLACE_OVERGROWN_STRUCTURE = "placeOvergrownStructure_";
    private static final String USE_CACHE_PRODUCTION = "useCacheProduction";
    private static final String SELECT_CACHE_PRODUCER = "selectCacheProducer_";
    private static final String RETIRED_ADVENTURERS_EXPEDITION = "retiredAdventurersExpedition_";
    private static final String RETIRED_ADVENTURERS_INFANTRY = "retiredAdventurersInfantry";
    private static final String RECOVERED_MEMORY_CORE_SPEAKER = "recoveredMemoryCoreSpeaker";
    private static final String RECOVERED_MEMORY_CORE_COMMAND_TOKENS = "recoveredMemoryCoreCommandTokens";

    @ButtonHandler(EXPLORE_FRONTIER)
    public static void resolveFrontierExplore(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.replace(EXPLORE_FRONTIER, "");
        Tile tile = game.getTileByPosition(position);
        ExploreService.expFront(event, tile, game, player, true);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(ADD_LOCAL_INSTABILITY_INGRESS)
    public static void addLocalInstabilityIngress(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(ADD_LOCAL_INSTABILITY_INGRESS.length()));
        if (tile == null || tile.hasIngress()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        tile.addToken(Constants.TOKEN_INGRESS, Constants.SPACE);
        if (game.isFowMode()) {
            FoWHelper.pingSystem(game, tile.getPosition(), "A new ingress tears into The Fracture.", false);
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Placed an ingress token in " + tile.getRepresentationForButtons() + ".");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SPEND_FOR_STRUCTURE)
    public static void spendForStructure(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SPEND_FOR_STRUCTURE.length()).split("\\|", 2);
        String payment = payload.length == 2 ? payload[0] : "";
        String planetName = payload.length == 2 ? payload[1] : "";
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<UnitModel> structures = getPlaceableOvergrownStructures(game, player, planet);
        if (structures.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You have no structures available to place.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if ("comm".equals(payment) && player.getCommodities() >= 2) {
            player.setCommodities(player.getCommodities() - 2);
        } else if ("tg".equals(payment) && player.getTg() >= 2) {
            player.setTg(player.getTg() - 2);
        } else {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        List<Button> buttons = structures.stream()
                .map(structure -> Buttons.green(
                        player.factionButtonChecker() + PLACE_OVERGROWN_STRUCTURE + structure.getAsyncId() + "|"
                                + planetName,
                        "Place " + structure.getName(),
                        structure.getUnitEmoji()))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose a structure to place on "
                        + Helper.getPlanetRepresentation(planetName, game) + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_OVERGROWN_STRUCTURE)
    public static void placeOvergrownStructure(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(PLACE_OVERGROWN_STRUCTURE.length()).split("\\|", 2);
        String unitAsyncId = payload.length == 2 ? payload[0] : "";
        String planetName = payload.length == 2 ? payload[1] : "";
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        UnitModel structure =
                player.getUnitsByAsyncID(unitAsyncId).stream().findFirst().orElse(null);
        if (tile == null
                || planet == null
                || structure == null
                || !getPlaceableOvergrownStructures(game, player, planet).contains(structure)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), structure.getAsyncId() + " " + planetName);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed " + structure.getName() + " on "
                        + Helper.getPlanetRepresentation(planetName, game) + " with _Overgrown Infrastructure_.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<UnitModel> getPlaceableOvergrownStructures(Game game, Player player, Planet planet) {
        return player.getUnitModels().stream()
                .filter(UnitModel::getIsStructure)
                .filter(UnitModel::getIsPlanetOnly)
                .filter(unit -> unit.getUnitType() != UnitType.Monument || game.isMonumentsMode())
                .filter(unit -> unit.canBePlacedOnPlanetTypes(planet.getPlanetTypes()))
                .filter(unit -> ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, unit.getAsyncId())
                        < player.getUnitCap(unit.getAsyncId()))
                .toList();
    }

    @ButtonHandler(USE_CACHE_PRODUCTION)
    public static void useMaterialCacheProduction(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getMaterialCacheProducerButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You have no units with PRODUCTION on the board.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", choose one of your units whose PRODUCTION value you will use for _Material Cache_.",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + SELECT_CACHE_PRODUCER, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_CACHE_PRODUCER)
    public static void selectMaterialCacheProducer(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String selectionMessage = player.getRepresentationNoPing()
                + ", choose one of your units whose PRODUCTION value you will use for _Material Cache_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                getMaterialCacheProducerButtons(game, player),
                selectionMessage,
                player.factionButtonChecker() + SELECT_CACHE_PRODUCER,
                buttonID)) {
            return;
        }
        String[] payload = buttonID.substring(SELECT_CACHE_PRODUCER.length()).split("\\|", 3);
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        UnitHolder holder = tile == null || payload.length != 3
                ? null
                : tile.getUnitHolders().get(payload[1]);
        UnitKey unitKey = holder == null
                ? null
                : holder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(payload[2]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
        if (tile == null || holder == null || unit == null || unit.getProductionValue() < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> productionButtons =
                Helper.getPlaceUnitButtons(event, player, game, tile, "materialCacheProduction", "place");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", use the PRODUCTION ability of " + unit.getName() + " in "
                        + tile.getRepresentationForButtons(game, player) + " due to _Material Cache_.",
                productionButtons);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getMaterialCacheProducerButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
                    UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
                    if (unit == null || unit.getProductionValue() < 1) {
                        continue;
                    }
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + SELECT_CACHE_PRODUCER + tile.getPosition() + "|"
                                    + holder.getName() + "|" + unitKey.asyncID(),
                            "Use " + unit.getName() + " in " + tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        return buttons;
    }

    public static List<Button> getRetiredAdventurersButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        if (game.isThundersEdge()) {
            game.getExpeditions().getExpeditionFactions().entrySet().stream()
                    .filter(entry -> entry.getValue() == null)
                    .forEach(entry -> buttons.add(Buttons.green(
                            player.factionButtonChecker() + RETIRED_ADVENTURERS_EXPEDITION + entry.getKey(),
                            "Resolve " + getExpeditionName(entry.getKey()))));
        }
        if (game.getTileFromPlanet(Constants.THUNDERSEDGE) != null) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + RETIRED_ADVENTURERS_INFANTRY,
                    "Place 3 Infantry on Thunder's Edge"));
        }
        return buttons;
    }

    @ButtonHandler(RETIRED_ADVENTURERS_EXPEDITION)
    public static void resolveRetiredAdventurersExpedition(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String expedition = buttonID.substring(RETIRED_ADVENTURERS_EXPEDITION.length());
        if (!game.isThundersEdge()
                || !game.getExpeditions().getExpeditionFactions().containsKey(expedition)
                || game.getExpeditions().getExpeditionFactions().get(expedition) != null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Expeditions.setExpedition(game, expedition, player.getFaction());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " resolved the " + getExpeditionName(expedition)
                        + " expedition without paying its cost due to _Retired Adventurers_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RETIRED_ADVENTURERS_INFANTRY)
    public static void placeRetiredAdventurersInfantry(ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = game.getTileFromPlanet(Constants.THUNDERSEDGE);
        Planet thundersEdge = game.getUnitHolderFromPlanet(Constants.THUNDERSEDGE);
        if (tile == null || thundersEdge == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        int infantryBefore = thundersEdge.getUnitCount(UnitType.Infantry, player);
        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "3 infantry " + Constants.THUNDERSEDGE);
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", coexistenceFlag);
            }
        }
        int infantryPlaced = thundersEdge.getUnitCount(UnitType.Infantry, player) - infantryBefore;
        if (infantryPlaced > 0) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " placed " + infantryPlaced
                            + " infantry into coexistence on Thunder's Edge due to _Retired Adventurers_.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " could not place infantry on Thunder's Edge.");
        }
        ButtonHelper.deleteMessage(event);
    }

    private static String getExpeditionName(String expedition) {
        return switch (expedition) {
            case "techSkip" -> "technology specialty";
            case "tradeGoods" -> "3 trade goods";
            case "fiveRes" -> "5 resources";
            case "fiveInf" -> "5 influence";
            case "secret" -> "secret objective";
            case "actionCards" -> "2 action cards";
            default -> "expedition";
        };
    }

    public static List<Button> getRecoveredMemoryCoreButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        if (game.getSpeaker() != player) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + RECOVERED_MEMORY_CORE_SPEAKER, "Gain the Speaker Token"));
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + RECOVERED_MEMORY_CORE_COMMAND_TOKENS, "Gain 2 Command Tokens"));
        return buttons;
    }

    @ButtonHandler(RECOVERED_MEMORY_CORE_SPEAKER)
    public static void gainRecoveredMemoryCoreSpeaker(ButtonInteractionEvent event, Game game, Player player) {
        if (game.getSpeaker() != player) {
            game.setSpeaker(player);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " gained the " + MiscEmojis.SpeakerToken
                            + " speaker token due to _Recovered Memory Core_.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RECOVERED_MEMORY_CORE_COMMAND_TOKENS)
    public static void gainRecoveredMemoryCoreCommandTokens(ButtonInteractionEvent event, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", gain 2 command tokens due to _Recovered Memory Core_.",
                ButtonHelper.getGainCCButtons(player));
        ButtonHelper.deleteMessage(event);
    }

    public static void offerForgottenLegionButtons(
            GenericInteractionCreateEvent event, Player player, String planetName) {
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + PLACE_FORGOTTEN_LEGION + "1|" + planetName,
                        "Place 1 Neutral Infantry"),
                Buttons.green(
                        player.factionButtonChecker() + PLACE_FORGOTTEN_LEGION + "2|" + planetName,
                        "Place 2 Neutral Infantry"),
                Buttons.green(
                        player.factionButtonChecker() + PLACE_FORGOTTEN_LEGION + "3|" + planetName,
                        "Place 3 Neutral Infantry"),
                Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", choose how many neutral infantry to place for _Forgotten Legion_.",
                buttons);
    }

    @ButtonHandler(PLACE_FORGOTTEN_LEGION)
    public static void placeForgottenLegionInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(PLACE_FORGOTTEN_LEGION.length()).split("\\|", 2);
        int amount = payload.length == 2 ? getAmount(payload[0]) : 0;
        String planetName = payload.length == 2 ? payload[1] : "";
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (amount < 1 || amount > 3 || tile == null || planet == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, game.getNeutralColor(), amount + " infantry " + planetName);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed " + amount + " neutral infantry on "
                        + Helper.getPlanetRepresentation(planetName, game) + " using _Forgotten Legion_.");
        StartCombatService.startGroundCombat(player, game.getNeutral(), game, event, planet, tile);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                "Press this button for each neutral infantry you destroyed after resolving combat.",
                List.of(
                        Buttons.green(player.factionButtonChecker() + CLAIM_FORGOTTEN_LEGION_TG, "Gain 1 Trade Good"),
                        Buttons.red(player.factionButtonChecker() + DECLINE_FORGOTTEN_LEGION_TG, "Done")));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CLAIM_FORGOTTEN_LEGION_TG)
    public static void claimForgottenLegionTradeGood(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.gainTGs(event, game, player, 1, false);
    }

    @ButtonHandler(DECLINE_FORGOTTEN_LEGION_TG)
    public static void declineForgottenLegionTradeGoods(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(EXTRACTION_SECRET)
    public static void resolveExtractionSecret(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        ExtractionChoice choice = getExtractionChoice(buttonID.substring(EXTRACTION_SECRET.length()));
        if (choice == null || !canResolveExtractionChoice(game, player, choice)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        if (choice.removeInfantry()) {
            removeExtractionInfantry(event, game, player, choice.planetName());
        }
        DrawSecretService.drawSO(event, game, player, 1, false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(EXTRACTION_COMMANDER)
    public static void resolveExtractionCommander(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        ExtractionChoice choice = getExtractionChoice(buttonID.substring(EXTRACTION_COMMANDER.length()));
        if (choice == null || !canResolveExtractionChoice(game, player, choice)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You no longer have the required unit on that planet.");
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Leader> commanders = getLockedCommanders(player);
        if (commanders.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You do not have a locked commander to unlock.");
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        if (player.getTacticalCC() + player.getFleetCC() + player.getStrategicCC() < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You need a command token on your command sheet.");
            return;
        }
        setExtractionChoice(game, player, choice);
        if (commanders.size() == 1) {
            offerExtractionCommandTokenButtons(event, player, commanders.getFirst());
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Leader commander : commanders) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + EXTRACTION_SELECT_COMMANDER + commander.getId(),
                    "Unlock " + commander.getName()));
        }
        MessageHelper.editMessageWithButtons(
                event, player.getRepresentationNoPing() + ", choose a commander to unlock.", buttons);
    }

    @ButtonHandler(EXTRACTION_SELECT_COMMANDER)
    public static void selectExtractionCommander(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        ExtractionChoice choice = getExtractionChoice(game, player);
        Leader commander = player.unsafeGetLeader(buttonID.substring(EXTRACTION_SELECT_COMMANDER.length()));
        if (choice == null
                || commander == null
                || !commander.isLocked()
                || !canResolveExtractionChoice(game, player, choice)) {
            clearExtractionChoice(game, player);
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        offerExtractionCommandTokenButtons(event, player, commander);
    }

    @ButtonHandler(EXTRACTION_SPEND_CC)
    public static void spendExtractionCommandToken(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(EXTRACTION_SPEND_CC.length()).split("\\|", 2);
        String pool = payload[0];
        ExtractionChoice choice = getExtractionChoice(game, player);
        Leader commander = payload.length == 2 ? player.unsafeGetLeader(payload[1]) : null;
        if (choice == null
                || commander == null
                || !commander.isLocked()
                || !canResolveExtractionChoice(game, player, choice)) {
            clearExtractionChoice(game, player);
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        boolean spent =
                switch (pool) {
                    case "tactical" -> spendTacticalToken(player);
                    case "fleet" -> spendFleetToken(player);
                    case "strategy" -> spendStrategyToken(player);
                    default -> false;
                };
        if (!spent) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        if (choice.removeInfantry()) {
            removeExtractionInfantry(event, game, player, choice.planetName());
        }
        UnlockLeaderService.unlockLeader(commander.getId(), game, player);
        clearExtractionChoice(game, player);
        ButtonHelper.deleteMessage(event);
    }

    private static void offerExtractionCommandTokenButtons(
            ButtonInteractionEvent event, Player player, Leader commander) {
        List<Button> buttons = new ArrayList<>();
        if (player.getTacticalCC() > 0) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + EXTRACTION_SPEND_CC + "tactical|" + commander.getId(),
                    "Spend 1 Tactical Token"));
        }
        if (player.getFleetCC() > 0) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + EXTRACTION_SPEND_CC + "fleet|" + commander.getId(),
                    "Spend 1 Fleet Token"));
        }
        if (player.getStrategicCC() > 0) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + EXTRACTION_SPEND_CC + "strategy|" + commander.getId(),
                    "Spend 1 Strategy Token"));
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose a command token to spend to unlock " + commander.getName()
                        + ".",
                buttons);
    }

    private static List<Leader> getLockedCommanders(Player player) {
        return player.getLeaders().stream()
                .filter(leader -> Constants.COMMANDER.equals(leader.getType()))
                .filter(Leader::isLocked)
                .toList();
    }

    private static ExtractionChoice getExtractionChoice(Game game, Player player) {
        return getExtractionChoice(game.getStoredValue(EXTRACTION_CHOICE + player.getFaction()));
    }

    private static void setExtractionChoice(Game game, Player player, ExtractionChoice choice) {
        game.setStoredValue(
                EXTRACTION_CHOICE + player.getFaction(),
                (choice.removeInfantry() ? "inf" : "mech") + "|" + choice.planetName());
    }

    private static void clearExtractionChoice(Game game, Player player) {
        game.removeStoredValue(EXTRACTION_CHOICE + player.getFaction());
    }

    @ButtonHandler(BEGIN_NEIGHBOR_PLACEMENT)
    public static void chooseNeighbor(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getEligibleNeighbors(game, player).stream()
                .map(neighbor -> FoWHelper.fogSafeTargetButton(
                        player.factionButtonChecker() + SELECT_NEIGHBOR + neighbor.getFaction(),
                        "gray",
                        neighbor,
                        player))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event, player.getRepresentationNoPing() + ", choose a neighboring player.", buttons);
    }

    @ButtonHandler(SELECT_NEIGHBOR)
    public static void choosePlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player neighbor = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_NEIGHBOR.length()));
        if (neighbor == null || !getEligibleNeighbors(game, player).contains(neighbor)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = getNeighborPlanetButtons(game, player, neighbor);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose one of "
                        + FoWHelper.identityOrColorIfCanSeeStats(
                                game, neighbor, player, neighbor.getRepresentationNoPing())
                        + "'s non-home planets.",
                NewStuffHelper.buttonPagination(
                        buttons, player.factionButtonChecker() + PLACE_NEIGHBOR_UNIT + neighbor.getFaction() + "|", 0));
    }

    @ButtonHandler(PLACE_NEIGHBOR_UNIT)
    public static void placeNeighborUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(PLACE_NEIGHBOR_UNIT.length()).split("\\|", 2);
        Player neighbor = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        List<Button> buttons = neighbor == null ? List.of() : getNeighborPlanetButtons(game, player, neighbor);
        if (neighbor != null
                && NewStuffHelper.checkAndHandlePaginationChange(
                        event,
                        event.getMessageChannel(),
                        buttons,
                        "Choose a neighboring player's non-home planet.",
                        player.factionButtonChecker() + PLACE_NEIGHBOR_UNIT + neighbor.getFaction() + "|",
                        buttonID)) {
            return;
        }
        String planetName = payload.length == 2 ? payload[1] : "";
        if (neighbor == null || !isNeighborPlanet(game, player, neighbor, planetName)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        Tile tile = game.getTileFromPlanet(planetName);
        String previousCoexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planetName);
        } finally {
            if (previousCoexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", previousCoexistenceFlag);
            }
        }
        ButtonHelperAbilities.oceanBoundCheck(game);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed 1 infantry into coexistence on "
                        + Helper.getPlanetRepresentation(planetName, game) + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getNeighborPlanetButtons(Game game, Player player, Player neighbor) {
        List<Button> buttons = new ArrayList<>();
        Set<String> knownPlanets = PlanetTargetService.knownPlanetIds(game, player, Set.of());
        for (String planetName : knownPlanets.stream().sorted().toList()) {
            if (!isNeighborPlanet(game, player, neighbor, planetName)) continue;
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_NEIGHBOR_UNIT + neighbor.getFaction() + "|" + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }
        return buttons;
    }

    private static List<Player> getEligibleNeighbors(Game game, Player player) {
        return player.getNeighbouringPlayers(true).stream()
                .filter(neighbor ->
                        getNeighborPlanetButtons(game, player, neighbor).size() > 0)
                .toList();
    }

    private static boolean isNeighborPlanet(Game game, Player player, Player neighbor, String planetName) {
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        Player controller = game.getPlayerThatControlsPlanet(planetName, true);
        return planet != null
                && tile != null
                && planet.getPlanetModel() != null
                && !planet.isSpaceStation()
                && !tile.isHomeSystem(game)
                && controller == neighbor
                && player.getNeighbouringPlayers(true).contains(neighbor);
    }

    private static int getAmount(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static ExtractionChoice getExtractionChoice(String payload) {
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2 || !("mech".equals(parts[0]) || "inf".equals(parts[0]))) return null;
        return new ExtractionChoice(parts[1], "inf".equals(parts[0]));
    }

    private static boolean canResolveExtractionChoice(Game game, Player player, ExtractionChoice choice) {
        Planet planet = game.getUnitHolderFromPlanet(choice.planetName());
        if (planet == null) return false;
        UnitType unitType = choice.removeInfantry() ? UnitType.Infantry : UnitType.Mech;
        return planet.getUnitCount(unitType, player) > 0;
    }

    private static void removeExtractionInfantry(
            ButtonInteractionEvent event, Game game, Player player, String planetName) {
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile != null) {
            RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 infantry " + planetName);
        }
    }

    private static boolean spendTacticalToken(Player player) {
        if (player.getTacticalCC() < 1) return false;
        player.setTacticalCC(player.getTacticalCC() - 1);
        return true;
    }

    private static boolean spendFleetToken(Player player) {
        if (player.getFleetCC() < 1) return false;
        player.setFleetCC(player.getFleetCC() - 1);
        return true;
    }

    private static boolean spendStrategyToken(Player player) {
        if (player.getStrategicCC() < 1) return false;
        player.setStrategicCC(player.getStrategicCC() - 1);
        return true;
    }

    private record ExtractionChoice(String planetName, boolean removeInfantry) {}
}
