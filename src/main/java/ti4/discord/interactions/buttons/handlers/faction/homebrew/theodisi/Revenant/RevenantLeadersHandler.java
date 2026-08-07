package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Revenant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion.OblivionUnitHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.thundersedge.DSHelperBreakthroughs;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.leader.PlayHeroService;
import ti4.service.leader.PurgeHeroService;
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class RevenantLeadersHandler {
    // Revenant of Arcanum
    private static final String REVARCAGENT = "revenantarcanumagent";
    private static final String USE_REVARCAGENT = "useRevArcanumAgent";
    private static final String REVARCAGENT_EXPLORE_OPTIONS = "revArcanumAgentExploreOptions_";
    private static final String REVARCAGENT_PLANET = "useRevArcanumAgentPlanet_";
    private static final String REVARCAGENT_WINDOW = "revArcanumAgentWindow_";
    private static final Set<String> EXPLORATION_TRAITS =
            Set.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL);
    // Revenant of Oblivion
    private static final String REVOBLCOMMANDER = "revenantoblivioncommander";
    private static final String USE_ARLIR_MIRRORED = "useArlirMirrored_";
    // Revenant of Kairn
    private static final String CHOOSE_EXP_DECK = "chooseRevKairnExpDeck_";
    private static final String CHOOSE_EXP_CARD = "chooseRevKairnExpCard_";
    private static final String BACK_TO_REV_KAIRN_DECKS = "backToRevKairnDecks";
    private static final String FINISH_REV_KAIRN_DISCARDS = "finishRevKairnDiscards";
    private static final String CHOOSE_REV_KAIRN_TRAIT = "chooseRevKairnTrait_";
    private static final String CHOOSE_REV_KAIRN_PLANET = "chooseRevKairnPlanet_";
    private static final String REV_KAIRN_HERO_ACTIVE = "revKairnHeroActive_";
    private static final String REV_KAIRN_HERO_DECKS = "revKairnHeroDecks_";
    private static final String REV_KAIRN_HERO_TRAITS = "revKairnHeroTraits_";
    private static final List<String> EXPLORE_DECK_TYPES =
            List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL, Constants.FRONTIER);
    // Revenant Base Agent
    private static final String REVBASE = "revenantagent";
    private static final String USE_REVBASE = "readyTechWithRevBase_";
    private static final String SELECT_REVBASE_TARGET = "selectRevenantAgentTarget";
    private static final String USE_REVBASE_TARGET = "useRevenantAgentOn_";
    // Revenant Base Hero
    private static final String REVENANT_HERO = "revenanthero";
    private static final String SELECT_REVENANT_HERO = "selectRevenantHero_";
    private static final String REVENANT_HERO_CHOICES = "revenantHeroChoices_";
    private static final String REVENANT_UNAVAILABLE_HEROES = "revenantUnavailableHeroes";
    // Revenant of Verydith
    private static final String REVVERYDITH = "revenantverydithagent";
    private static final String SELECT_REVVERYDITH_TARGET = "selectRevenantTarget";
    private static final String RESOLVE_REVVERYDITH_TARGET = "resolveRevenantTarget_";
    private static final String PRODUCE_WITH_REVVERYDITH = "produceUsingRevVerydithAgent";
    private static final String PAGE_REVVERYDITH_SYSTEMS = "pageRevenantVerydithSystems_";
    // Revenant of Myrr
    private static final String REVMYRR = "revenantmyrrcommander";
    private static final String PLACE_REVMYRR_UNIT = "placeRevenantMyrrUnit_";
    private static final String REVMYRR_USED = "revenantMyrrCommanderUsed_";
    // Revenant of Ruin
    private static final String USE_REVTHRONES = "useRevenantThronesHero";
    private static final String SELECT_REVTHRONES_SYSTEM = "selectRevenantThronesSystem_";
    private static final String REVTHRONES_PRODUCTION = "revenantThronesProduction_";
    // Revenant of Xytheris
    private static final String REVXYTHERIS = "revenantxytherisagent";
    private static final String USE_REVXYTHERIS = "useRevenantXytherisAgent_";
    private static final String SELECT_REVXYTHERIS_TARGET = "selectRevenantXytherisTarget";
    private static final String REVXYTHERIS_WINDOW = "revenantXytherisAgentWindow";
    private static final String REVXYTHERIS_TARGET = "revenantXytherisAgentTarget_";
    // Revenant of Ponthous
    private static final String REVPONTHOUS = "revenantponthouscommander";
    private static final String USE_REVPONTHOUS = "useRevenantPonthousCommander_";
    private static final String REVPONTHOUS_OFFERED = "revenantPonthousCommanderOffered_";
    private static final String REVPONTHOUS_USED = "revenantPonthousCommanderUsed_";
    // Revenant of Kryxos
    private static final String REVKRYXOS = "revenantkryxoshero";
    private static final String USE_REVKRYXOS = "useRevenantKryxosHero_";
    private static final String SELECT_REVKRYXOS_TECH = "selectRevenantKryxosTech_";
    private static final String DECLINE_REVKRYXOS = "declineRevenantKryxosHero";
    private static final String REVKRYXOS_CONTEXT = "revenantKryxosHeroContext_";
    private static final String REVKRYXOS_FIRST_TECH = "revenantKryxosHeroFirstTech_";

    // Purple Revenant Leader Set
    // Revenant of Verydith
    public static Button getRevVerydithAgentButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + PRODUCE_WITH_REVVERYDITH,
                "Use Revenant Verydith Agent",
                FactionEmojis.revenant);
    }

    public static Button getRevVerydithCardsInfoButton(Game game, Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + SELECT_REVVERYDITH_TARGET,
                "Use Revenant Verydith Agent",
                FactionEmojis.revenant);
    }

    @ButtonHandler(SELECT_REVVERYDITH_TARGET)
    public static void offerRevVerydithTargetButtons(
            ButtonInteractionEvent event, Game game, Player componentOwner, String buttonID) {
        if (!componentOwner.hasUnexhaustedLeader(REVVERYDITH)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Koral Vel, the Revenant of Verydith agent, is no longer available.");
            return;
        }

        List<Button> buttons = game.getRealPlayers().stream()
                .filter(target -> target != componentOwner) // Remove to include self
                .map(target -> Buttons.green(
                        componentOwner.factionButtonChecker() + RESOLVE_REVVERYDITH_TARGET + target.getFaction(),
                        target.getFactionNameOrColor(),
                        target.fogSafeEmoji()))
                .toList();

        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no eligible players.");
            return;
        }

        String message = componentOwner.getRepresentation() + ", choose the player who will produce 1 unit with "
                + "Koral Vel, the Revenant of Verydith agent.";
        String prefix = componentOwner.factionButtonChecker() + SELECT_REVVERYDITH_TARGET;
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, componentOwner.getCorrectChannel(), buttons, extraButtons, message, prefix, buttonID)) {
            return;
        }
        List<Button> displayedButtons = new ArrayList<>(buttons);
        displayedButtons.addAll(extraButtons);
        if (displayedButtons.size() > 25) {
            displayedButtons = NewStuffHelper.buttonPagination(buttons, extraButtons, prefix, 25, 0, false);
        }
        MessageHelper.sendMessageToChannelWithButtons(componentOwner.getCorrectChannel(), message, displayedButtons);
    }

    @ButtonHandler(RESOLVE_REVVERYDITH_TARGET)
    public static void chooseRevenantTarget(
            ButtonInteractionEvent event, Game game, Player componentOwner, String buttonID) {

        String targetFaction = buttonID.substring(RESOLVE_REVVERYDITH_TARGET.length());
        Player target = game.getPlayerFromColorOrFaction(targetFaction);

        if (target == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find that player.");
            return;
        }

        if (useRevVerydithAgent(event, game, componentOwner, target)) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler(PRODUCE_WITH_REVVERYDITH)
    public static void offerRevVerydithChoices(ButtonInteractionEvent event, Game game, Player target) {
        if (useRevVerydithAgent(event, game, target, target)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
    }

    private static boolean useRevVerydithAgent(
            ButtonInteractionEvent event, Game game, Player agentOwner, Player target) {
        if (game == null || agentOwner == null || target == null || !agentOwner.hasUnexhaustedLeader(REVVERYDITH)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Koral Vel, the Revenant of Verydith agent, is no longer available.");
            return false;
        }
        List<Button> buttons = getProduceOneUnitInSystemsWithShipsButtons(game, target);

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentation() + " does not have any ships on the game board.");
            return false;
        }

        Leader agent = agentOwner.getLeader(REVVERYDITH).orElse(null);
        if (agent == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Could not find Koral Vel, the Revenant of Verydith agent.");
            return false;
        }
        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        sendRevVerydithSystemButtons(null, game, target, "", buttons);
        return true;
    }

    @ButtonHandler(PAGE_REVVERYDITH_SYSTEMS)
    public static void pageRevVerydithSystemButtons(
            ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String targetFaction =
                buttonID.substring(PAGE_REVVERYDITH_SYSTEMS.length()).split("_page", 2)[0];
        if (!target.getFaction().equals(targetFaction)) {
            return;
        }
        sendRevVerydithSystemButtons(
                event, game, target, buttonID, getProduceOneUnitInSystemsWithShipsButtons(game, target));
    }

    private static void sendRevVerydithSystemButtons(
            ButtonInteractionEvent event, Game game, Player target, String buttonID, List<Button> buttons) {
        String message = target.getRepresentation()
                + ", choose a system containing 1 or more of your ships in which to produce 1 unit due to Koral Vel, the Revenant of Verydith agent.";
        String prefix = target.factionButtonChecker() + PAGE_REVVERYDITH_SYSTEMS + target.getFaction() + "_";
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        if (event != null
                && NewStuffHelper.checkAndHandlePaginationChange(
                        event, target.getCorrectChannel(), buttons, extraButtons, message, prefix, buttonID)) {
            return;
        }
        List<Button> displayedButtons = new ArrayList<>(buttons);
        displayedButtons.addAll(extraButtons);
        if (displayedButtons.size() > 25) {
            displayedButtons = NewStuffHelper.buttonPagination(buttons, extraButtons, prefix, 25, 0, false);
        }
        MessageHelper.sendMessageToChannelWithButtons(target.getCorrectChannel(), message, displayedButtons);
    }

    public static List<Button> getProduceOneUnitInSystemsWithShipsButtons(Game game, Player target) {
        if (game == null || target == null) {
            return List.of();
        }

        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasActualShipsInSystem(target, tile))
                .map(tile -> Buttons.green(
                        target.factionButtonChecker() + "produceOneUnitInTile_" + tile.getPosition()
                                + "_revenantVerydith",
                        tile.getRepresentationForButtons(game, target)))
                .toList();
    }

    // Revenant of Myrr
    public static void offerRevMyrrCommander(Game game, Player player, Tile tile) {
        if (game == null
                || player == null
                || tile == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, REVMYRR)
                || !game.getStoredValue(REVMYRR_USED + player.getFaction()).isEmpty()
                || player.getCurrentProducedUnits().entrySet().stream()
                        .noneMatch(entry -> entry.getValue() > 0
                                && tile.getPosition().equals(getProducedUnitTilePosition(entry.getKey()))
                                && ("ff".equals(getProducedUnitAlias(entry.getKey()))
                                        || "gf".equals(getProducedUnitAlias(entry.getKey()))))) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        String prefix = player.factionButtonChecker() + PLACE_REVMYRR_UNIT;
        buttons.add(Buttons.green(prefix + tile.getPosition() + "|ff|space", "Place 1 Fighter"));
        buttons.add(Buttons.green(prefix + tile.getPosition() + "|gf|space", "Place 1 Infantry in Space"));
        tile.getPlanetUnitHolders().stream()
                .filter(planet -> player.getPlanets().contains(planet.getName()))
                .map(Planet::getName)
                .map(planetName -> Buttons.green(
                        prefix + tile.getPosition() + "|gf|" + planetName,
                        "Place 1 Infantry on " + Helper.getPlanetRepresentation(planetName, game)))
                .forEach(buttons::add);
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may place 1 additional fighter or infantry in the system due to DOMI.N.O, the Revenant of Myrr commander.",
                buttons);
    }

    @ButtonHandler(PLACE_REVMYRR_UNIT)
    public static void placeRevMyrrCommanderUnit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null
                || player == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, REVMYRR)
                || !game.getStoredValue(REVMYRR_USED + player.getFaction()).isEmpty()) {
            return;
        }

        String[] payload = buttonID.substring(PLACE_REVMYRR_UNIT.length()).split("\\|", 3);
        if (payload.length != 3) {
            return;
        }
        Tile tile = game.getTileByPosition(payload[0]);
        String unit = payload[1];
        String location = payload[2];
        if (tile == null
                || !("ff".equals(unit) || "gf".equals(unit))
                || (!"space".equals(location)
                        && (tile.getUnitHolders().get(location) == null
                                || !player.getPlanets().contains(location)))) {
            return;
        }

        game.setStoredValue(REVMYRR_USED + player.getFaction(), "true");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + unit + " " + location);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed 1 " + ("ff".equals(unit) ? "fighter" : "infantry") + " in "
                        + tile.getRepresentationForButtons(game, player)
                        + " due to DOMI.N.O, the Revenant of Myrr commander.");
        ButtonHelper.deleteMessage(event);
    }

    // Revenant of Ruin
    public static Button getRevThronesHeroButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_REVTHRONES, "Use Revenant Thrones Hero", FactionEmojis.revenant);
    }

    @ButtonHandler(USE_REVTHRONES)
    public static void offerRevThronesHeroSystems(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!canUseRevThronesHero(game, player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Lost Throne of Pride, the Revenant Thrones hero, cannot be used right now.");
            return;
        }

        List<Button> buttons = getRevThronesHeroSystemButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You do not have any units on the game board.");
            return;
        }

        PurgeHeroService.purgeHeroPreamble(
                event, player, game, "revenantthroneshero", "Lost Throne of Pride - Fallen King");

        String message = player.getRepresentation()
                + ", choose a system containing 1 or more of your units in which to use PRODUCTION 4 due to Lost Throne of Pride, the Revenant Thrones hero.";
        String prefix = player.factionButtonChecker() + SELECT_REVTHRONES_SYSTEM;
        List<Button> paginatedButtons = NewStuffHelper.buttonPagination(buttons, prefix, 0);
        if (buttons.size() <= 24) {
            paginatedButtons = new ArrayList<>(buttons);
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, paginatedButtons);
    }

    @ButtonHandler(SELECT_REVTHRONES_SYSTEM)
    public static void useRevThronesHero(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!canUseRevThronesHero(game, player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Lost Throne of Pride, the Revenant Thrones hero, cannot be used right now.");
            return;
        }

        List<Button> buttons = getRevThronesHeroSystemButtons(game, player);
        String message = player.getRepresentation()
                + ", choose a system containing 1 or more of your units in which to use PRODUCTION 4 due to Lost Throne of Pride, the Revenant Thrones hero.";
        String prefix = player.factionButtonChecker() + SELECT_REVTHRONES_SYSTEM;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        String position = buttonID.substring(SELECT_REVTHRONES_SYSTEM.length());
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !tile.containsPlayersUnits(player)) {
            return;
        }

        game.setStoredValue(REVTHRONES_PRODUCTION + player.getFaction(), position);
        List<Button> productionButtons =
                Helper.getPlaceUnitButtons(event, player, game, tile, "revenantThronesHero", "place");
        int totalProduction = Helper.getProductionValue(player, game, tile, false);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", choose the units you wish to produce with Lost Throne of Pride, the Revenant Thrones hero."
                        + " Total PRODUCTION in this system: " + totalProduction + ".",
                productionButtons);
        ButtonHelper.deleteMessage(event);
    }

    public static int getRevThronesProduction(Game game, Player player, Tile tile) {
        if (game == null || player == null || tile == null) {
            return 0;
        }
        return tile.getPosition().equals(game.getStoredValue(REVTHRONES_PRODUCTION + player.getFaction())) ? 4 : 0;
    }

    public static void clearPurpleLeaderActionState(Game game) {
        if (game == null) {
            return;
        }
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(REVMYRR_USED) || key.startsWith(REVTHRONES_PRODUCTION))
                .toList()
                .forEach(game::removeStoredValue);
    }

    public static boolean canUseRevThronesHero(Game game, Player player) {
        return game != null
                && player != null
                && player.isActivePlayer()
                && game.getStoredValue(REVTHRONES_PRODUCTION + player.getFaction())
                        .isEmpty();
    }

    private static List<Button> getRevThronesHeroSystemButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.containsPlayersUnits(player))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + SELECT_REVTHRONES_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static String getProducedUnitAlias(String producedUnitKey) {
        int lastSeparator = producedUnitKey.lastIndexOf('_');
        if (lastSeparator < 0) {
            return null;
        }
        int middleSeparator = producedUnitKey.lastIndexOf('_', lastSeparator - 1);
        return middleSeparator < 0 ? null : producedUnitKey.substring(0, middleSeparator);
    }

    private static String getProducedUnitTilePosition(String producedUnitKey) {
        int lastSeparator = producedUnitKey.lastIndexOf('_');
        if (lastSeparator < 0) {
            return null;
        }
        int middleSeparator = producedUnitKey.lastIndexOf('_', lastSeparator - 1);
        return middleSeparator < 0 ? null : producedUnitKey.substring(middleSeparator + 1, lastSeparator);
    }

    // Red Revenant Leader Set
    public static void addRedLeaderCardsInfoButtons(List<Button> buttons, Player player) {
        if (buttons != null && player != null && player.hasUnexhaustedLeader(REVXYTHERIS)) {
            buttons.add(getRevXytherisCardsInfoButton(player));
        }
    }

    // Revenant of Xytheris
    public static void openRevXytherisAgentWindow(Game game, Player activePlayer) {
        if (game == null
                || activePlayer == null
                || game.getRealPlayers().stream().noneMatch(player -> player.hasUnexhaustedLeader(REVXYTHERIS))) {
            return;
        }

        game.setStoredValue(REVXYTHERIS_WINDOW, activePlayer.getFaction() + "|" + game.getActiveSystem());
        if (!activePlayer.hasUnexhaustedLeader(REVXYTHERIS)) {
            return;
        }

        List<Button> buttons = List.of(
                Buttons.green(
                        activePlayer.factionButtonChecker() + USE_REVXYTHERIS + activePlayer.getFaction(),
                        "Use Revenant Xytheris Agent",
                        FactionEmojis.revenant),
                Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                activePlayer.getCorrectChannel(),
                activePlayer.getRepresentation()
                        + ", you may exhaust Zexan Myrix, the Revenant of Xytheris agent, to apply +1 to your combat rolls during this tactical action.",
                buttons);
    }

    public static Button getRevXytherisCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + SELECT_REVXYTHERIS_TARGET,
                "Use Revenant Xytheris Agent",
                FactionEmojis.revenant);
    }

    @ButtonHandler(SELECT_REVXYTHERIS_TARGET)
    public static void offerRevXytherisTargetButtons(ButtonInteractionEvent event, Game game, Player agentOwner) {
        if (!canUseRevXytherisAgent(game, agentOwner)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Zexan Myrix, the Revenant of Xytheris agent, cannot be used right now.");
            return;
        }

        List<Button> buttons = game.getRealPlayersExcludingThis(agentOwner).stream()
                .map(target -> Buttons.green(
                        agentOwner.factionButtonChecker() + USE_REVXYTHERIS + target.getFaction(),
                        target.getFactionNameOrColor(),
                        target.fogSafeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no other players to choose.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                agentOwner.getCardsInfoThread(),
                agentOwner.getRepresentationUnfogged()
                        + ", choose a player to receive +1 to their combat rolls from Zexan Myrix, the Revenant of Xytheris agent, during this tactical action.",
                buttons);
    }

    @ButtonHandler(USE_REVXYTHERIS)
    public static void useRevXytherisAgent(
            ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        if (!canUseRevXytherisAgent(game, agentOwner)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Zexan Myrix, the Revenant of Xytheris agent, cannot be used right now.");
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(USE_REVXYTHERIS.length()));
        Leader agent = agentOwner.getLeader(REVXYTHERIS).orElse(null);
        if (target == null || agent == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find the selected player or agent.");
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        game.setStoredValue(REVXYTHERIS_TARGET + agentOwner.getFaction(), target.getFaction());
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentation()
                        + " will apply +1 to their combat rolls during this tactical action due to Zexan Myrix, the Revenant of Xytheris agent.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static void addRevXytherisAgentModifier(
            List<NamedCombatModifierModel> modifiers, Game game, Player player, CombatRollType rollType) {
        if (modifiers == null || game == null || player == null || rollType != CombatRollType.combatround) {
            return;
        }

        long modifierCount = game.getStoredValueMap().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(REVXYTHERIS_TARGET))
                .filter(entry -> player.getFaction().equals(entry.getValue()))
                .count();
        var modifier = Mapper.getCombatModifiers().get("plus1_1tacticalaction_all");
        for (int i = 0; i < modifierCount && modifier != null; i++) {
            modifiers.add(
                    new NamedCombatModifierModel(modifier, "+1 from Zexan Myrix, the Revenant of Xytheris agent"));
        }
    }

    private static boolean canUseRevXytherisAgent(Game game, Player agentOwner) {
        if (game == null || agentOwner == null || !agentOwner.hasUnexhaustedLeader(REVXYTHERIS)) {
            return false;
        }
        Player activePlayer = game.getActivePlayer();
        return activePlayer != null
                && (activePlayer.getFaction() + "|" + game.getActiveSystem())
                        .equals(game.getStoredValue(REVXYTHERIS_WINDOW));
    }

    // Revenant of Ponthous
    public static void offerRevPonthousCommander(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        if (event == null
                || game == null
                || player == null
                || tile == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, REVPONTHOUS)) {
            return;
        }

        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null
                || !Constants.SPACE.equals(combat.unitHolderName())
                || !tile.getPosition().equals(combat.tilePosition())
                || !combat.factions().contains(player.getFaction())) {
            return;
        }
        Player opponent = combat.factions().stream()
                .filter(faction -> !faction.equals(player.getFaction()))
                .map(game::getPlayerFromColorOrFaction)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (opponent == null) {
            return;
        }

        String context = tile.getPosition() + "|" + opponent.getFaction() + "|" + combat.round();
        String offeredKey = REVPONTHOUS_OFFERED + player.getFaction();
        String usedKey = REVPONTHOUS_USED + player.getFaction();
        if (context.equals(game.getStoredValue(offeredKey)) || context.equals(game.getStoredValue(usedKey))) {
            return;
        }

        game.setStoredValue(offeredKey, context);
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + USE_REVPONTHOUS + context,
                        "Use Revenant Ponthous Commander",
                        FactionEmojis.revenant),
                Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", you may produce 1 hit against your opponent due to Melloh Terras, the Revenant of Ponthous commander.",
                buttons);
    }

    @ButtonHandler(USE_REVPONTHOUS)
    public static void useRevPonthousCommander(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !game.playerHasLeaderUnlockedOrAlliance(player, REVPONTHOUS)) {
            return;
        }

        String context = buttonID.substring(USE_REVPONTHOUS.length());
        String[] payload = context.split("\\|", 3);
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        Player opponent = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (tile == null
                || opponent == null
                || combat == null
                || !Constants.SPACE.equals(combat.unitHolderName())
                || !tile.getPosition().equals(combat.tilePosition())
                || !Integer.toString(combat.round()).equals(payload[2])
                || !combat.factions().contains(player.getFaction())
                || !combat.factions().contains(opponent.getFaction())
                || context.equals(game.getStoredValue(REVPONTHOUS_USED + player.getFaction()))) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That combat-round window is no longer valid.");
            return;
        }

        game.setStoredValue(REVPONTHOUS_USED + player.getFaction(), context);
        game.removeStoredValue(REVPONTHOUS_OFFERED + player.getFaction());
        List<Button> buttons = List.of(
                Buttons.green(
                        opponent.factionButtonChecker() + "autoAssignSpaceHits_" + tile.getPosition() + "_1",
                        "Auto-assign 1 Hit"),
                Buttons.red(
                        opponent.factionButtonChecker() + "getDamageButtons_" + tile.getPosition()
                                + "deleteThis_spacecombat",
                        "Manually Assign 1 Hit"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " produced 1 hit against " + opponent.getRepresentationNoPing()
                        + " due to Melloh Terras, the Revenant of Ponthous commander.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    // Revenant of Kryxos
    public static void addRevKryxosHeroButton(
            List<Button> buttons, Game game, Player player, Player opponent, Tile tile, boolean isSpaceCombat) {
        if (buttons == null
                || game == null
                || player == null
                || opponent == null
                || tile == null
                || !isSpaceCombat
                || !player.hasLeaderUnlocked(REVKRYXOS)
                || !game.getStoredValue(REVKRYXOS_CONTEXT + player.getFaction()).isEmpty()) {
            return;
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + USE_REVKRYXOS + tile.getPosition() + "|" + opponent.getFaction(),
                "Use Revenant Kryxos Hero",
                FactionEmojis.revenant));
    }

    @ButtonHandler(USE_REVKRYXOS)
    public static void useRevKryxosHero(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_REVKRYXOS.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Player opponent = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        if (player == opponent || tile == null || opponent == null || !player.hasLeaderUnlocked(REVKRYXOS)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Pryxos Xiv, the Revenant of Kryxos hero, cannot be used.");
            return;
        }

        List<TechnologyModel> opponentTechs = getRevKryxosTechs(game, player, opponent, tile);
        List<TechnologyModel> playerTechs = new ArrayList<>(getRevKryxosTechs(game, player, player, tile));
        playerTechs.removeIf(firstTech -> opponentTechs.stream()
                .noneMatch(secondTech -> !secondTech.getAlias().equals(firstTech.getAlias())));
        if (playerTechs.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You do not have two eligible technologies matching units in this combat.");
            return;
        }

        game.setStoredValue(REVKRYXOS_CONTEXT + player.getFaction(), tile.getPosition() + "|" + opponent.getFaction());
        game.removeStoredValue(REVKRYXOS_FIRST_TECH + player.getFaction());
        sendRevKryxosTechButtons(
                event,
                player,
                playerTechs,
                1,
                player.getRepresentation()
                        + ", choose a technology matching a unit you control in the active system to research with Pryxos Xiv, the Revenant of Kryxos hero.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_REVKRYXOS_TECH)
    public static void researchRevKryxosHeroTech(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] selection = buttonID.substring(SELECT_REVKRYXOS_TECH.length()).split("\\|", 2);
        String[] context =
                game.getStoredValue(REVKRYXOS_CONTEXT + player.getFaction()).split("\\|", 2);
        int step;
        try {
            step = selection.length == 2 ? Integer.parseInt(selection[0]) : 0;
        } catch (NumberFormatException e) {
            step = 0;
        }
        Tile tile = context.length == 2 ? game.getTileByPosition(context[0]) : null;
        Player opponent = context.length == 2 ? game.getPlayerFromColorOrFaction(context[1]) : null;
        String techId = selection.length == 2 ? selection[1] : "";
        Player matchingUnitOwner = step == 1 ? player : opponent;
        List<TechnologyModel> availableTechs = (step == 1 || step == 2) && opponent != null && tile != null
                ? getRevKryxosTechs(game, player, matchingUnitOwner, tile)
                : List.of();
        String menuMessage = step == 1
                ? player.getRepresentation()
                        + ", choose a technology matching a unit you control in the active system to research with Pryxos Xiv, the Revenant of Kryxos hero."
                : player.getRepresentation()
                        + ", choose a technology matching a unit your opponent controls in the active system to research with Pryxos Xiv, the Revenant of Kryxos hero.";
        String paginationPrefix = player.factionButtonChecker() + SELECT_REVKRYXOS_TECH + step + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                getRevKryxosTechButtons(player, availableTechs, step),
                List.of(Buttons.red("deleteButtons", "Decline")),
                menuMessage,
                paginationPrefix,
                buttonID)) {
            return;
        }
        boolean eligible = (step == 1 || step == 2)
                && opponent != null
                && tile != null
                && player.hasLeaderUnlocked(REVKRYXOS)
                && availableTechs.stream().anyMatch(tech -> tech.getAlias().equals(techId));
        if (!eligible
                || (step == 1
                        && !game.getStoredValue(REVKRYXOS_FIRST_TECH + player.getFaction())
                                .isEmpty())
                || (step == 2
                        && game.getStoredValue(REVKRYXOS_FIRST_TECH + player.getFaction())
                                .isEmpty())) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That technology is no longer eligible.");
            return;
        }

        PlayerTechService.getTech(game, player, event, "getTech_" + techId + "__noPay");
        if (step == 1) {
            game.setStoredValue(REVKRYXOS_FIRST_TECH + player.getFaction(), techId);
            List<TechnologyModel> techs = getRevKryxosTechs(game, player, opponent, tile);
            if (techs.isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), "No eligible technology remains for the second research.");
                return;
            }
            ButtonHelper.deleteMessage(event);
            sendRevKryxosTechButtons(
                    event,
                    player,
                    techs,
                    2,
                    player.getRepresentation()
                            + ", choose a technology matching a unit your opponent controls in the active system to research with Pryxos Xiv, the Revenant of Kryxos hero.");
            return;
        }

        Leader hero = player.getLeader(REVKRYXOS).orElse(null);
        boolean purged = hero != null && PlayHeroService.removeLeader(game, player, hero);
        game.removeStoredValue(REVKRYXOS_CONTEXT + player.getFaction());
        game.removeStoredValue(REVKRYXOS_FIRST_TECH + player.getFaction());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                purged
                        ? "Pryxos Xiv, the Revenant of Kryxos hero, was purged."
                        : "Pryxos Xiv, the Revenant of Kryxos hero, could not be purged.");
    }

    @ButtonHandler(DECLINE_REVKRYXOS)
    public static void declineRevKryxosHero(ButtonInteractionEvent event, Game game, Player player) {
        game.removeStoredValue(REVKRYXOS_CONTEXT + player.getFaction());
        game.removeStoredValue(REVKRYXOS_FIRST_TECH + player.getFaction());
        ButtonHelper.deleteMessage(event);
    }

    public static void clearRedLeaderTacticalWindow(Game game) {
        if (game == null) {
            return;
        }
        game.removeStoredValue(REVXYTHERIS_WINDOW);
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(REVPONTHOUS_OFFERED)
                        || key.startsWith(REVPONTHOUS_USED)
                        || key.startsWith(REVKRYXOS_CONTEXT)
                        || key.startsWith(REVKRYXOS_FIRST_TECH))
                .toList()
                .forEach(game::removeStoredValue);
    }

    public static void clearRedLeaderTacticalState(Game game) {
        if (game == null) {
            return;
        }
        clearRedLeaderTacticalWindow(game);
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(REVXYTHERIS_TARGET))
                .toList()
                .forEach(game::removeStoredValue);
    }

    private static List<TechnologyModel> getRevKryxosTechs(Game game, Player researcher, Player unitOwner, Tile tile) {
        if (game == null || researcher == null || unitOwner == null || tile == null) {
            return List.of();
        }
        UnitHolder space = tile.getSpaceUnitHolder();
        Set<String> unitTypes = space.getUnitKeysForPlayer(unitOwner).stream()
                .map(unitOwner::getUnitFromUnitKey)
                .filter(Objects::nonNull)
                .map(UnitModel::getBaseType)
                .collect(java.util.stream.Collectors.toSet());
        return ListTechService.getAllTechOfAType(
                        game, TechnologyModel.TechnologyType.UNITUPGRADE.toString(), researcher, false, true)
                .stream()
                .filter(tech -> {
                    UnitModel upgradedUnit = Mapper.getUnitModelByTechUpgrade(tech.getAlias());
                    return upgradedUnit != null && unitTypes.contains(upgradedUnit.getBaseType());
                })
                .toList();
    }

    private static List<Button> getRevKryxosTechButtons(Player player, List<TechnologyModel> technologies, int step) {
        return technologies.stream()
                .map(tech -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_REVKRYXOS_TECH + step + "|" + tech.getAlias(),
                        tech.getName(),
                        tech.getCondensedReqsEmojis(true)))
                .toList();
    }

    private static void sendRevKryxosTechButtons(
            ButtonInteractionEvent event, Player player, List<TechnologyModel> technologies, int step, String message) {
        List<Button> buttons = getRevKryxosTechButtons(player, technologies, step);
        List<Button> extraButtons = List.of(Buttons.red(player.factionButtonChecker() + DECLINE_REVKRYXOS, "Decline"));
        String prefix = player.factionButtonChecker() + SELECT_REVKRYXOS_TECH + step + "|";
        List<Button> displayedButtons = new ArrayList<>(buttons);
        displayedButtons.addAll(extraButtons);
        if (displayedButtons.size() > 25) {
            displayedButtons = NewStuffHelper.buttonPagination(buttons, extraButtons, prefix, 25, 0, false);
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, displayedButtons);
    }

    // Base Revenant Leader Set
    // Agent
    public static void offerRevenantAgentButtons(Player player, String tech) {
        if (player == null || !player.hasUnexhaustedLeader(REVBASE)) {
            return;
        }
        TechnologyModel techM = Mapper.getTech(tech);
        if (techM == null) {
            return;
        }
        String technologyRepresentation = techM.getNameRepresentation();

        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + USE_REVBASE + techM.getID(),
                        "Ready " + technologyRepresentation),
                Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may exhaust Xythis the Whispering Mask, the Revenant agent, to ready "
                        + technologyRepresentation + ".",
                buttons);
    }

    public static Button getRevenantAgentButton(Player player) {
        return Buttons.green(
                player.factionButtonChecker() + SELECT_REVBASE_TARGET, "Use Revenant Agent", FactionEmojis.revenant);
    }

    @ButtonHandler(SELECT_REVBASE_TARGET)
    public static void offerRevBaseTargetButtons(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasUnexhaustedLeader(REVBASE)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Xythis the Whispering Mask, the Revenant agent, is no longer available.");
            return;
        }

        List<Button> targets = game.getRealPlayersExcludingThis(player).stream()
                .filter(target ->
                        target.getExhaustedTechs().stream().anyMatch(techId -> Mapper.getTech(techId) != null))
                .map(target -> Buttons.green(
                        player.factionButtonChecker() + USE_REVBASE_TARGET + target.getFaction(),
                        target.getFactionNameOrColor(),
                        target.fogSafeEmoji()))
                .toList();
        if (targets.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "No other player has an exhausted technology.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", choose a player on whom to use Xythis the Whispering Mask, the Revenant agent.",
                targets);
    }

    @ButtonHandler(USE_REVBASE_TARGET)
    public static void offerRevBaseTechButtons(
            ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        if (game == null || agentOwner == null || !agentOwner.hasUnexhaustedLeader(REVBASE)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Xythis the Whispering Mask, the Revenant agent, is no longer available.");
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(USE_REVBASE_TARGET.length()));
        if (target == null || target == agentOwner) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find that player.");
            return;
        }

        List<Button> techButtons = target.getExhaustedTechs().stream()
                .map(Mapper::getTech)
                .filter(tech -> tech != null)
                .map(tech -> Buttons.green(
                        target.factionButtonChecker() + USE_REVBASE + agentOwner.getFaction() + "~" + tech.getAlias(),
                        "Ready " + tech.getNameRepresentation()))
                .toList();
        if (techButtons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That player no longer has an exhausted technology.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged()
                        + ", choose the technology that Xythis the Whispering Mask, the Revenant agent, should ready.",
                techButtons);
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Sent technology choices to " + target.getRepresentationUnfoggedNoPing() + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(USE_REVBASE)
    public static void resolveRevenantAgent(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String[] payload = buttonID.substring(USE_REVBASE.length()).split("~", 2);
        Player agentOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : player;
        String techId = payload.length == 2 ? payload[1] : payload[0];
        if (agentOwner == null || !agentOwner.hasUnexhaustedLeader(REVBASE)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Xythis the Whispering Mask, the Revenant agent, is no longer available.");
            return;
        }

        TechnologyModel techM = Mapper.getTech(techId);
        if (techM == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unable to resolve the technology ID.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Leader agent = agentOwner.getLeaderByID(REVBASE).orElse(null);
        if (agent == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Could not find Xythis the Whispering Mask, the Revenant agent.");
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        player.refreshTech(techId);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " readied " + techM.getNameRepresentation()
                        + " using Xythis the Whispering Mask, the Revenant agent.");

        ButtonHelper.deleteMessage(event);
    }

    // Hero
    public static void offerRevenantHeroChoices(Game game, Player player) {
        if (game == null || player == null || !player.hasLeaderUnlocked(REVENANT_HERO)) {
            return;
        }

        String choicesKey = REVENANT_HERO_CHOICES + player.getFaction();
        List<String> drawnHeroes =
                new ArrayList<>(List.of(game.getStoredValue(choicesKey).split(",")));
        drawnHeroes.removeIf(String::isBlank);

        if (drawnHeroes.isEmpty()) {
            List<String> unavailableHeroes = new ArrayList<>(
                    List.of(game.getStoredValue(REVENANT_UNAVAILABLE_HEROES).split(",")));
            unavailableHeroes.removeIf(String::isBlank);

            List<String> heroPool = new ArrayList<>();

            for (FactionModel faction : Mapper.getFactionsValues()) {
                boolean sourceEnabled = faction.getSource().isOfficial()
                        || (game.isDiscordantStarsMode() && faction.getSource().isDs())
                        || (game.isBlueReverieMode() && faction.getSource().isBr())
                        || game.getRealPlayers().stream()
                                .map(Player::getFactionModel)
                                .filter(model -> model != null)
                                .anyMatch(model -> model.getSource() == faction.getSource());

                if (!sourceEnabled || game.getFactions().contains(faction.getAlias())) {
                    continue;
                }

                for (String leaderId : faction.getLeaders()) {
                    LeaderModel hero = Mapper.getLeader(leaderId);
                    if (hero == null
                            || !"hero".equalsIgnoreCase(hero.getType())
                            || REVENANT_HERO.equals(leaderId)
                            || Constants.CALL_OF_THE_HAUNTED_LEADERS.contains(leaderId)
                            || "unknown".equalsIgnoreCase(hero.getAbilityText())
                            || unavailableHeroes.contains(leaderId)
                            || Helper.getPlayerFromLeader(game, leaderId) != null
                            || heroPool.contains(leaderId)) {
                        continue;
                    }

                    heroPool.add(leaderId);
                }
            }

            if (heroPool.size() < 3) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation()
                                + ", there are not enough unused heroes to resolve The Nameless Host, the Revenant hero.");
                return;
            }

            Collections.shuffle(heroPool);
            drawnHeroes = new ArrayList<>(heroPool.subList(0, 3));

            game.setStoredValue(choicesKey, String.join(",", drawnHeroes));

            // Reserve all three immediately so another copy of this hero cannot
            // draw one while this choice is pending.
            for (String heroId : drawnHeroes) {
                if (!unavailableHeroes.contains(heroId)) {
                    unavailableHeroes.add(heroId);
                }
            }
            game.setStoredValue(REVENANT_UNAVAILABLE_HEROES, String.join(",", unavailableHeroes));
        }

        List<MessageEmbed> embeds = drawnHeroes.stream()
                .map(Mapper::getLeader)
                .filter(hero -> hero != null)
                .map(LeaderModel::getRepresentationEmbed)
                .toList();

        List<Button> buttons = drawnHeroes.stream()
                .map(Mapper::getLeader)
                .filter(hero -> hero != null)
                .map(hero -> Buttons.green(
                        player.factionButtonChecker() + SELECT_REVENANT_HERO + hero.getAlias(),
                        "Attach " + hero.getName()))
                .toList();

        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", choose 1 of these heroes to attach to The Nameless Host, the Revenant hero. The other 2 will be purged.",
                embeds,
                buttons);
    }

    @ButtonHandler(SELECT_REVENANT_HERO)
    public static void selectRevenantHero(ButtonInteractionEvent event, Game game, Player player, String buttonID) {

        if (game == null || player == null || !player.hasLeaderUnlocked(REVENANT_HERO)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "The Nameless Host, the Revenant hero, is no longer available.");
            return;
        }

        String selectedHeroId = buttonID.substring(SELECT_REVENANT_HERO.length());
        String choicesKey = REVENANT_HERO_CHOICES + player.getFaction();

        List<String> drawnHeroes =
                new ArrayList<>(List.of(game.getStoredValue(choicesKey).split(",")));
        drawnHeroes.removeIf(String::isBlank);

        LeaderModel selectedHero = Mapper.getLeader(selectedHeroId);
        if (selectedHero == null || !drawnHeroes.contains(selectedHeroId)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That hero was not one of the heroes drawn.");
            return;
        }

        List<String> purgedHeroes = drawnHeroes.stream()
                .filter(heroId -> !heroId.equals(selectedHeroId))
                .toList();

        if (!player.removeLeader(REVENANT_HERO)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Could not replace The Nameless Host, the Revenant hero.");
            return;
        }

        player.addLeader(selectedHeroId);
        Leader attachedHero = player.unsafeGetLeader(selectedHeroId);
        if (attachedHero != null) {
            attachedHero.setLocked(false);
        }

        game.removeStoredValue(choicesKey);
        ButtonHelper.deleteMessage(event);

        String purgedNames = purgedHeroes.stream()
                .map(Mapper::getLeader)
                .filter(hero -> hero != null)
                .map(LeaderModel::getNameRepresentation)
                .collect(java.util.stream.Collectors.joining(" and "));

        MessageHelper.sendMessageToChannelWithEmbed(
                event.getMessageChannel(),
                player.getRepresentation() + " attached "
                        + selectedHero.getNameRepresentation()
                        + " to The Nameless Host, the Revenant hero, and purged "
                        + purgedNames + ".",
                selectedHero.getRepresentationEmbed());

        // Each unchosen hero was purged as part of the effect.
        DSHelperBreakthroughs.doLanefirBtCheck(game, player);
        OblivionUnitHandler.doOblivionMechCheck(game, player);
        for (int i = 0; i < purgedHeroes.size(); i++) {
            RevenantUnitsHandler.doRevenantMechCheck(game, player);
            RevenantTechHandler.doLazarusPodsLeaderCheck(game);
        }
    }

    // Green Revenant Leader Set
    // Revenant of Arcanum
    public static void addRevArcanumAgentButtons(List<Button> buttons, Game game, Player player, Planet planet) {
        if (buttons.isEmpty() || game == null || player == null || planet == null) {
            return;
        }

        boolean agentIsReady = game.getRealPlayers().stream().anyMatch(p -> p.hasUnexhaustedLeader(REVARCAGENT));
        if (!agentIsReady) {
            return;
        }

        String key = REVARCAGENT_EXPLORE_OPTIONS + player.getFaction();
        String options = game.getStoredValue(key);
        if (!List.of(options.split(",")).contains(planet.getName())) {
            game.setStoredValue(key, options.isEmpty() ? planet.getName() : options + "," + planet.getName());
        }

        if (player.hasUnexhaustedLeader(REVARCAGENT)
                && buttons.size() < 25
                && buttons.stream().noneMatch(button -> button.getCustomId().endsWith(USE_REVARCAGENT))) {
            buttons.add(Buttons.green(player.factionButtonChecker() + USE_REVARCAGENT, "Use Revenant Arcanum Agent"));
        }
    }

    @ButtonHandler(USE_REVARCAGENT)
    public static void useRevArcanumAgent(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Leader agent = player.getLeader(REVARCAGENT).orElse(null);
        if (agent == null || !player.hasUnexhaustedLeader(REVARCAGENT)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Runebearer Lothos, the Revenant of Arcanum agent, is no longer available.");
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        String targetFaction = buttonID.substring(USE_REVARCAGENT.length());
        if ("_other".equals(targetFaction)) {
            List<Button> targetButtons = new ArrayList<>();
            for (Player target : game.getRealPlayers()) {
                if (target == player
                        || game.getStoredValue(REVARCAGENT_EXPLORE_OPTIONS + target.getFaction())
                                .isEmpty()) {
                    continue;
                }
                targetButtons.add(Buttons.green(
                        player.factionButtonChecker() + USE_REVARCAGENT + "_" + target.getFaction(),
                        target.getFactionNameOrColor()));
            }
            if (targetButtons.isEmpty()) {
                MessageHelper.sendEphemeralMessageToEventChannel(
                        event, "No other player currently has an exploration prompt.");
                return;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + ", please choose a player on whom to use Runebearer Lothos, the Revenant of Arcanum agent.",
                    targetButtons);
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        Player explorer = targetFaction.isEmpty()
                ? player
                : targetFaction.startsWith("_") ? game.getPlayerFromColorOrFaction(targetFaction.substring(1)) : null;
        if (explorer == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That player is no longer eligible.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        List<String> eligiblePlanets = new ArrayList<>();
        String options = game.getStoredValue(REVARCAGENT_EXPLORE_OPTIONS + explorer.getFaction());
        for (String planetName : options.split(",")) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null
                    || !explorer.getPlanetsAllianceMode().contains(planetName)
                    || planet.getPlanetTypes().stream().noneMatch(EXPLORATION_TRAITS::contains)) {
                continue;
            }

            eligiblePlanets.add(planetName);
            buttons.add(Buttons.green(
                    explorer.factionButtonChecker() + REVARCAGENT_PLANET + planetName,
                    "Explore " + planet.getRepresentation(game)));

            if (buttons.size() == 25) {
                break;
            }
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That player has no eligible planets to explore.");
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        game.removeStoredValue(REVARCAGENT_EXPLORE_OPTIONS + explorer.getFaction());
        game.setStoredValue(REVARCAGENT_WINDOW + explorer.getFaction(), String.join(",", eligiblePlanets));
        ExhaustLeaderService.exhaustLeader(game, player, agent);
        if (explorer != player) {
            ActionCardHelper.drawActionCards(player, 1);
        }
        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                explorer.getRepresentation()
                        + ", please choose a planet to explore with Runebearer Lothos, the Revenant of Arcanum agent.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(REVARCAGENT_PLANET)
    public static void selectRevArcanumAgentPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(REVARCAGENT_PLANET.length());
        String key = REVARCAGENT_WINDOW + player.getFaction();
        if (!List.of(game.getStoredValue(key).split(",")).contains(planetName)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null || !player.getPlanetsAllianceMode().contains(planetName)) {
            game.removeStoredValue(key);
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.removeStoredValue(key);
        List<Button> traitButtons = List.of(
                Buttons.gray(
                        player.factionButtonChecker() + "movedNExplored_filler_" + planetName + "_cultural",
                        "Explore as Cultural",
                        ExploreEmojis.Cultural),
                Buttons.gray(
                        player.factionButtonChecker() + "movedNExplored_filler_" + planetName + "_hazardous",
                        "Explore as Hazardous",
                        ExploreEmojis.Hazardous),
                Buttons.gray(
                        player.factionButtonChecker() + "movedNExplored_filler_" + planetName + "_industrial",
                        "Explore as Industrial",
                        ExploreEmojis.Industrial));
        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                player.getRepresentation() + ", please choose how to explore " + planet.getRepresentation(game)
                        + " with Runebearer Lothos, the Revenant of Arcanum agent.",
                traitButtons);
        ButtonHelper.deleteMessage(event);
    }

    // Revenant of Oblivion
    public static void addArlirMirroredButton(
            List<Button> buttons, Game game, Player player, String planetName, String cardId, String drawColor) {
        if (game == null
                || player == null
                || planetName == null
                || cardId == null
                || drawColor == null
                || Constants.FRONTIER.equals(drawColor)) {
            return;
        }

        if (!game.playerHasLeaderUnlockedOrAlliance(player, REVOBLCOMMANDER)) {
            return;
        }

        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null
                || !player.getPlanetsAllianceMode().contains(planetName)
                || !planet.hasStructures(player)
                || buttons.size() >= 25) {
            return;
        }

        String buttonId =
                player.factionButtonChecker() + USE_ARLIR_MIRRORED + cardId + "|" + planetName + "|" + drawColor;

        buttons.add(Buttons.green(buttonId, "Use Revenant Oblivion Commander"));
    }

    @ButtonHandler(USE_ARLIR_MIRRORED)
    public static void useArlirMirrored(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_ARLIR_MIRRORED.length()).split("\\|", 3);
        if (payload.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String originalCardId = payload[0];
        String planetName = payload[1];
        String drawColor = payload[2];

        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (Constants.FRONTIER.equals(drawColor)
                || !game.playerHasLeaderUnlockedOrAlliance(player, REVOBLCOMMANDER)
                || planet == null
                || !player.getPlanetsAllianceMode().contains(planetName)
                || !planet.hasStructures(player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Arlir Mirrored, the Revenant of Oblivion commander, is no longer available.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String secondCardId = game.drawExplore(drawColor);
        if (secondCardId == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no more exploration cards to draw.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (secondCardId.equalsIgnoreCase(originalCardId)) {
            secondCardId = game.drawExplore(drawColor);
            if (secondCardId == null) {
                MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no more exploration cards to draw.");
                ButtonHelper.deleteMessage(event);
                return;
            }
        }

        ExploreModel originalCard = Mapper.getExplore(originalCardId);
        ExploreModel secondCard = Mapper.getExplore(secondCardId);
        if (originalCard == null || secondCard == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find one of the exploration cards.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = List.of(
                Buttons.green(
                        "resolve_explore_" + originalCardId + "_" + planetName, "Resolve " + originalCard.getName()),
                Buttons.green("resolve_explore_" + secondCardId + "_" + planetName, "Resolve " + secondCard.getName()));

        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please choose 1 exploration card to resolve with Arlir Mirrored, the Revenant of Oblivion commander.",
                List.of(originalCard.getRepresentationEmbed(), secondCard.getRepresentationEmbed()),
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    // Revenant of Kairn
    public static void startRevKairnHero(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        game.setStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction(), "true");
        game.setStoredValue(REV_KAIRN_HERO_DECKS + player.getFaction(), "");
        game.setStoredValue(REV_KAIRN_HERO_TRAITS + player.getFaction(), "");
        showRevKairnHeroDecks(event, game, player);
    }

    private static void showRevKairnHeroDecks(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!"true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))) {
            return;
        }

        List<String> selectedDecks = List.of(
                game.getStoredValue(REV_KAIRN_HERO_DECKS + player.getFaction()).split(","));
        List<Button> buttons = new ArrayList<>();
        for (String trait : EXPLORE_DECK_TYPES) {
            if (selectedDecks.contains(trait) || game.getExploreDiscard(trait).isEmpty()) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + CHOOSE_EXP_DECK + trait,
                    "View " + StringUtils.capitalize(trait) + " Discard"));
        }
        buttons.add(
                Buttons.red(player.factionButtonChecker() + FINISH_REV_KAIRN_DISCARDS, "Continue to Planet Explores"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please choose up to 1 card from each exploration discard pile to shuffle into its deck.",
                buttons);
    }

    @ButtonHandler(CHOOSE_EXP_DECK)
    public static void showRevenantExploreDiscard(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String trait = buttonID.substring(CHOOSE_EXP_DECK.length());

        if (!"true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))
                || !EXPLORE_DECK_TYPES.contains(trait)
                || List.of(game.getStoredValue(REV_KAIRN_HERO_DECKS + player.getFaction())
                                .split(","))
                        .contains(trait)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String buttonPrefix = player.factionButtonChecker() + CHOOSE_EXP_CARD + trait + "|";
        List<Button> buttons = getRevKairnExploreDiscardButtons(game, player, trait, buttonPrefix);
        List<Button> extraButtons =
                List.of(Buttons.red(player.factionButtonChecker() + BACK_TO_REV_KAIRN_DECKS, "Back to Discard Piles"));

        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That explore discard pile is empty.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> displayedButtons = buttons.size() <= 24
                ? new ArrayList<>(buttons)
                : NewStuffHelper.buttonPagination(buttons, extraButtons, buttonPrefix, 25, 0, false);
        if (buttons.size() <= 24) {
            displayedButtons.addAll(extraButtons);
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose an exploration card from the "
                        + StringUtils.capitalize(trait) + " discard pile.",
                displayedButtons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(BACK_TO_REV_KAIRN_DECKS)
    public static void backToRevKairnDecks(ButtonInteractionEvent event, Game game, Player player) {
        if ("true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))) {
            showRevKairnHeroDecks(event, game, player);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CHOOSE_EXP_CARD)
    public static void chooseRevenantExploreCard(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(CHOOSE_EXP_CARD.length());
        int traitEnd = payload.indexOf('|');
        if (traitEnd < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String trait = payload.substring(0, traitEnd);
        if (!"true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))
                || !EXPLORE_DECK_TYPES.contains(trait)
                || List.of(game.getStoredValue(REV_KAIRN_HERO_DECKS + player.getFaction())
                                .split(","))
                        .contains(trait)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String buttonPrefix = player.factionButtonChecker() + CHOOSE_EXP_CARD + trait + "|";
        List<Button> buttons = getRevKairnExploreDiscardButtons(game, player, trait, buttonPrefix);
        List<Button> extraButtons =
                List.of(Buttons.red(player.factionButtonChecker() + BACK_TO_REV_KAIRN_DECKS, "Back to Discard Piles"));
        String message = player.getRepresentation() + ", please choose an exploration card from the "
                + StringUtils.capitalize(trait) + " discard pile.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, extraButtons, message, buttonPrefix, buttonID)) {
            return;
        }

        String exploreId = payload.substring(traitEnd + 1);
        if (!game.getExploreDiscard(trait).contains(exploreId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ExploreModel explore = Mapper.getExplore(exploreId);
        if (explore == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.addExplore(exploreId);
        String selectedDecks = game.getStoredValue(REV_KAIRN_HERO_DECKS + player.getFaction());
        game.setStoredValue(
                REV_KAIRN_HERO_DECKS + player.getFaction(),
                selectedDecks.isEmpty() ? trait : selectedDecks + "," + trait);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " shuffled _" + explore.getName() + "_ into the "
                        + StringUtils.capitalize(trait) + " exploration deck.");
        showRevKairnHeroDecks(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(FINISH_REV_KAIRN_DISCARDS)
    public static void finishRevKairnDiscards(ButtonInteractionEvent event, Game game, Player player) {
        if (!"true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        showRevKairnHeroTraits(event, game, player);
    }

    private static void showRevKairnHeroTraits(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!"true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))) {
            return;
        }

        List<String> exploredTraits = List.of(
                game.getStoredValue(REV_KAIRN_HERO_TRAITS + player.getFaction()).split(","));
        List<Button> buttons = new ArrayList<>();
        for (String trait : EXPLORATION_TRAITS) {
            boolean controlsTraitPlanet = player.getPlanetsAllianceMode().stream()
                    .map(game::getUnitHolderFromPlanet)
                    .anyMatch(
                            planet -> planet != null && planet.getPlanetTypes().contains(trait));
            if (controlsTraitPlanet && !exploredTraits.contains(trait)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + CHOOSE_REV_KAIRN_TRAIT + trait,
                        "Explore a " + StringUtils.capitalize(trait) + " Planet"));
            }
        }

        if (buttons.isEmpty()) {
            game.removeStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction());
            game.removeStoredValue(REV_KAIRN_HERO_DECKS + player.getFaction());
            game.removeStoredValue(REV_KAIRN_HERO_TRAITS + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " has finished resolving Zairos the First, the Revenant of Kairn hero.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please choose a trait to explore for Zairos the First, the Revenant of Kairn hero.",
                buttons);
    }

    @ButtonHandler(CHOOSE_REV_KAIRN_TRAIT)
    public static void chooseRevKairnTrait(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String trait = buttonID.substring(CHOOSE_REV_KAIRN_TRAIT.length());
        if (!"true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))
                || !EXPLORATION_TRAITS.contains(trait)
                || List.of(game.getStoredValue(REV_KAIRN_HERO_TRAITS + player.getFaction())
                                .split(","))
                        .contains(trait)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String buttonPrefix = player.factionButtonChecker() + CHOOSE_REV_KAIRN_PLANET + trait + "|";
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanetsAllianceMode()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet != null && planet.getPlanetTypes().contains(trait)) {
                buttons.add(Buttons.green(buttonPrefix + planetName, "Explore " + planet.getRepresentation(game)));
            }
        }

        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            showRevKairnHeroTraits(event, game, player);
            return;
        }

        String message = player.getRepresentation() + ", please choose a " + StringUtils.capitalize(trait)
                + " planet to explore for Zairos the First, the Revenant of Kairn hero.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CHOOSE_REV_KAIRN_PLANET)
    public static void chooseRevKairnPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(CHOOSE_REV_KAIRN_PLANET.length());
        int traitEnd = payload.indexOf('|');
        if (traitEnd < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String trait = payload.substring(0, traitEnd);
        String planetName = payload.substring(traitEnd + 1);
        String buttonPrefix = player.factionButtonChecker() + CHOOSE_REV_KAIRN_PLANET + trait + "|";
        List<Button> buttons = new ArrayList<>();
        for (String ownedPlanetName : player.getPlanetsAllianceMode()) {
            Planet planet = game.getUnitHolderFromPlanet(ownedPlanetName);
            if (planet != null && planet.getPlanetTypes().contains(trait)) {
                buttons.add(Buttons.green(buttonPrefix + ownedPlanetName, "Explore " + planet.getRepresentation(game)));
            }
        }

        String message = player.getRepresentation() + ", please choose a " + StringUtils.capitalize(trait)
                + " planet to explore for Zairos the First, the Revenant of Kairn hero.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (!"true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))
                || !EXPLORATION_TRAITS.contains(trait)
                || List.of(game.getStoredValue(REV_KAIRN_HERO_TRAITS + player.getFaction())
                                .split(","))
                        .contains(trait)
                || planet == null
                || !player.getPlanetsAllianceMode().contains(planetName)
                || !planet.getPlanetTypes().contains(trait)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String exploredTraits = game.getStoredValue(REV_KAIRN_HERO_TRAITS + player.getFaction());
        game.setStoredValue(
                REV_KAIRN_HERO_TRAITS + player.getFaction(),
                exploredTraits.isEmpty() ? trait : exploredTraits + "," + trait);
        ButtonHelper.deleteMessage(event);
        ExploreService.explorePlanet(
                event, game.getTileFromPlanet(planetName), planetName, trait, player, false, game, 1, false);
        showRevKairnHeroTraits(event, game, player);
    }

    private static List<Button> getRevKairnExploreDiscardButtons(
            Game game, Player player, String trait, String buttonPrefix) {
        List<Button> buttons = new ArrayList<>();
        for (String exploreId : game.getExploreDiscard(trait)) {
            ExploreModel explore = Mapper.getExplore(exploreId);
            if (explore != null) {
                buttons.add(Buttons.green(buttonPrefix + exploreId, explore.getName()));
            }
        }
        return buttons;
    }

    // Lich token debt pool handling
    public static List<Button> offerLichTokenChoices(Player player, Game game) {
        List<Button> targets = new ArrayList<>();
        for (Player target : game.getRealPlayersExcludingThis(player)) {
            if (player.getDebtTokenCount(target.getColor(), "lich") >= 1) {
                continue;
            }

            targets.add(Buttons.green(
                    player.factionButtonChecker() + "selectLichTarget_" + target.getColor(),
                    target.getFaction(),
                    FactionEmojis.getFactionIcon(target.getFaction())));
        }

        return targets;
    }

    @ButtonHandler("selectLichTarget_")
    public static void resolveAllureOfDarkness(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasAbility("allure_of_darkness")) {
            return;
        }

        String targetColor = buttonID.replace("selectLichTarget_", "");
        Player target = game.getPlayerFromColorOrFaction(targetColor);
        if (target == null) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not find player.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        for (Player targets : game.getRealPlayers()) {
            player.clearAllDebtTokens(targets.getColor(), "lich");
        }

        game.setDebtPoolIcon("lich", FactionEmojis.revenant.emojiString());

        player.addDebtTokens(targetColor, 1, "lich");

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " placed the lich token on " + target.getRepresentation()
                        + "'s commander.");

        ButtonHelper.deleteMessage(event);
    }
}
