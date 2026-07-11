package ti4.service.combat;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNotePlacement;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNoteType;
import ti4.contest.replay.core.CombatRollPayload.DieRollSource;
import ti4.contest.replay.core.CombatRollPayload.RollSegmentType;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronFactionTechsHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronUnitsHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenPromissoryHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum.CrystellumAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum.CrystellumUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersAbilitiesHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersUnitsHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora.KaloraBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora.KaloraLeaderHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora.KaloraUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.vyserix.VyserixBreakthroughHandler;
import ti4.discord.interactions.commands.planet.PlanetExhaust;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.CombatMessageHelper;
import ti4.helpers.CombatModHelper;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperUnits;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.PlanetModel;
import ti4.model.RelicModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.service.breakthrough.ValefarZService;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.HacanFlagshipService;
import ti4.spring.context.SpringContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@UtilityClass
public class CombatRollService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public boolean checkIfUnitsOfType(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType) {
        UnitHolder combatOnHolder = tile.getUnitHolders().get(unitHolderName);
        Map<UnitModel, Integer> playerUnitsByQuantity =
                getUnitsInCombat(tile, combatOnHolder, player, event, rollType, game);
        return !playerUnitsByQuantity.isEmpty();
    }

    public static int secondHalfOfCombatRoll(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType) {
        if (rollType == CombatRollType.bombardment) {
            AshenUnitHandler.clearFlagshipBombardmentContexts(game);
            if (game.getStoredValue("assignedBombardment" + player.getFaction()).isEmpty()) {
                BombardmentService.autoAssignAllBombardmentToAPlanet(player, game, tile);
            }
            List<BombardmentAssignment> assignedUnits = MAPPER.readValue(
                    game.getStoredValue("assignedBombardment" + player.getFaction()),
                    new TypeReference<List<BombardmentAssignment>>() {});

            boolean hasValidBombardment = false;
            List<String> bombardedPlanets = new ArrayList<>();
            for (String planet : BombardmentService.getBombardablePlanets(player, game, tile)) {
                if (assignedUnits.stream().anyMatch(a -> a.planet().equals(planet))) {
                    game.setStoredValue("bombardmentTarget" + player.getFaction(), planet);
                    secondHalfOfCombatRoll(
                            player, game, event, tile, unitHolderName, CombatRollType.bombardment, false);
                    hasValidBombardment = true;
                    bombardedPlanets.add(planet);
                }
            }
            if (!hasValidBombardment) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "No valid bombardment target found. Please assign bombardment to a planet using the buttons and try again.");
            } else if (ButtonHelper.doesPlayerHaveFSHere("kalora_flagship", player, tile)) {
                KaloraUnitHandler.flagshipBombardmentReroll(
                        player, event.getMessageChannel(), tile.getPosition(), bombardedPlanets);
            }
            return 0;
        }
        return secondHalfOfCombatRoll(player, game, event, tile, unitHolderName, rollType, false);
    }

    public static UnitModel getMetaliAFBUnit(Player player) {
        UnitModel metaliFakeUnit = new UnitModel();
        metaliFakeUnit.setAfbDieCount(3);
        metaliFakeUnit.setAfbHitsOn(6);
        metaliFakeUnit.setName("Metali Void Armaments");
        metaliFakeUnit.setAsyncId("MetaliAFB");
        metaliFakeUnit.setId("MetaliAFB");
        metaliFakeUnit.setBaseType("dd");
        metaliFakeUnit.setFaction(player.getFaction());
        return metaliFakeUnit;
    }

    public static UnitModel getProjectionUnit(Player player, boolean tf) {
        UnitModel metaliFakeUnit = new UnitModel();
        int proj = 2;
        if (!tf) {
            proj = 1;
        }
        metaliFakeUnit.setAfbDieCount(proj);
        metaliFakeUnit.setAfbHitsOn(6);
        metaliFakeUnit.setName("Projection of Power");
        metaliFakeUnit.setAsyncId("projectionafb");
        metaliFakeUnit.setId("projectionafb");
        metaliFakeUnit.setBaseType("dd");
        metaliFakeUnit.setFaction(player.getFaction());
        return metaliFakeUnit;
    }

    public static UnitModel getZelianPlanetUnit(Player player, String planetName, int planetCombat) {
        UnitModel zelianFakeUnit = new UnitModel();
        zelianFakeUnit.setCombatDieCount(1);
        zelianFakeUnit.setCombatHitsOn(planetCombat);
        zelianFakeUnit.setName("Zelian Planet " + planetName);
        zelianFakeUnit.setAsyncId("zelianplanet");
        zelianFakeUnit.setId("zelianplanet");
        zelianFakeUnit.setBaseType("dd");
        zelianFakeUnit.setFaction(player.getFaction());
        return zelianFakeUnit;
    }

    public static int secondHalfOfCombatRoll(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType,
            boolean automated) {
        CombatRollPipelineState state =
                new CombatRollPipelineState(player, game, event, tile, unitHolderName, rollType, automated);
        validateCombatRollLocation(state);
        if (state.stopped) return 0;
        prepareCombatRoll(state);
        if (state.stopped) return 0;
        executeCombatRoll(state);
        loadCombatRounds(state);
        announceCombatRound(state);
        publishCombatRollResults(state);
        return state.hits;
    }

    public static void sendSpaceAssignHitsButtons(
            GenericInteractionCreateEvent event, Game game, Player opponent, Tile tile, int hits) {
        List<Button> buttons = new ArrayList<>();

        String plural = "hit" + (hits == 1 ? "" : "s");
        if (opponent.isDummy() || opponent.isNpc()) {
            String id = opponent.dummyPlayerSpoof() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + hits;
            buttons.add(Buttons.green(id, "Auto-assign " + plural + " for Dummy"));

        } else {
            String assignID =
                    opponent.factionButtonChecker() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + hits;
            buttons.add(Buttons.green(assignID, "Auto-assign " + plural));

            String manualID = "getDamageButtons_" + tile.getPosition() + "deleteThis_spacecombat";
            buttons.add(Buttons.red(manualID, "Manually Assign " + plural));

            String cancelID = opponent.factionButtonChecker() + "cancelSpaceHits_" + tile.getPosition() + "_" + hits;
            buttons.add(Buttons.gray(cancelID, "Cancel a Hit"));
        }

        String msg2 = opponent.getRepresentationNoPing() + ", you may automatically assign ";
        msg2 += (hits == 1 ? "the hit" : "hits") + ". ";
        msg2 += ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, hits, event, true);
        if (opponent.hasRelic("metalivoidshielding")) {
            RelicModel relicModel = Mapper.getRelic("metalivoidshielding");
            msg2 += "\nReminder: You have the _" + relicModel.getName() + "_ relic,";
            msg2 += " you may SUSTAIN DAMAGE on one of your non-fighter ships instead of taking a hit.";
        }
        String combatRoundKey = "combatRoundTracker" + opponent.getFaction() + tile.getPosition() + "space";
        String combatRoundValue = game.getStoredValue(combatRoundKey);
        if (opponent.hasUnlockedBreakthrough("crystellumbt") && "1".equals(combatRoundValue)) {
            msg2 +=
                    "\nReminder: You have _Defensive Architecture_.\nFor each unit in the active system that is at capacity, you may give one other non-fighter ship in the same system SUSTAIN DAMAGE until the end of this combat. This is not tracked by the bot.";
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);
    }

    public static String rollForUnits(
            Map<UnitModel, Integer> playerUnitsFlat,
            List<NamedCombatModifierModel> extraRolls,
            List<NamedCombatModifierModel> autoMods,
            List<NamedCombatModifierModel> tempMods,
            Player player,
            Player opponent,
            Game game,
            CombatRollType rollType,
            GenericInteractionCreateEvent event,
            Tile activeSystem,
            UnitHolder unitHolder) {
        Map<Pair<UnitModel, UnitHolder>, Integer> playerUnits = new HashMap<>();
        playerUnitsFlat.forEach((model, count) -> playerUnits.put(new ImmutablePair<>(model, unitHolder), count));
        UnitRollPipelineState state = new UnitRollPipelineState(
                playerUnits,
                extraRolls,
                autoMods,
                tempMods,
                player,
                opponent,
                game,
                rollType,
                event,
                activeSystem,
                unitHolder);
        prepareRollModifiers(state);
        repairUnitsAtStartOfCombatRound(state);
        prepareSingleUnitRollBoost(state);
        mergeDivergingUnitModels(state);
        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : state.playerUnits.entrySet()) {
            UnitRollState unit = prepareUnitRoll(state, entry);
            if (unit == null) continue;
            rollUnitSegments(unit);
        }
        recordRollStatistics(state);
        applyHitMultipliers(state);
        appendHitResults(state);
        appendX89HitMessage(state);
        offerHacanFlagshipRerolls(state);
        appendThalnosRerollOffer(state);
        appendAdditionalHitMessages(state);
        appendDelayedRollNotes(state);
        appendExtraRollMessages(state);
        clearMunitionsReserves(state);
        return buildCombatRollResult(state).message();
    }

    public static Player getOpponent(Player player, List<UnitHolder> unitHolders, Game game) {
        Player opponent = null;
        String playerColorID = Mapper.getColorID(player.getColor());
        List<Player> opponents = unitHolders.stream()
                .flatMap(holder -> holder.getUnitColorsOnHolder().stream())
                .filter(color -> !color.equals(playerColorID))
                .map(game::getPlayerByColorID)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        if (!opponents.isEmpty()) {
            opponent = opponents.getFirst();
        }
        if (opponents.size() > 1) {
            Optional<Player> activeOpponent = opponents.stream()
                    .filter(opp -> opp.getUserID().equals(game.getActivePlayerID()))
                    .findAny();
            if (activeOpponent.isPresent()) {
                opponent = activeOpponent.get();
            }
            if (!game.getStoredValue("hiredGunsInPlay").isEmpty()) {
                Player nokar = game.getPlayerFromColorOrFaction(
                        game.getStoredValue("hiredGunsInPlay").split("_")[0]);
                Player activePlay = game.getPlayerFromColorOrFaction(
                        game.getStoredValue("hiredGunsInPlay").split("_")[1]);
                if (player == nokar || player == activePlay) {
                    for (Player p2 : opponents) {
                        if (p2 != nokar && p2 != activePlay) {
                            opponent = p2;
                        }
                    }
                }
            }
            if (!player.getAllianceMembers().isEmpty()
                    && opponent != null
                    && player.getAllianceMembers().contains(opponent.getFaction())) {
                for (Player p2 : opponents) {
                    if (p2 != player && !player.getAllianceMembers().contains(p2.getFaction())) {
                        opponent = p2;
                    }
                }
            }
        }
        return opponent;
    }

    public static Map<Pair<UnitModel, UnitHolder>, Integer> getUnitsInCombatByHolder(
            Tile tile,
            UnitHolder unitHolder,
            Player player,
            GenericInteractionCreateEvent event,
            CombatRollType roleType,
            Game game) {
        Planet unitHolderPlanet = unitHolder instanceof Planet p ? p : null;
        return switch (roleType) {
            case combatround -> {
                Map<Pair<UnitModel, UnitHolder>, Integer> result = new HashMap<>();
                getCombatRoundUnits(tile, unitHolder, player, event)
                        .forEach((model, count) -> result.put(new ImmutablePair<>(model, unitHolder), count));
                yield result;
            }
            case SpaceCannonDefence -> {
                Map<Pair<UnitModel, UnitHolder>, Integer> result = new HashMap<>();
                getUnitsInSpaceCannonDefence(unitHolderPlanet, player, event)
                        .forEach((model, count) -> result.put(new ImmutablePair<>(model, unitHolder), count));
                yield result;
            }
            case AFB -> getUnitsInAFB(tile, player, event);
            case bombardment -> getUnitsInBombardment(tile, player, event);
            case SpaceCannonOffence -> getUnitsInSpaceCannonOffense(tile, player, event, game);
        };
    }

    public static Map<UnitModel, Integer> getUnitsInCombat(
            Tile tile,
            UnitHolder unitHolder,
            Player player,
            GenericInteractionCreateEvent event,
            CombatRollType roleType,
            Game game) {
        Map<UnitModel, Integer> result = new HashMap<>();
        getUnitsInCombatByHolder(tile, unitHolder, player, event, roleType, game)
                .forEach((key, value) -> result.merge(key.getLeft(), value, Integer::sum));
        return result;
    }

    public static Map<UnitModel, Integer> getProximaBombardUnit(Player player) {
        UnitModel proximaFakeUnit = new UnitModel();
        proximaFakeUnit.setBombardDieCount(3);
        if (player.hasTech("tf-proxima")) {
            proximaFakeUnit.setBombardHitsOn(7);
        } else {
            proximaFakeUnit.setBombardHitsOn(8);
        }
        proximaFakeUnit.setName(Mapper.getTech("proxima").getName());
        proximaFakeUnit.setAsyncId("ProximaBombard");
        proximaFakeUnit.setId("ProximaBombard");
        proximaFakeUnit.setBaseType("dn");
        proximaFakeUnit.setFaction(player.getFaction());
        return Map.of(proximaFakeUnit, 1);
    }

    public static Map<Pair<UnitModel, UnitHolder>, Integer> getUnitsInBombardment(
            Tile tile, Player player, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        UnitHolder spaceHolder = tile.getUnitHolders().get("space");
        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            getUnitsOnHolderByAsyncId(colorID, unitsByAsyncId, unitHolder);
        }
        Map<UnitModel, Integer> unitsInCombat = getUnitsInCombat(player, unitsByAsyncId);

        Map<Pair<UnitModel, UnitHolder>, Integer> output = new HashMap<>(unitsInCombat.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().getBombardDieCount(player) > 0)
                .collect(Collectors.toMap(
                        entry -> new ImmutablePair<>(entry.getKey(), spaceHolder), Map.Entry::getValue)));
        Map<UnitModel, Integer> flatOutput = new HashMap<>();
        output.forEach((k, v) -> flatOutput.merge(k.getLeft(), v, Integer::sum));
        checkBadUnits(player, event, unitsByAsyncId, flatOutput);
        if (player.getGame() != null && player.getGame().playerHasLeaderUnlockedOrAlliance(player, "kaloracommander")) {
            KaloraLeaderHandler.addCommanderBombardmentUnits(player, tile, output);
        }
        return output;
    }

    public static Map<UnitModel, Integer> flattenUnitMap(Map<Pair<UnitModel, UnitHolder>, Integer> map) {
        Map<UnitModel, Integer> result = new HashMap<>();
        map.forEach((k, v) -> result.merge(k.getLeft(), v, Integer::sum));
        return result;
    }

    private static void validateCombatRollLocation(CombatRollPipelineState state) {
        state.combatOnHolder = state.tile.getUnitHolders().get(state.unitHolderName);
        if (state.combatOnHolder == null) {
            MessageHelper.sendMessageToChannel(
                    state.event.getMessageChannel(),
                    "Cannot find the planet " + state.unitHolderName + " on tile " + state.tile.getPosition() + ".");
            state.stopped = true;
            return;
        }
        if (state.rollType == CombatRollType.SpaceCannonDefence && !(state.combatOnHolder instanceof Planet)) {
            MessageHelper.sendMessageToChannel(
                    state.event.getMessageChannel(),
                    "Planet needs to be specified to fire SPACE CANNON against ships on tile "
                            + state.tile.getPosition() + ".");
            state.stopped = true;
        }
    }

    private static void prepareCombatRoll(CombatRollPipelineState state) {
        Map<Pair<UnitModel, UnitHolder>, Integer> playerUnits = getUnitsInCombatByHolder(
                state.tile, state.combatOnHolder, state.player, state.event, state.rollType, state.game);
        addSpecialUnitsForRoll(
                playerUnits,
                state.player,
                state.game,
                state.tile,
                state.combatOnHolder,
                state.unitHolderName,
                state.rollType);
        BombardmentContext bombardment =
                prepareBombardmentContext(playerUnits, state.player, state.game, state.rollType);
        playerUnits = removeUnitsDisabledByArticlesOfWar(playerUnits, state.game, state.event, state.rollType);
        if (reportAndCheckNoUnits(
                playerUnits, state.player, state.game, state.event, state.tile, state.unitHolderName, state.rollType)) {
            state.stopped = true;
            return;
        }
        Player opponent = resolveCombatRollOpponent(
                state.player, bombardment.opponent(), state.game, state.tile, state.combatOnHolder, state.rollType);
        if (isEmpSpaceCannonBlocked(state.player, state.game, state.event, state.tile, state.rollType)) {
            state.stopped = true;
            return;
        }
        Map<UnitModel, Integer> opponentUnits =
                getUnitsInCombat(state.tile, state.combatOnHolder, opponent, state.event, state.rollType, state.game);
        RollModifiers modifiers = collectRollModifiers(
                playerUnits,
                opponentUnits,
                state.player,
                opponent,
                state.game,
                state.tile,
                state.combatOnHolder,
                bombardment.planet(),
                state.rollType);
        state.playerUnits = playerUnits;
        state.opponent = opponent;
        state.modifiers = modifiers;
        state.bombardPlanet = bombardment.planet();
    }

    private static Player resolveCombatRollOpponent(
            Player player,
            Player bombardmentOpponent,
            Game game,
            Tile tile,
            UnitHolder combatOnHolder,
            CombatRollType rollType) {
        if (bombardmentOpponent != null) {
            return bombardmentOpponent;
        }
        List<UnitHolder> combatHolders = new ArrayList<>(List.of(combatOnHolder));
        if (rollType == CombatRollType.SpaceCannonDefence || rollType == CombatRollType.SpaceCannonOffence) {
            combatHolders.add(tile.getUnitHolders().get(Constants.SPACE));
        }
        Player opponent = getOpponent(player, combatHolders, game);
        return opponent == null ? player : opponent;
    }

    private static boolean isEmpSpaceCannonBlocked(
            Player player, Game game, GenericInteractionCreateEvent event, Tile tile, CombatRollType rollType) {
        return game.getRealPlayers().stream().anyMatch(realPlayer -> realPlayer.hasUnit("netrunners_flagship"))
                && NetrunnersUnitsHandler.resolveEmpSpaceCannonBlock(event, game, player, tile, rollType);
    }

    private static void executeCombatRoll(CombatRollPipelineState state) {
        UnitRollPipelineState rollState = new UnitRollPipelineState(
                state.playerUnits,
                state.modifiers.extraRolls(),
                state.modifiers.combatModifiers(),
                state.modifiers.temporaryModifiers(),
                state.player,
                state.opponent,
                state.game,
                state.rollType,
                state.event,
                state.tile,
                state.combatOnHolder);
        prepareRollModifiers(rollState);
        repairUnitsAtStartOfCombatRound(rollState);
        prepareSingleUnitRollBoost(rollState);
        mergeDivergingUnitModels(rollState);
        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : rollState.playerUnits.entrySet()) {
            UnitRollState unit = prepareUnitRoll(rollState, entry);
            if (unit == null) continue;
            rollUnitSegments(unit);
        }
        recordRollStatistics(rollState);
        applyHitMultipliers(rollState);
        appendHitResults(rollState);
        appendX89HitMessage(rollState);
        offerHacanFlagshipRerolls(rollState);
        appendThalnosRerollOffer(rollState);
        appendAdditionalHitMessages(rollState);
        appendDelayedRollNotes(rollState);
        appendExtraRollMessages(rollState);
        clearMunitionsReserves(rollState);
        CombatRollResult result = buildCombatRollResult(rollState);
        String summary = CombatMessageHelper.displayCombatSummary(
                state.player, state.tile, state.combatOnHolder, state.rollType);
        String message = summary + result.message();
        CombatRollPayload.RollHeader header = buildRollHeader(
                state.game, state.player, state.opponent, state.tile, state.combatOnHolder, state.rollType, summary);
        CombatRollPayload payload = result.payload().withHeader(header);
        FOWCombatThreadMirroring.mirrorCombatMessage(state.event, state.player, state.game, message);
        state.rollResult = result;
        state.message = message;
        state.payload = payload;
    }

    private static void loadCombatRounds(CombatRollPipelineState state) {
        state.opponentRound = getStoredCombatRound(state.game, state.opponent, state.tile, state.combatOnHolder, 0);
        state.playerRound = getStoredCombatRound(state.game, state.player, state.tile, state.combatOnHolder, 1);
    }

    private static int getStoredCombatRound(
            Game game, Player player, Tile tile, UnitHolder combatOnHolder, int defaultRound) {
        String key = "combatRoundTracker" + player.getFaction() + tile.getPosition() + combatOnHolder.getName();
        String storedRound = game.getStoredValue(key);
        return storedRound.isEmpty() ? defaultRound : Integer.parseInt(storedRound);
    }

    private static void announceCombatRound(CombatRollPipelineState state) {
        if (state.playerRound > state.opponentRound && state.rollType == CombatRollType.combatround) {
            MessageHelper.sendMessageToChannel(
                    state.event.getMessageChannel(), "## __Start of Combat Round #" + state.playerRound + "__");
        }
    }

    private static void publishCombatRollResults(CombatRollPipelineState state) {
        MessageHelper.sendMessageToChannel(state.event.getMessageChannel(), "");
        AdjustedRollResult adjustedRoll = applyProximaBombardmentCancellation(
                state.player,
                state.opponent,
                state.game,
                state.rollType,
                state.bombardPlanet,
                state.message,
                state.rollResult.totalHits());
        String message = removeTrailingRollSeparator(adjustedRoll.message());
        message = appendAshenBombardmentReminder(state.player, state.rollType, message);
        MessageHelper.sendMessageToChannel(state.event.getMessageChannel(), message);
        boolean trackedCandidateRoll = mirrorCombatReplay(
                state.game,
                state.player,
                state.opponent,
                state.tile,
                message,
                state.rollType,
                state.rollResult,
                state.payload);
        offerThalnosReroll(state.event, state.tile, state.unitHolderName, message);
        if (state.game.isFowMode()) {
            relayFogOfWarCombatResult(state.player, state.opponent, state.event, state.rollType, message);
            handleFogOfWarDummyCombatResult(
                    state.opponent,
                    state.game,
                    state.event,
                    state.tile,
                    state.combatOnHolder,
                    state.rollType,
                    adjustedRoll.hits(),
                    state.opponentRound,
                    state.playerRound);
        } else {
            reportSurprisingDiceRoll(state.game, state.player, state.opponent, message, trackedCandidateRoll);
            handlePublicCombatRoundResults(
                    state.player,
                    state.opponent,
                    state.game,
                    state.event,
                    state.tile,
                    state.combatOnHolder,
                    state.rollType,
                    adjustedRoll.hits(),
                    state.opponentRound,
                    state.playerRound,
                    state.automated);
            handleAntiFighterBarrageResults(
                    state.opponent, state.event, state.tile, state.rollType, adjustedRoll.hits());
        }
        offerSpaceCannonHitAssignmentButtons(
                state.event, state.game, state.opponent, state.player, state.tile, state.rollType, adjustedRoll.hits());
        offerVyserixMorayButtons(
                state.event, state.game, state.player, state.tile, state.rollType, adjustedRoll.hits());
        handleBombardmentResults(
                state.event,
                state.game,
                state.player,
                state.tile,
                state.rollType,
                adjustedRoll.hits(),
                state.bombardPlanet);
        state.hits = adjustedRoll.hits();
    }

    private static String appendAshenBombardmentReminder(Player player, CombatRollType rollType, String message) {
        if (player.hasBreakthrough("ashenbt")) {
            return AshenBreakthroughHandler.appendBombardmentManualReminder(player, rollType, message);
        }
        return message;
    }

    private static AdjustedRollResult applyProximaBombardmentCancellation(
            Player player,
            Player opponent,
            Game game,
            CombatRollType rollType,
            String bombardPlanet,
            String message,
            int hits) {
        if (rollType != CombatRollType.bombardment || opponent == player || !opponent.hasTech("proxima") || hits < 1) {
            return new AdjustedRollResult(message, hits);
        }
        if (opponent.hasTech("tf-proxima")) {
            return new AdjustedRollResult(message + "\n_Proxima Targeting VI_ canceled 1 hit automatically.", hits - 1);
        }
        if (bombardPlanet.isEmpty()) {
            return new AdjustedRollResult(message, hits);
        }
        UnitHolder planet = game.getUnitHolderFromPlanet(bombardPlanet);
        if (planet == null || planet.getGalvanizedUnitCount(player.getColorID()) < 1) {
            return new AdjustedRollResult(message, hits);
        }
        int adjustedHits = Math.max(0, hits - planet.getGalvanizedUnitCount(player.getColorID()));
        int canceledHits = hits - adjustedHits;
        String adjustedMessage = message + "\n_Proxima Targeting VI_ canceled " + canceledHits + " hit"
                + (canceledHits == 1 ? "" : "s") + " automatically.";
        return new AdjustedRollResult(adjustedMessage, adjustedHits);
    }

    private static String removeTrailingRollSeparator(String message) {
        return message.endsWith(";\n") ? message.substring(0, message.length() - 2) : message;
    }

    private static boolean mirrorCombatReplay(
            Game game,
            Player player,
            Player opponent,
            Tile tile,
            String message,
            CombatRollType rollType,
            CombatRollResult rollResult,
            CombatRollPayload payload) {
        CombatReplayService combatReplayService = SpringContext.getBean(CombatReplayService.class);
        boolean trackedCandidateRoll =
                combatReplayService.isTrackedCandidateRoll(game, player, opponent, tile, rollType);
        combatReplayService.mirrorCombatRoll(
                game, player, opponent, tile, message, rollType, rollResult.whiff(), rollResult.slam(), payload);
        return trackedCandidateRoll;
    }

    private static void offerThalnosReroll(
            GenericInteractionCreateEvent event, Tile tile, String unitHolderName, String message) {
        if (!message.contains("adding +1, at the risk of your")) {
            return;
        }
        Button thalnosButton = Buttons.green(
                "startThalnos_" + tile.getPosition() + "_" + unitHolderName, "Roll Thalnos", ExploreEmojis.Relic);
        Button decline = Buttons.gray("deleteButtons", "Decline");
        String thalnosMessage =
                "Use this button to roll for Thalnos.\n-# Note that if it matters, the dice were just rolled in the following format: (normal dice for unit 1)+(normal dice for unit 2)...etc...+(extra dice for unit 1)+(extra dice for unit 2)...etc.\n-# Sol and Letnev agents automatically are given as extra dice for unit 1.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), thalnosMessage, List.of(thalnosButton, decline));
    }

    private static void handleBombardmentResults(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            CombatRollType rollType,
            int h,
            String bombardPlanet) {
        if (rollType == CombatRollType.bombardment) {
            AshenLeadersHandler.offerCommanderBombardmentButtons(event, game, player, h);
            if (h > 0) {
                if (!AshenLeadersHandler.offerHeroBombardmentAssignButtons(event, game, player, h, bombardPlanet)
                        && !game.isFowMode()) {
                    List<Button> buttons = new ArrayList<>();

                    buttons.add(Buttons.red(
                            "getDamageButtons_" + tile.getPosition() + "_bombardment",
                            "Assign Hit" + (h == 1 ? "" : "s")));
                    for (Player p2 : game.getRealPlayersNNeutral()) {
                        if (p2 == player) {
                            continue;
                        }
                        if (!bombardPlanet.isEmpty()
                                && FoWHelper.playerHasUnitsOnPlanet(p2, game.getUnitHolderFromPlanet(bombardPlanet))) {
                            if (p2.isRealPlayer()) {
                                MessageHelper.sendMessageToChannelWithButtons(
                                        game.isFowMode() ? p2.getCorrectChannel() : event.getMessageChannel(),
                                        p2.getRepresentation() + ", please assign the BOMBARDMENT hit"
                                                + (h == 1 ? "" : "s") + ".",
                                        buttons);
                            } else {
                                List<Button> buttons2 = new ArrayList<>();
                                buttons2.add(Buttons.green(
                                        p2.dummyPlayerSpoof() + "autoAssignGroundHits_"
                                                + game.getUnitHolderFromPlanet(bombardPlanet)
                                                        .getName() + "_" + h,
                                        "Auto-assign Hit" + (h == 1 ? "" : "s") + " For Dummy"));
                                MessageHelper.sendMessageToChannelWithButtons(
                                        game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                                        player.getRepresentation() + ", please assign the BOMBARDMENT hit"
                                                + (h == 1 ? "" : "s") + " for the dummy player.",
                                        buttons2);
                            }
                        }
                    }
                }
                if (player.hasAbility("meteor_slings")
                        || player.getPromissoryNotes().containsKey("dspnkhra")) {
                    List<Button> buttons = new ArrayList<>();
                    String planet = game.getStoredValue("bombardmentTarget" + player.getFaction());
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + "meteorSlings_" + planet,
                            "Infantry on " + Helper.getPlanetRepresentation(planet, game)));

                    buttons.add(Buttons.red("deleteButtons", "Done"));
                    String msg2 = player.getRepresentation() + " you could potentially cancel "
                            + (h == 1 ? "the BOMBARDMENT hit" : "some BOMBARDMENT hits")
                            + " to place infantry instead. Use these buttons to do so, and press done when done. The bot did not track how many hits you got. ";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);
                }
                if (player.hasUnlockedBreakthrough("kalorabt")) {
                    KaloraBreakthroughHandler.offerCommitInfantryButton(event, game, player, tile, bombardPlanet);
                }
            }
            if (player.hasTech("x89c4")) {
                for (Player p2 : game.getRealPlayers()) {
                    if (p2.hasPlanetReady(bombardPlanet)) {
                        PlanetExhaust.doAction(p2, bombardPlanet, game);
                        MessageHelper.sendMessageToChannel(
                                p2.getCorrectChannel(),
                                p2.getRepresentation() + ", your planet "
                                        + Helper.getPlanetRepresentation(bombardPlanet, game) + " was exhausted when "
                                        + (game.isFowMode() ? "another player" : player.getRepresentationNoPing())
                                        + " bombarded it with _X-89 Bacterial Weapon ΩΩ_.");
                        break;
                    }
                }
            }
        }
    }

    private static void offerVyserixMorayButtons(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, CombatRollType rollType, int h) {
        if (rollType == CombatRollType.AFB && player.hasUnlockedBreakthrough("vyserixbt")) {
            VyserixBreakthroughHandler.offerMoraySystemButtons(event, game, player, tile, h);
        }
    }

    private static void offerSpaceCannonHitAssignmentButtons(
            GenericInteractionCreateEvent event,
            Game game,
            Player opponent,
            Player player,
            Tile tile,
            CombatRollType rollType,
            int h) {
        if ((!game.isFowMode() || isFoWPrivateChannelRoll(player, event))
                && rollType == CombatRollType.SpaceCannonOffence
                && h > 0
                && opponent != player) {
            MessageChannel channel =
                    isFoWPrivateChannelRoll(player, event) ? opponent.getCorrectChannel() : event.getMessageChannel();
            String msg = "\n" + opponent.getRepresentation(true, true, true, true) + " suffered "
                    + StringHelper.pluralize(h, "hit") + " from SPACE CANNON against your ships.";
            MessageHelper.sendMessageToChannel(channel, msg);
            List<Button> buttons = new ArrayList<>();
            String factionChecker = "FFCC_" + opponent.getFaction() + "_";
            if (opponent.isDummy() || opponent.isNpc()) {
                buttons.add(Buttons.green(
                        opponent.dummyPlayerSpoof() + "autoAssignSpaceCannonOffenceHits_" + tile.getPosition() + "_"
                                + h,
                        "Auto-assign Hit" + (h == 1 ? "" : "s For Dummy")));
            } else {
                buttons.add(Buttons.green(
                        factionChecker + "autoAssignSpaceCannonOffenceHits_" + tile.getPosition() + "_" + h,
                        "Auto-assign Hit" + (h == 1 ? "" : "s")));
            }
            buttons.add(Buttons.red(
                    "getDamageButtons_" + tile.getPosition() + "deleteThis_pds",
                    "Manually Assign Hit" + (h == 1 ? "" : "s")));
            buttons.add(Buttons.gray(
                    factionChecker + "cancelPdsOffenseHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
            String msg2 = opponent.getRepresentationNoPing() + ", you may automatically assign "
                    + (h == 1 ? "the hit" : "hits") + "."
                    + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, h, event, true, true);
            MessageHelper.sendMessageToChannelWithButtons(channel, msg2, buttons);
        }
    }

    private static void handlePublicCombatRoundResults(
            Player player,
            Player opponent,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            UnitHolder combatOnHolder,
            CombatRollType rollType,
            int hits,
            int opponentRound,
            int playerRound,
            boolean automated) {
        if (rollType != CombatRollType.combatround || opponent == player) return;
        if (combatOnHolder instanceof Planet) {
            handleGroundCombatRoundResults(
                    player, opponent, game, event, tile, combatOnHolder, hits, opponentRound, playerRound, automated);
        } else {
            handleSpaceCombatRoundResults(
                    opponent, game, event, tile, combatOnHolder, hits, opponentRound, playerRound);
        }
    }

    private static void handleGroundCombatRoundResults(
            Player player,
            Player opponent,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            UnitHolder combatOnHolder,
            int hits,
            int opponentRound,
            int playerRound,
            boolean automated) {
        GroundCombatResultContext context = new GroundCombatResultContext(
                player, opponent, game, event, tile, combatOnHolder, hits, opponentRound, playerRound);
        reportGroundCombatHits(opponent, event, hits, playerRound);
        if (automated) {
            reportAutomatedValkyrieParticleWeaveHit(player, opponent, event, hits);
            return;
        }
        if (hits > 0) {
            offerGroundCombatHitAssignment(context);
            offerValkyrieParticleWeaveHitAssignment(context);
        } else {
            offerNextGroundCombatRound(context);
        }
    }

    private static void reportGroundCombatHits(
            Player opponent, GenericInteractionCreateEvent event, int hits, int playerRound) {
        String message = "\n" + opponent.getRepresentation(true, true, true, true) + ", you suffered "
                + StringHelper.pluralize(hits, "hit") + " in round #" + playerRound + ".";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
    }

    private static void offerGroundCombatHitAssignment(GroundCombatResultContext context) {
        List<Button> buttons = new ArrayList<>();
        if (context.playerRound > context.opponentRound) {
            String prefix =
                    context.opponent.isDummy() || context.opponent.isNpc() ? context.opponent.dummyPlayerSpoof() : "";
            buttons.add(Buttons.blue(
                    prefix + "combatRoll_" + context.tile.getPosition() + "_" + context.combatOnHolder.getName(),
                    "Roll Dice " + (prefix.isEmpty() ? "" : "For Dummy ") + "for Combat Round #"
                            + (context.opponentRound + 1)));
        }
        if (context.opponent.isDummy() || context.opponent.isNpc()) {
            buttons.add(Buttons.green(
                    context.opponent.dummyPlayerSpoof() + "autoAssignGroundHits_" + context.combatOnHolder.getName()
                            + "_" + context.hits,
                    "Auto-assign Hit" + (context.hits == 1 ? "" : "s") + " For Dummy"));
        } else {
            buttons.add(Buttons.green(
                    context.opponent.factionButtonChecker() + "autoAssignGroundHits_" + context.combatOnHolder.getName()
                            + "_" + context.hits,
                    "Auto-assign Hit" + (context.hits == 1 ? "" : "s")));
            buttons.add(Buttons.red(
                    "getDamageButtons_" + context.tile.getPosition() + "deleteThis_groundcombat",
                    "Manually Assign Hit" + (context.hits == 1 ? "" : "s")));
            buttons.add(Buttons.gray(
                    context.opponent.factionButtonChecker() + "cancelGroundHits_" + context.tile.getPosition() + "_"
                            + context.hits,
                    "Cancel a Hit"));
            AshenPromissoryHandler.addFromTheAshesButton(
                    buttons,
                    context.game,
                    context.opponent,
                    context.player,
                    context.tile,
                    context.combatOnHolder,
                    context.hits);
            if (context.opponent.hasUnit("crystellum_mech")) {
                CrystellumUnitHandler.offerRefractumButtonIfRelevant(
                        buttons, context.opponent, context.game, context.tile, context.combatOnHolder, context.hits);
            }
        }
        String message = context.opponent.getRepresentationUnfogged() + " you may autoassign "
                + StringHelper.pluralize(context.hits, "hit") + ".";
        MessageHelper.sendMessageToChannelWithButtons(context.event.getMessageChannel(), message, buttons);
    }

    private static void offerValkyrieParticleWeaveHitAssignment(GroundCombatResultContext context) {
        if (!context.opponent.hasTech("vpw")) return;
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                context.player.factionButtonChecker() + "autoAssignGroundHits_" + context.combatOnHolder.getName()
                        + "_1",
                "Auto-assign Hit"));
        buttons.add(Buttons.red(
                "getDamageButtons_" + context.tile.getPosition() + "deleteThis_groundcombat", "Manually Assign Hit"));
        buttons.add(Buttons.gray(
                context.player.factionButtonChecker() + "cancelGroundHits_" + context.tile.getPosition() + "_1",
                "Cancel a Hit"));
        String message = context.player.getRepresentationUnfogged()
                + " you got hit by _Valkyrie Particle Weave_. You may autoassign 1 hit.";
        MessageHelper.sendMessageToChannelWithButtons(context.event.getMessageChannel(), message, buttons);
    }

    private static void offerNextGroundCombatRound(GroundCombatResultContext context) {
        if (context.playerRound <= context.opponentRound) return;
        String prefix =
                context.opponent.isDummy() || context.opponent.isNpc() ? context.opponent.dummyPlayerSpoof() : "";
        List<Button> buttons = List.of(Buttons.blue(
                prefix + "combatRoll_" + context.tile.getPosition() + "_" + context.combatOnHolder.getName(),
                "Roll Dice " + (prefix.isEmpty() ? "" : "For Dummy ") + "for Combat Round #"
                        + (context.opponentRound + 1)));
        String message = context.opponent.getRepresentationUnfogged() + " you may roll dice for Combat Round #"
                + (context.opponentRound + 1) + ".";
        MessageHelper.sendMessageToChannelWithButtons(context.event.getMessageChannel(), message, buttons);
    }

    private static void reportAutomatedValkyrieParticleWeaveHit(
            Player player, Player opponent, GenericInteractionCreateEvent event, int hits) {
        if (!opponent.hasTech("vpw") || hits < 1) return;
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " suffered 1 hit due to _Valkyrie Particle Weave_.");
    }

    private static void handleSpaceCombatRoundResults(
            Player opponent,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            UnitHolder combatOnHolder,
            int hits,
            int opponentRound,
            int playerRound) {
        SpaceCombatResultContext context = new SpaceCombatResultContext(
                opponent, game, event, tile, combatOnHolder, hits, opponentRound, playerRound);
        List<Button> buttons = buildNextSpaceCombatRoundButtons(context);
        reportSpaceCombatHits(context);
        if (hits > 0) sendSpaceCombatHitAssignment(context, buttons);
        else offerNextSpaceCombatRound(context, buttons);
    }

    private static List<Button> buildNextSpaceCombatRoundButtons(SpaceCombatResultContext context) {
        List<Button> buttons = new ArrayList<>();
        if (context.playerRound() <= context.opponentRound()) return buttons;
        Player opponent = context.opponent();
        String idPrefix = opponent.isDummy() || opponent.isNpc() ? opponent.dummyPlayerSpoof() : "";
        String labelPrefix = opponent.isDummy() || opponent.isNpc() ? "Roll Dice For Dummy For " : "Roll Dice For ";
        buttons.add(Buttons.blue(
                idPrefix + "combatRoll_" + context.tile().getPosition() + "_"
                        + context.combatOnHolder().getName(),
                labelPrefix + "Combat Round #" + (context.opponentRound() + 1)));
        return buttons;
    }

    private static void reportSpaceCombatHits(SpaceCombatResultContext context) {
        String message = "\n" + context.opponent().getRepresentation(true, true, true, true) + ", you suffered "
                + StringHelper.pluralize(context.hits(), "hit") + " in round #" + context.playerRound() + ".";
        MessageHelper.sendMessageToChannel(context.event().getMessageChannel(), message);
    }

    private static void sendSpaceCombatHitAssignment(SpaceCombatResultContext context, List<Button> buttons) {
        addSpaceCombatHitAssignmentButtons(context, buttons);
        String message = buildSpaceCombatHitAssignmentMessage(context);
        MessageHelper.sendMessageToChannelWithButtons(context.event().getMessageChannel(), message, buttons);
    }

    private static void addSpaceCombatHitAssignmentButtons(SpaceCombatResultContext context, List<Button> buttons) {
        Player opponent = context.opponent();
        int hits = context.hits();
        if (opponent.isDummy() || opponent.isNpc()) {
            buttons.add(Buttons.green(
                    opponent.dummyPlayerSpoof() + "autoAssignSpaceHits_"
                            + context.tile().getPosition() + "_" + hits,
                    "Auto-assign Hit" + (hits == 1 ? "" : "s") + " For Dummy"));
            return;
        }
        String factionChecker = "FFCC_" + opponent.getFaction() + "_";
        buttons.add(Buttons.green(
                factionChecker + "autoAssignSpaceHits_" + context.tile().getPosition() + "_" + hits,
                "Auto-assign Hit" + (hits == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + context.tile().getPosition() + "deleteThis_spacecombat",
                "Manually Assign Hit" + (hits == 1 ? "" : "s")));
        buttons.add(Buttons.gray(
                factionChecker + "cancelSpaceHits_" + context.tile().getPosition() + "_" + hits, "Cancel a Hit"));
        if (opponent.hasAbility("refraction")) {
            CrystellumAbilityHandler.addRefractionButtonIfRelevant(
                    buttons, opponent, context.game(), context.tile(), hits);
        }
    }

    private static String buildSpaceCombatHitAssignmentMessage(SpaceCombatResultContext context) {
        Player opponent = context.opponent();
        String message = opponent.getRepresentationNoPing() + ", you may automatically assign "
                + (context.hits() == 1 ? "the hit" : "hits") + ". "
                + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                        opponent, context.game(), context.tile(), context.hits(), context.event(), true);
        if (opponent.hasRelic("metalivoidshielding")) {
            message += "\nReminder: You have the _"
                    + Mapper.getRelic("metalivoidshielding").getName()
                    + "_ relic, you may SUSTAIN DAMAGE on one of your non-fighter ships instead of taking a hit.";
        }
        if (opponent.hasUnlockedBreakthrough("crystellumbt") && context.playerRound() == 1) {
            message +=
                    "\nReminder: You have _Defensive Architecture_.\nFor each unit in the active system that is at capacity, you may give one other non-fighter ship in the same system SUSTAIN DAMAGE until the end of this combat. This is not tracked by the bot.";
        }
        return message;
    }

    private static void offerNextSpaceCombatRound(SpaceCombatResultContext context, List<Button> buttons) {
        if (context.playerRound() <= context.opponentRound()) return;
        String message = context.opponent().getRepresentationUnfogged() + " you may roll dice for Combat Round #"
                + (context.opponentRound() + 1) + ".";
        MessageHelper.sendMessageToChannelWithButtons(context.event().getMessageChannel(), message, buttons);
    }

    private static void handleAntiFighterBarrageResults(
            Player opponent, GenericInteractionCreateEvent event, Tile tile, CombatRollType rollType, int hits) {
        if (rollType != CombatRollType.AFB || hits < 1) return;
        String message = opponent.getRepresentation() + ", you may automatically assign "
                + (hits == 1 ? "the hit" : "hits") + " from AFB.";
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), message, buildAntiFighterBarrageAssignmentButtons(opponent, tile, hits));
    }

    private static List<Button> buildAntiFighterBarrageAssignmentButtons(Player opponent, Tile tile, int hits) {
        List<Button> buttons = new ArrayList<>();
        String label = "Auto-assign Hit" + (hits == 1 ? "" : "s");
        if (opponent.isNpc() || opponent.isDummy()) {
            buttons.add(Buttons.green(
                    opponent.dummyPlayerSpoof() + "autoAssignAFBHits_" + tile.getPosition() + "_" + hits,
                    label + " For Dummy"));
            return buttons;
        }
        buttons.add(Buttons.green(
                opponent.factionButtonChecker() + "autoAssignAFBHits_" + tile.getPosition() + "_" + hits, label));
        buttons.add(Buttons.red(
                opponent.factionButtonChecker() + "getDamageButtons_" + tile.getPosition() + "_afb",
                "Manually Assign Hit" + (hits == 1 ? "" : "s")));
        buttons.add(Buttons.gray(
                opponent.factionButtonChecker() + "cancelAFBHits_" + tile.getPosition() + "_" + hits, "Cancel a Hit"));
        return buttons;
    }

    private static void relayFogOfWarCombatResult(
            Player player,
            Player opponent,
            GenericInteractionCreateEvent event,
            CombatRollType rollType,
            String message) {
        if (!isFoWPrivateChannelRoll(player, event)) return;
        if (rollType == CombatRollType.SpaceCannonOffence) {
            relayPrivateSpaceCannonResult(player, opponent, message);
        } else if (rollType == CombatRollType.bombardment) {
            remindPlayerToRelayPrivateBombardment(player);
        }
    }

    private static void relayPrivateSpaceCannonResult(Player player, Player opponent, String message) {
        MessageHelper.sendMessageToChannel(
                opponent.getCorrectChannel(),
                opponent.getRepresentationUnfogged() + " "
                        + FOWCombatThreadMirroring.parseCombatRollMessage(message, player));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), "Roll result was sent to " + opponent.getRepresentationNoPing());
    }

    private static void remindPlayerToRelayPrivateBombardment(Player player) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " This roll result is not automatically relayed. Please communicate the hits to the opponent manually.");
    }

    private static void handleFogOfWarDummyCombatResult(
            Player opponent,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            UnitHolder combatOnHolder,
            CombatRollType rollType,
            int hits,
            int opponentRound,
            int playerRound) {
        if ((opponent.isDummy() || opponent.isNpc()) && hits > 0) {
            List<Button> buttons = new ArrayList<>();
            if (combatOnHolder instanceof Planet) {
                if (playerRound > opponentRound) {
                    buttons.add(Buttons.blue(
                            opponent.dummyPlayerSpoof() + "combatRoll_" + tile.getPosition() + "_"
                                    + combatOnHolder.getName(),
                            "Roll Dice For Dummy for Combat Round #" + (opponentRound + 1)));
                }
                buttons.add(Buttons.green(
                        opponent.dummyPlayerSpoof() + "autoAssignGroundHits_" + combatOnHolder.getName() + "_" + hits,
                        "Auto-assign Hit" + (hits == 1 ? "" : "s") + " For Dummy"));
                String msg = opponent.getRepresentationUnfogged() + " you may autoassign "
                        + StringHelper.pluralize(hits, "hit") + ".";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
            } else {
                String msg2 = opponent.getRepresentationNoPing() + ", you may automatically assign "
                        + (hits == 1 ? "the hit" : "hits") + ".";
                if (rollType == CombatRollType.AFB) {
                    buttons.add(Buttons.green(
                            opponent.dummyPlayerSpoof() + "autoAssignAFBHits_" + tile.getPosition() + "_" + hits,
                            "Auto-assign Hit" + (hits == 1 ? "" : "s For Dummy")));
                } else {
                    buttons.add(Buttons.green(
                            opponent.dummyPlayerSpoof() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + hits,
                            "Auto-assign Hits For Dummy"));
                    msg2 += ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, hits, event, true);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);
            }
        }
    }

    private static void reportSurprisingDiceRoll(
            Game game, Player player, Player opponent, String message, boolean trackedCandidateRoll) {
        if (!trackedCandidateRoll && !"none".equals(game.getStoredValue("surprisingDiceRoll"))) {
            StringBuilder disaster;
            if ("hits".equals(game.getStoredValue("surprisingDiceRoll"))) {
                disaster = new StringBuilder(player.getRepresentation() + " has rolled grievously against "
                        + opponent.getRepresentation() + " in " + game.getName() + ".");
            } else {
                disaster = new StringBuilder(player.getRepresentation() + " has rolled dismally against "
                        + opponent.getRepresentation() + " in " + game.getName() + ".");
            }
            for (String line : message.split("\n")) {
                if (line.startsWith("> `") || line.startsWith("**Total hits")) {
                    disaster.append('\n').append(line);
                }
            }
            DisasterWatchHelper.sendMessageInDisasterWatch(game, disaster.toString());
        }
    }

    private static void addSpecialUnitsForRoll(
            Map<Pair<UnitModel, UnitHolder>, Integer> units,
            Player player,
            Game game,
            Tile tile,
            UnitHolder combatOnHolder,
            String unitHolderName,
            CombatRollType rollType) {
        if (rollType == CombatRollType.AFB && player.hasRelic("metalivoidarmaments")) {
            units.put(new ImmutablePair<>(getMetaliAFBUnit(player), combatOnHolder), 1);
        }
        if (rollType == CombatRollType.AFB && player.hasTech("tf-projectionofpow")) {
            units.put(new ImmutablePair<>(getProjectionUnit(player, true), combatOnHolder), 1);
        }
        if (player.hasAbility("projection_of_power") && isAdjacentToPlayersSpaceDock(game, player, tile)) {
            units.put(new ImmutablePair<>(getProjectionUnit(player, false), combatOnHolder), 1);
        }
        if (rollType == CombatRollType.combatround && player.hasActiveBreakthrough("zelianbt")) {
            addEligiblePlanetCombatUnits(units, player, game, tile, combatOnHolder, unitHolderName, false);
        }
        if (rollType == CombatRollType.combatround
                && player.hasTech("tf-hostileplanetoids")
                && Constants.SPACE.equalsIgnoreCase(unitHolderName)) {
            addEligiblePlanetCombatUnits(units, player, game, tile, combatOnHolder, unitHolderName, true);
        }
    }

    private static Map<Pair<UnitModel, UnitHolder>, Integer> removeUnitsDisabledByArticlesOfWar(
            Map<Pair<UnitModel, UnitHolder>, Integer> units,
            Game game,
            GenericInteractionCreateEvent event,
            CombatRollType rollType) {
        if (!ButtonHelper.isLawInPlay(game, "articles_war")) {
            return units;
        }
        units = removeDisabledUnit(
                units,
                "naaz_mech_space",
                event,
                "Skipping Z-Grav Eidolon (Naaz-Rokha mech) combat rolls due to _Articles of War_.");
        if (rollType == CombatRollType.SpaceCannonDefence || rollType == CombatRollType.SpaceCannonOffence) {
            units = removeDisabledUnit(
                    units,
                    "xxcha_mech",
                    event,
                    "Skipping Indomitus (Xxcha mech) SPACE CANNON rolls due to _Articles of War_.");
        }
        if (rollType == CombatRollType.bombardment) {
            units = removeDisabledUnit(
                    units,
                    "l1z1x_mech",
                    event,
                    "Skipping Annihilator (L1Z1X mech) BOMBARDMENT rolls due to _Articles of War_.");
        }
        return units;
    }

    private static boolean reportAndCheckNoUnits(
            Map<Pair<UnitModel, UnitHolder>, Integer> units,
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType) {
        if (!units.isEmpty()) {
            return false;
        }
        String location = Constants.SPACE.equalsIgnoreCase(unitHolderName)
                ? unitHolderName
                : Helper.getPlanetRepresentation(unitHolderName, game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "There are no units in " + location + " on tile " + tile.getPosition() + " for player "
                        + player.getColor() + " " + player.getFactionEmoji() + " for the combat roll type " + rollType
                        + "\nPing bothelper if this seems to be in error.");
        return true;
    }

    private static BombardmentContext prepareBombardmentContext(
            Map<Pair<UnitModel, UnitHolder>, Integer> units, Player player, Game game, CombatRollType rollType) {
        String planet = game.getStoredValue("bombardmentTarget" + player.getFaction());
        if (rollType != CombatRollType.bombardment || planet.isEmpty()) {
            return new BombardmentContext("", null);
        }
        if (player.hasUnit("ashen_flagship")) {
            AshenUnitHandler.prepareFlagshipBombardmentContext(game, player, planet);
        }
        limitUnitsToBombardmentAssignments(units, player, game, planet);
        Player opponent = game.getRealPlayersNNeutral().stream()
                .filter(candidate -> candidate.getPlanets().contains(planet))
                .findFirst()
                .orElse(null);
        return new BombardmentContext(planet, opponent);
    }

    private static void limitUnitsToBombardmentAssignments(
            Map<Pair<UnitModel, UnitHolder>, Integer> units, Player player, Game game, String planet) {
        List<BombardmentAssignment> assignedUnits = MAPPER.readValue(
                game.getStoredValue("assignedBombardment" + player.getFaction()),
                new TypeReference<List<BombardmentAssignment>>() {});
        Map<String, Integer> remainingAssignedByAsyncId = new HashMap<>();
        for (BombardmentAssignment assignedUnit : assignedUnits) {
            if (assignedUnit.planet().equals(planet) && assignedUnit.sourceId() != null) {
                remainingAssignedByAsyncId.merge(assignedUnit.sourceId(), 1, Integer::sum);
            }
        }
        for (Pair<UnitModel, UnitHolder> unit : new ArrayList<>(units.keySet())) {
            String asyncId = unit.getLeft().getAsyncId();
            int available = remainingAssignedByAsyncId.getOrDefault(asyncId, 0);
            int count = Math.min(available, units.get(unit));
            if (count > 0) {
                remainingAssignedByAsyncId.put(asyncId, available - count);
                units.put(unit, count);
            } else {
                units.remove(unit);
            }
        }
    }

    private static Map<Pair<UnitModel, UnitHolder>, Integer> removeDisabledUnit(
            Map<Pair<UnitModel, UnitHolder>, Integer> units,
            String alias,
            GenericInteractionCreateEvent event,
            String message) {
        if (units.keySet().stream()
                .noneMatch(pair -> alias.equals(pair.getLeft().getAlias()))) {
            return units;
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        return units.entrySet().stream()
                .filter(entry -> !alias.equals(entry.getKey().getLeft().getAlias()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static RollModifiers collectRollModifiers(
            Map<Pair<UnitModel, UnitHolder>, Integer> playerUnits,
            Map<UnitModel, Integer> opponentUnits,
            Player player,
            Player opponent,
            Game game,
            Tile tile,
            UnitHolder combatOnHolder,
            String bombardPlanet,
            CombatRollType rollType) {
        TileModel tileModel = TileHelper.getTileById(tile.getTileID());
        Map<UnitModel, Integer> playerUnitsFlat = flattenUnitMap(playerUnits);
        List<NamedCombatModifierModel> combatModifiers = CombatModHelper.getModifiers(
                player,
                opponent,
                playerUnitsFlat,
                opponentUnits,
                tileModel,
                game,
                rollType,
                combatOnHolder,
                Constants.COMBAT_MODIFIERS);
        List<NamedCombatModifierModel> extraRolls = CombatModHelper.getModifiers(
                player,
                opponent,
                playerUnitsFlat,
                opponentUnits,
                tileModel,
                game,
                rollType,
                combatOnHolder,
                Constants.COMBAT_EXTRA_ROLLS);
        removeUnassignedBombardmentExtraRolls(extraRolls, game, player, bombardPlanet, rollType);

        CombatTempModHelper.ensureValidTempMods(player, tileModel, combatOnHolder);
        CombatTempModHelper.initializeNewTempMods(player, tileModel, combatOnHolder);
        List<NamedCombatModifierModel> temporaryModifiers =
                new ArrayList<>(CombatTempModHelper.buildCurrentRoundTempNamedModifiers(
                        player, tileModel, combatOnHolder, false, rollType));
        temporaryModifiers.addAll(CombatTempModHelper.buildCurrentRoundTempNamedModifiers(
                opponent, tileModel, combatOnHolder, true, rollType));
        if (game.getRealPlayers().stream().anyMatch(realPlayer -> realPlayer.hasAbility("control_network"))) {
            temporaryModifiers.addAll(NetrunnersAbilitiesHandler.getPendingControlNetworkSpaceCannonModifier(
                    game, player, tile, combatOnHolder, rollType));
        }
        if (player.hasTech("beironats")) {
            extraRolls.addAll(IronFactionTechsHandler.getAdvancedTargetingSystemsExtraRollModifier(
                    game, player, opponent, tile, combatOnHolder, rollType));
        }
        return new RollModifiers(combatModifiers, extraRolls, temporaryModifiers);
    }

    private static void removeUnassignedBombardmentExtraRolls(
            List<NamedCombatModifierModel> extraRolls,
            Game game,
            Player player,
            String bombardPlanet,
            CombatRollType rollType) {
        String storedAssignments = game.getStoredValue("assignedBombardment" + player.getFaction());
        if (storedAssignments.isEmpty() || rollType != CombatRollType.bombardment) {
            return;
        }
        List<BombardmentAssignment> assignments =
                MAPPER.readValue(storedAssignments, new TypeReference<List<BombardmentAssignment>>() {});
        extraRolls.removeIf(modifier -> isUnassignedBombardmentExtraRoll(modifier, assignments, bombardPlanet));
    }

    private static boolean isUnassignedBombardmentExtraRoll(
            NamedCombatModifierModel modifier, List<BombardmentAssignment> assignments, String bombardPlanet) {
        String alias = modifier.getModifier().getAlias();
        if (alias == null) {
            return false;
        }
        List<BombardmentAssignment> planetAssignments = assignments.stream()
                .filter(a -> a.planet().equals(bombardPlanet))
                .toList();
        return switch (alias.toLowerCase()) {
            case "plus1_roll_plasmascoring" ->
                planetAssignments.stream().noneMatch(a -> "plasmascoring".equals(a.sourceId()));
            case "plus1_roll_argent_commander_bombard" ->
                planetAssignments.stream().noneMatch(a -> "argentcommander".equals(a.sourceId()));
            case "roll_1_for_galvanize_bombard" ->
                planetAssignments.stream().noneMatch(BombardmentAssignment::galvanized);
            default -> false;
        };
    }

    private static boolean isAdjacentToPlayersSpaceDock(Game game, Player player, Tile tile) {
        for (Tile spaceDockTile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock)) {
            if (FoWHelper.getAdjacentTiles(game, spaceDockTile.getPosition(), player, false, true)
                    .contains(tile.getPosition())) {
                return true;
            }
        }
        return false;
    }

    private static void addEligiblePlanetCombatUnits(
            Map<Pair<UnitModel, UnitHolder>, Integer> units,
            Player player,
            Game game,
            Tile tile,
            UnitHolder combatOnHolder,
            String unitHolderName,
            boolean spaceOnly) {
        for (UnitHolder planet : tile.getPlanetUnitHolders()) {
            boolean eligibleHolder = spaceOnly
                    ? Constants.SPACE.equalsIgnoreCase(unitHolderName)
                    : Constants.SPACE.equalsIgnoreCase(unitHolderName)
                            || planet.getName().equalsIgnoreCase(unitHolderName);
            if (player.getPlanetsAllianceMode().contains(planet.getName()) && eligibleHolder) {
                int resources = Helper.getPlanetResources(planet.getName(), game);
                units.put(
                        new ImmutablePair<>(
                                getZelianPlanetUnit(player, Helper.getPlanetName(planet.getName()), 10 - resources),
                                combatOnHolder),
                        1);
            }
        }
    }

    // This roll was made from fow private channel and not from a combat thread
    private static boolean isFoWPrivateChannelRoll(Player player, GenericInteractionCreateEvent event) {
        return event.getMessageChannel().equals(player.getPrivateChannel());
    }

    static CombatRollResult rollForUnitsWithResult(
            Map<Pair<UnitModel, UnitHolder>, Integer> playerUnits,
            List<NamedCombatModifierModel> extraRolls,
            List<NamedCombatModifierModel> autoMods,
            List<NamedCombatModifierModel> tempMods,
            Player player,
            Player opponent,
            Game game,
            CombatRollType rollType,
            GenericInteractionCreateEvent event,
            Tile activeSystem,
            UnitHolder unitHolder) {
        UnitRollPipelineState state = new UnitRollPipelineState(
                playerUnits,
                extraRolls,
                autoMods,
                tempMods,
                player,
                opponent,
                game,
                rollType,
                event,
                activeSystem,
                unitHolder);
        prepareRollModifiers(state);
        repairUnitsAtStartOfCombatRound(state);
        prepareSingleUnitRollBoost(state);
        mergeDivergingUnitModels(state);
        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : state.playerUnits.entrySet()) {
            UnitRollState unit = prepareUnitRoll(state, entry);
            if (unit == null) continue;
            rollUnitSegments(unit);
        }
        recordRollStatistics(state);
        applyHitMultipliers(state);
        appendHitResults(state);
        appendX89HitMessage(state);
        offerHacanFlagshipRerolls(state);
        appendThalnosRerollOffer(state);
        appendAdditionalHitMessages(state);
        appendDelayedRollNotes(state);
        appendExtraRollMessages(state);
        clearMunitionsReserves(state);
        return buildCombatRollResult(state);
    }

    private static UnitRollState prepareUnitRoll(
            UnitRollPipelineState state, Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry) {
        UnitRollState unit = new UnitRollState(state, entry);
        calculateUnitCombatModifier(unit);
        calculateUnitExtraRolls(unit);
        consumeBestExtraRollModifiers(unit);
        applyCombatRoundProfile(unit);
        normalizeThalnosUnitDice(unit);
        applyExperimentalBattlestationLimit(unit);
        applyTnelisAgentLimit(unit);
        if (!applyMetaliVoidLimit(unit)) return null;
        selectSingleUnitBoostSegments(unit);
        unit.ogNumOfUnit = unit.numOfUnit;
        unit.baseModifierToHit = unit.modifierToHit;
        return unit;
    }

    private static void calculateUnitCombatModifier(UnitRollState unit) {
        unit.modifierToHit = CombatModHelper.getCombinedModifierForUnit(
                unit.unitModel,
                unit.numOfUnit,
                unit.pipeline.mods,
                unit.pipeline.player,
                unit.pipeline.opponent,
                unit.pipeline.game,
                unit.pipeline.playerUnitsList,
                unit.pipeline.rollType,
                unit.pipeline.activeSystem,
                unit.perUnitHolder);
    }

    private static void calculateUnitExtraRolls(UnitRollState unit) {
        unit.availableExtraRolls = unit.pipeline.extraRolls.stream()
                .filter(modifier -> !unit.pipeline.consumedBestMods.contains(
                        modifier.getModifier().getAlias()))
                .collect(Collectors.toList());
        unit.extraRollsForUnit = CombatModHelper.getCombinedModifierForUnit(
                unit.unitModel,
                unit.numOfUnit,
                unit.availableExtraRolls,
                unit.pipeline.player,
                unit.pipeline.opponent,
                unit.pipeline.game,
                unit.pipeline.playerUnitsList,
                unit.pipeline.rollType,
                unit.pipeline.activeSystem,
                unit.perUnitHolder);
    }

    private static void consumeBestExtraRollModifiers(UnitRollState unit) {
        if (unit.extraRollsForUnit < 1) return;
        for (NamedCombatModifierModel modifier : unit.availableExtraRolls) {
            String scope = modifier.getModifier().getScope();
            boolean bestUnitScope = "_best_".equals(scope)
                    || "_bestCap_".equals(scope)
                    || (scope != null && scope.contains("_mostdice_"));
            if (bestUnitScope
                    && Boolean.TRUE.equals(modifier.getModifier()
                            .isInScopeForUnit(
                                    unit.unitModel,
                                    unit.pipeline.playerUnitsList,
                                    unit.pipeline.rollType,
                                    unit.pipeline.game,
                                    unit.pipeline.player))) {
                unit.pipeline.consumedBestMods.add(modifier.getModifier().getAlias());
            }
        }
    }

    private static void applyCombatRoundProfile(UnitRollState unit) {
        if (unit.pipeline.rollType != CombatRollType.combatround) return;
        CombatStatsService.CombatRoundProfile profile = CombatStatsService.getCombatRoundProfile(
                true, unit.unitModel, unit.pipeline.player, unit.pipeline.activeSystem, unit.pipeline.opponent, false);
        unit.toHit = profile.hitsOn();
        unit.numRollsPerUnit = profile.diceCount();
    }

    private static void normalizeThalnosUnitDice(UnitRollState unit) {
        if (!unit.pipeline.isThalnosReroll || (unit.numRollsPerUnit < 2 && unit.extraRollsForUnit < 1)) return;
        unit.extraRollsCount = true;
        unit.numRollsPerUnit = 1;
        unit.extraRollsForUnit = 0;
    }

    private static void applyExperimentalBattlestationLimit(UnitRollState unit) {
        if (unit.pipeline.rollType != CombatRollType.SpaceCannonOffence
                || unit.numRollsPerUnit != 3
                || !"spacedock".equalsIgnoreCase(unit.unitModel.getBaseType())) return;
        unit.numOfUnit = 1;
        unit.pipeline.game.setStoredValue("EBSFaction", "");
    }

    private static void applyTnelisAgentLimit(UnitRollState unit) {
        if (unit.pipeline.rollType != CombatRollType.bombardment
                || unit.numRollsPerUnit < 2
                || !"destroyer".equalsIgnoreCase(unit.unitModel.getBaseType())) return;
        unit.numOfUnit = 1;
        unit.pipeline.game.setStoredValue("TnelisAgentFaction", "");
    }

    private static boolean applyMetaliVoidLimit(UnitRollState unit) {
        boolean usingMetaliVoid =
                unit.unitModel.getAfbDieCount() == 0 && unit.unitModel.getAfbDieCount(unit.pipeline.player) == 3;
        if (unit.pipeline.rollType != CombatRollType.AFB || !usingMetaliVoid) return true;
        unit.numOfUnit = 1;
        if (unit.pipeline.metaliVoidCounted) return false;
        unit.pipeline.metaliVoidCounted = true;
        return true;
    }

    private static void selectSingleUnitBoostSegments(UnitRollState unit) {
        if (unit.pipeline.rollType != CombatRollType.combatround || unit.pipeline.isThalnosReroll) return;
        boolean hasBoost = unit.pipeline.player.hasTech("tf-supercharge")
                || (unit.pipeline.player.hasUnlockedBreakthrough("letnevbt")
                        && "space".equalsIgnoreCase(unit.pipeline.unitHolder.getName()));
        String key = "highestValueSingleUnit" + unit.pipeline.player.getFaction();
        if (!hasBoost || !unit.pipeline.game.getStoredValue(key).equalsIgnoreCase(unit.unitModel.getAsyncId())) return;
        unit.singleUnitUse = new ArrayList<>(List.of("singleUnit", "RestOfUnits"));
        unit.pipeline.game.removeStoredValue(key);
    }

    private static void rollUnitSegments(UnitRollState unit) {
        for (String segmentName : unit.singleUnitUse) {
            if (!prepareUnitRollSegment(unit, segmentName)) continue;
            resolveJolNarFlagshipExtraHits(unit);
            resolveTeklarEliteExtraHits(unit);
            resolveZephyrionCommanderExtraHits(unit);
            resolveDragonFreedBombardment(unit);
            resolveSigmaJolNarFlagshipDice(unit);
            resolveValorExtraHits(unit);
            resolveVadenFlagshipTradeGood(unit);
            resolveUzeanWardogAbility(unit);
            recordPrimaryRollTotals(unit);
            resolveThalnosMisses(unit);
            publishPrimaryUnitRoll(unit);
            activateJusticerGraviton(unit);
            resolveJolNarCommanderRerolls(unit);
            resolveIronCommanderRerolls(unit);
            offerGledgePdsExploration(unit);
            resolveInitialKaltrimCommanderRerolls(unit);
            resolveMunitionsReservesReroll(unit);
            resolvePostMunitionsKaltrimCommanderRerolls(unit);
            resolveStrikeWingAlphaInfantryKills(unit);
            rewardMercenaryCaptains(unit);
            accumulateNearMisses(unit);
        }
    }

    private static boolean prepareUnitRollSegment(UnitRollState unit, String segmentName) {
        unit.numOfUnit = unit.ogNumOfUnit;
        unit.modifierToHit = unit.baseModifierToHit;
        int dice = (unit.ogNumOfUnit * unit.numRollsPerUnit) + unit.extraRollsForUnit;
        if ("singleUnit".equals(segmentName)) {
            dice = unit.numRollsPerUnit + Math.min(1, unit.extraRollsForUnit);
            unit.modifierToHit += unit.pipeline.letnevBTBoost;
            unit.numOfUnit = 1;
        } else if ("RestOfUnits".equals(segmentName)) {
            unit.numOfUnit = unit.ogNumOfUnit - 1;
            dice -= unit.numRollsPerUnit + Math.min(1, unit.extraRollsForUnit);
        }
        if (dice == 0) return false;
        unit.segmentType = switch (segmentName) {
            case "singleUnit" ->
                unit.pipeline.player.hasTech("tf-supercharge")
                        ? RollSegmentType.SUPERCHARGE_SELECTED_UNIT
                        : RollSegmentType.GRAVLEASH_SELECTED_UNIT;
            case "RestOfUnits" ->
                unit.pipeline.player.hasTech("tf-supercharge")
                        ? RollSegmentType.SUPERCHARGE_REST
                        : RollSegmentType.GRAVLEASH_REST;
            default -> RollSegmentType.PRIMARY;
        };
        unit.resultRolls = DiceHelper.rollDice(unit.toHit - unit.modifierToHit, dice);
        unit.pipeline.player.setExpectedHitsTimes10(
                unit.pipeline.player.getExpectedHitsTimes10() + (dice * (11 - unit.toHit + unit.modifierToHit)));
        unit.pipeline.chanceOfAllHits *= Math.pow((11 - unit.toHit + unit.modifierToHit) / 10.0, dice);
        unit.pipeline.chanceOfAllMiss *= Math.pow((unit.toHit - unit.modifierToHit - 1) / 10.0, dice);
        unit.pipeline.maximumHits += dice;
        unit.numRolls = dice;
        unit.multiplier = unit.pipeline.usesX89c4 ? 2 : 1;
        unit.hitRolls = DiceHelper.countSuccesses(unit.resultRolls);
        unit.secondaryRolls = new ArrayList<>();
        unit.numMisses = 0;
        unit.maximumHits = unit.pipeline.maximumHits;
        unit.chanceOfAllHits = unit.pipeline.chanceOfAllHits;
        return true;
    }

    private static void resolveJolNarFlagshipExtraHits(UnitRollState unit) {
        if (unit.unitModel.getUnitType() != UnitType.Flagship
                || !ValefarZService.hasFlagshipAbility(unit.pipeline.game, unit.pipeline.player, "jolnar_flagship"))
            return;
        unit.chanceOfAllHits *= Math.pow(2.0 / (11 - unit.toHit + unit.modifierToHit), unit.numRolls * unit.multiplier);
        for (Die die : unit.resultRolls) {
            if (die.getResult() >= 9) unit.hitRolls += 2;
            unit.maximumHits += 2;
        }
    }

    private static void resolveTeklarEliteExtraHits(UnitRollState unit) {
        if (unit.unitModel.getUnitType() != UnitType.Infantry || !unit.pipeline.player.hasUnit("tk-tekklarelite"))
            return;
        unit.chanceOfAllHits *= Math.pow(2.0 / (11 - unit.toHit + unit.modifierToHit), unit.numRolls * unit.multiplier);
        for (Die die : unit.resultRolls) {
            if (die.isSuccess()) unit.hitRolls++;
            unit.maximumHits++;
        }
    }

    private static void resolveZephyrionCommanderExtraHits(UnitRollState unit) {
        if ((unit.pipeline.rollType != CombatRollType.SpaceCannonDefence
                        && unit.pipeline.rollType != CombatRollType.SpaceCannonOffence)
                || !unit.pipeline.game.playerHasLeaderUnlockedOrAlliance(unit.pipeline.player, "zephyrioncommander"))
            return;
        for (Die die : unit.resultRolls) {
            if (die.getResult() == 10) unit.hitRolls++;
            unit.maximumHits++;
        }
    }

    private static void recordPrimaryRollTotals(UnitRollState unit) {
        unit.pipeline.maximumHits = unit.maximumHits;
        unit.pipeline.chanceOfAllHits = unit.chanceOfAllHits;
        unit.numMisses = unit.numRolls - unit.hitRolls;
        unit.pipeline.totalMisses += unit.numMisses;
        unit.pipeline.totalHits += unit.hitRolls;
    }

    private static void publishPrimaryUnitRoll(UnitRollState unit) {
        String holderLabel = unit.pipeline.divergingModels.contains(unit.unitModel.getId())
                        && unit.perUnitHolder instanceof Planet planet
                ? "on **" + Helper.getPlanetRepresentationNoResInf(planet.getName(), unit.pipeline.game) + "**"
                : "";
        String unitRoll = CombatMessageHelper.displayUnitRoll(
                unit.unitModel,
                unit.toHit,
                unit.modifierToHit,
                unit.numOfUnit,
                unit.numRollsPerUnit,
                unit.extraRollsForUnit,
                unit.resultRolls,
                unit.hitRolls,
                holderLabel);
        unit.pipeline.resultBuilder.append(unitRoll);
        unit.pipeline.payloadBuilder.addUnitRoll(
                unit.unitModel,
                unit.toHit,
                unit.modifierToHit,
                unit.numOfUnit,
                unit.numRollsPerUnit,
                unit.extraRollsForUnit,
                unit.segmentType,
                unit.resultRolls,
                unit.hitRolls,
                DieRollSource.PRIMARY);
    }

    private static void activateJusticerGraviton(UnitRollState unit) {
        if (!unit.pipeline.player.ownsUnit("tf-justicerrail")
                || unit.pipeline.rollType != CombatRollType.SpaceCannonOffence) return;
        unit.pipeline.game.setStoredValue(unit.pipeline.player.getFaction() + "graviton", "yes");
    }

    private static void resolveJolNarCommanderRerolls(UnitRollState unit) {
        if ((!unit.pipeline.game.playerHasLeaderUnlockedOrAlliance(unit.pipeline.player, "jolnarcommander")
                        && !unit.pipeline.player.hasTech("tf-tacticalbrilliance"))
                || unit.pipeline.rollType == CombatRollType.combatround) return;

        boolean rerollBombardmentHits = unit.pipeline.opponent == unit.pipeline.player
                && unit.pipeline.rollType == CombatRollType.bombardment
                && unit.pipeline.player.hasTech("proxima");
        int diceToReroll = rerollBombardmentHits ? unit.hitRolls : unit.numMisses;
        if (diceToReroll < 1) return;

        unit.secondaryRolls = DiceHelper.rollDice(unit.toHit - unit.modifierToHit, diceToReroll);
        if (rerollBombardmentHits) {
            unit.resultRolls.removeIf(Die::isSuccess);
        } else {
            unit.resultRolls.removeIf(Predicate.not(Die::isSuccess));
            unit.pipeline.chanceOfAllHits *= Math.pow((11 - unit.toHit + unit.modifierToHit) / 10.0, diceToReroll);
            unit.pipeline.chanceOfAllMiss *= Math.pow((unit.toHit - unit.modifierToHit - 1) / 10.0, diceToReroll);
            unit.pipeline.maximumHits += unit.numRolls * unit.multiplier;
        }
        unit.pipeline.player.setExpectedHitsTimes10(unit.pipeline.player.getExpectedHitsTimes10()
                + (diceToReroll * (11 - unit.toHit + unit.modifierToHit)));
        int rerollHits = DiceHelper.countSuccesses(unit.secondaryRolls);
        unit.pipeline.totalHits += rerollHits;
        if (rerollBombardmentHits) unit.pipeline.totalHits -= unit.hitRolls;

        int displayedExtraRolls = rerollBombardmentHits ? unit.extraRollsForUnit : 0;
        RollSegmentType segmentType = rerollBombardmentHits
                ? RollSegmentType.JOL_NAR_COMMANDER_REROLL_HITS
                : RollSegmentType.JOL_NAR_COMMANDER_REROLL_MISSES;
        DieRollSource rollSource = rerollBombardmentHits ? DieRollSource.REROLL_HIT : DieRollSource.REROLL_MISS;
        String unitRoll = CombatMessageHelper.displayUnitRoll(
                unit.unitModel,
                unit.toHit,
                unit.modifierToHit,
                unit.numOfUnit,
                unit.numRollsPerUnit,
                displayedExtraRolls,
                unit.secondaryRolls,
                rerollHits);
        unit.pipeline.payloadBuilder.addUnitRoll(
                unit.unitModel,
                unit.toHit,
                unit.modifierToHit,
                unit.numOfUnit,
                unit.numRollsPerUnit,
                displayedExtraRolls,
                segmentType,
                unit.secondaryRolls,
                rerollHits,
                rollSource);
        unit.pipeline
                .resultBuilder
                .append("Rerolling ")
                .append(diceToReroll)
                .append(rerollBombardmentHits ? " hit" : " miss")
                .append(diceToReroll == 1 ? "" : rerollBombardmentHits ? "s" : "es")
                .append(" due to Ta Zern, the Jol-Nar Commander:\n")
                .append(unitRoll);
    }

    private static void offerGledgePdsExploration(UnitRollState unit) {
        if (unit.pipeline.rollType != CombatRollType.SpaceCannonOffence
                && unit.pipeline.rollType != CombatRollType.SpaceCannonDefence) return;

        if (unit.pipeline.player.ownsUnit("gledge_pds2") && unit.pipeline.totalHits > 0) {
            String message = unit.pipeline.player.getRepresentation()
                    + ", use the buttons to explore a planet with the PDS that got the hit. It should be noted that the bot has no idea which PDS rolled which dice, but default practice would be to go from lowest tile position to highest, with _Plasma Scoring_ applying to the last die. You can specify any order before rolling though.";
            for (int hit = 0; hit < unit.pipeline.totalHits; hit++) {
                List<Button> buttons = new ArrayList<>();
                for (Tile tile : CheckUnitContainmentService.getTilesContainingPlayersUnits(
                        unit.pipeline.game, unit.pipeline.player, UnitType.Pds)) {
                    for (String planet : ButtonHelper.getPlanetsWithSpecificUnit(unit.pipeline.player, tile, "pds")) {
                        Planet planetUnit = unit.pipeline.game.getUnitHolderFromPlanet(planet);
                        if (planetUnit == null) continue;
                        planet = planetUnit.getName();
                        if (isNotBlank(planetUnit.getOriginalPlanetType())
                                && unit.pipeline.player.getPlanetsAllianceMode().contains(planet)
                                && FoWHelper.playerHasUnitsOnPlanet(unit.pipeline.player, tile, planet)) {
                            buttons.addAll(ButtonHelper.getPlanetExplorationButtons(
                                    unit.pipeline.game, planetUnit, unit.pipeline.player));
                        }
                    }
                }
                buttons.add(Buttons.red("deleteButtons", "No Valid Exploration"));
                MessageHelper.sendMessageToChannelWithButtons(
                        unit.pipeline.player.getCorrectChannel(), message, buttons);
            }
        }

        if (!unit.pipeline.player.ownsUnit("gledge_pds")) return;
        String message = unit.pipeline.player.getRepresentation()
                + " use the buttons to explore a planet with the PDS that got the hit.";
        for (Die die : unit.resultRolls) {
            if (die.getResult() < 9) continue;
            List<Button> buttons = new ArrayList<>();
            for (String planet :
                    ButtonHelper.getPlanetsWithSpecificUnit(unit.pipeline.player, unit.pipeline.activeSystem, "pds")) {
                Planet planetUnit = unit.pipeline.game.getUnitHolderFromPlanet(planet);
                if (planetUnit == null) continue;
                planet = planetUnit.getName();
                if (isNotBlank(planetUnit.getOriginalPlanetType())
                        && unit.pipeline.player.getPlanetsAllianceMode().contains(planet)
                        && FoWHelper.playerHasUnitsOnPlanet(unit.pipeline.player, unit.pipeline.activeSystem, planet)) {
                    buttons.addAll(ButtonHelper.getPlanetExplorationButtons(
                            unit.pipeline.game, planetUnit, unit.pipeline.player));
                }
            }
            buttons.add(Buttons.red("deleteButtons", "No Valid Exploration"));
            MessageHelper.sendMessageToChannelWithButtons(unit.pipeline.player.getCorrectChannel(), message, buttons);
        }
    }

    private static void resolveIronCommanderRerolls(UnitRollState unit) {
        if (!IronLeadersHandler.shouldAutoRerollCommanderMechMisses(
                        unit.pipeline.game, unit.pipeline.player, unit.unitModel, unit.pipeline.rollType)
                || unit.numMisses < 1) return;
        unit.secondaryRolls = DiceHelper.rollDice(unit.toHit - unit.modifierToHit, unit.numMisses);
        unit.resultRolls.removeIf(Predicate.not(Die::isSuccess));
        unit.pipeline.player.setExpectedHitsTimes10(unit.pipeline.player.getExpectedHitsTimes10()
                + (unit.numMisses * (11 - unit.toHit + unit.modifierToHit)));
        unit.pipeline.chanceOfAllHits *= Math.pow((11 - unit.toHit + unit.modifierToHit) / 10.0, unit.numMisses);
        unit.pipeline.chanceOfAllMiss *= Math.pow((unit.toHit - unit.modifierToHit - 1) / 10.0, unit.numMisses);
        unit.pipeline.maximumHits += unit.numRolls * unit.multiplier;
        int rerollHits = DiceHelper.countSuccesses(unit.secondaryRolls);
        unit.pipeline.totalHits += rerollHits;
        String unitRoll = CombatMessageHelper.displayUnitRoll(
                unit.unitModel,
                unit.toHit,
                unit.modifierToHit,
                unit.numOfUnit,
                unit.numRollsPerUnit,
                0,
                unit.secondaryRolls,
                rerollHits);
        unit.pipeline.payloadBuilder.addUnitRoll(
                unit.unitModel,
                unit.toHit,
                unit.modifierToHit,
                unit.numOfUnit,
                unit.numRollsPerUnit,
                0,
                RollSegmentType.IRON_COMMANDER_REROLL_MISSES,
                unit.secondaryRolls,
                rerollHits,
                DieRollSource.REROLL_MISS);
        unit.pipeline
                .resultBuilder
                .append("Rerolling ")
                .append(unit.numMisses)
                .append(" miss")
                .append(unit.numMisses == 1 ? "" : "es")
                .append(" due to Captain Vakros, the Iron Tide Commander:\n")
                .append(unitRoll);
        unit.resultRolls.addAll(unit.secondaryRolls);
        unit.numMisses -= rerollHits;
        unit.secondaryRolls = new ArrayList<>();
    }

    private static void resolveThalnosMisses(UnitRollState unit) {
        if (!unit.pipeline.isThalnosReroll) return;
        if (unit.pipeline.hacanFlagship) {
            unit.numMisses -= (int)
                    unit.resultRolls.stream().filter(Die::eligibleForHeartPlus).count();
            unit.pipeline.hacanFsButtons.add(buildHacanFlagshipThalnosButton(
                    unit.pipeline.player, unit.unitModel.getUnitType(), unit.resultRolls));
        } else if (unit.pipeline.tkHacanWarsun) {
            unit.numMisses = 0;
            unit.pipeline.hacanFsButtons.add(buildTkHacanWSThalnosButton(unit.resultRolls));
        }
        if ((unit.pipeline.hacanFlagship || unit.pipeline.tkHacanWarsun) && !unit.extraRollsCount) {
            unit.pipeline.hacanFsThalnosDestroyTypes.add(unit.unitModel.getUnitType());
        }
        if (unit.numMisses > 0 && !unit.extraRollsCount) {
            unit.pipeline
                    .extra
                    .append(unit.pipeline.player.getFactionEmoji())
                    .append(" destroyed ")
                    .append(unit.numMisses)
                    .append(" of their own ")
                    .append(unit.unitModel.getName())
                    .append(unit.numMisses == 1 ? "" : "s")
                    .append(" due to ")
                    .append(unit.numMisses == 1 ? "a Thalnos miss" : "Thalnos misses")
                    .append(".");
            unit.pipeline.delayedAfterTotalNotes.add(new CombatRollPayload.CombatRollNote(
                    CombatRollNoteType.UNIT_DESTROYED_FROM_ROLL,
                    CombatRollNotePlacement.AFTER_TOTAL,
                    "thalnos",
                    unit.unitModel.getId(),
                    unit.numMisses,
                    Map.of(
                            "actorEmoji",
                            unit.pipeline.player.getFactionEmoji(),
                            "unitName",
                            unit.unitModel.getName())));
            thalnosUnits(
                    unit.pipeline.event,
                    unit.pipeline.game,
                    unit.pipeline.player,
                    unit.numMisses,
                    unit.unitModel.getUnitType());
        } else if (unit.numMisses > 0) {
            MessageHelper.sendMessageToChannel(
                    unit.pipeline.event.getMessageChannel(),
                    unit.pipeline.player.getFactionEmoji() + " had " + unit.numMisses + " "
                            + unit.unitModel.getName() + (unit.numMisses == 1 ? "" : "s") + " miss"
                            + (unit.numMisses == 1 ? "" : "es")
                            + " on a Thalnos roll, but no units were removed due to extra rolls being unaccounted for.");
        }
    }

    private static void resolveStrikeWingAlphaInfantryKills(UnitRollState unit) {
        if (unit.pipeline.player == unit.pipeline.opponent
                || (!("argent_destroyer2".equalsIgnoreCase(unit.unitModel.getId())
                        || "tf-swa".equalsIgnoreCase(unit.unitModel.getId())))
                || unit.pipeline.rollType != CombatRollType.AFB) return;
        int availableInfantry =
                unit.pipeline.space.getUnitCount(Units.UnitType.Infantry, unit.pipeline.opponent.getColor());
        if (availableInfantry < 1) return;
        int infantryKills = (int) Stream.concat(unit.resultRolls.stream(), unit.secondaryRolls.stream())
                .filter(die -> die.getResult() > 8)
                .count();
        infantryKills = Math.min(infantryKills, availableInfantry);
        if (infantryKills < 1) return;
        unit.pipeline
                .resultBuilder
                .append("\nDue to the Strike Wing Alpha II destroyer ability, ")
                .append(infantryKills)
                .append(" of ")
                .append(unit.pipeline.opponent.getRepresentation(false, true))
                .append(" infantry were destroyed\n");
        unit.pipeline.payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                CombatRollNoteType.OPPONENT_UNIT_DESTROYED_FROM_ROLL,
                CombatRollNotePlacement.AFTER_UNIT_ROLLS,
                unit.unitModel.getId(),
                "infantry",
                infantryKills,
                Map.of("opponent", unit.pipeline.opponent.getRepresentation(false, true))));
        UnitKey infantry = Units.getUnitKey(UnitType.Infantry, unit.pipeline.opponent.getColorID());
        DestroyUnitService.destroyUnit(
                unit.pipeline.event,
                unit.pipeline.activeSystem,
                unit.pipeline.game,
                infantry,
                infantryKills,
                unit.pipeline.space,
                true);
    }

    private static void rewardMercenaryCaptains(UnitRollState unit) {
        if (unit.pipeline.totalHits < 1
                || !"neutral".equalsIgnoreCase(unit.pipeline.player.getFaction())
                || !unit.pipeline.game.getStoredValue("mercenarycaptaintrigged").isEmpty()) return;
        for (Player player : unit.pipeline.game.getRealPlayers()) {
            if (!player.hasTech("tf-mercenarycaptains")) continue;
            player.setCommodities(player.getCommodities() + 1);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " you gained 1 commodity due to the mercenary captains unit.");
            unit.pipeline.game.setStoredValue("mercenarycaptaintrigged", "yes");
        }
    }

    private static void accumulateNearMisses(UnitRollState unit) {
        unit.pipeline.nearMisses += (int) IterableUtils.countMatches(unit.resultRolls, Die::eligibleForHeartPlus);
        unit.pipeline.nearMisses += (int) IterableUtils.countMatches(unit.secondaryRolls, Die::eligibleForHeartPlus);
    }

    private static void resolveMunitionsReservesReroll(UnitRollState unit) {
        if (!unit.pipeline.game.getStoredValue("munitionsReserves").equalsIgnoreCase(unit.pipeline.player.getFaction())
                || unit.pipeline.rollType != CombatRollType.combatround
                || unit.numMisses < 1
                || unit.pipeline.isThalnosReroll) return;
        unit.secondaryRolls = DiceHelper.rollDice(unit.toHit - unit.modifierToHit, unit.numMisses);
        unit.resultRolls.removeIf(Predicate.not(Die::isSuccess));
        unit.pipeline.player.setExpectedHitsTimes10(unit.pipeline.player.getExpectedHitsTimes10()
                + (unit.numMisses * (11 - unit.toHit + unit.modifierToHit)));
        unit.pipeline.chanceOfAllHits *= Math.pow((11 - unit.toHit + unit.modifierToHit) / 10.0, unit.numMisses);
        unit.pipeline.chanceOfAllMiss *= Math.pow((unit.toHit - unit.modifierToHit - 1) / 10.0, unit.numMisses);
        unit.pipeline.maximumHits += unit.numRolls * unit.multiplier;
        int rerollHits = DiceHelper.countSuccesses(unit.secondaryRolls);
        if (hasValorAbilityHolder(unit.pipeline.game)
                && ButtonHelperAgents.getGloryTokenTiles(unit.pipeline.game).contains(unit.pipeline.activeSystem)) {
            for (Die die : unit.secondaryRolls) {
                if (die.getResult() <= 9) continue;
                rerollHits++;
                MessageHelper.sendMessageToChannel(
                        unit.pipeline.event.getMessageChannel(),
                        unit.pipeline.player.getRepresentation()
                                + " got an extra hit due to the **Valor** ability (it has been accounted for in the hit count).");
            }
        }
        unit.pipeline.totalHits += rerollHits;
        String unitRoll = CombatMessageHelper.displayUnitRoll(
                unit.unitModel,
                unit.toHit,
                unit.modifierToHit,
                unit.numOfUnit,
                unit.numRollsPerUnit,
                0,
                unit.secondaryRolls,
                rerollHits);
        unit.pipeline.payloadBuilder.addUnitRoll(
                unit.unitModel,
                unit.toHit,
                unit.modifierToHit,
                unit.numOfUnit,
                unit.numRollsPerUnit,
                0,
                RollSegmentType.MUNITIONS_RESERVES_REROLL,
                unit.secondaryRolls,
                rerollHits,
                DieRollSource.MUNITIONS_RESERVES);
        unit.pipeline
                .resultBuilder
                .append("**Munitions Reserve** rerolling ")
                .append(unit.numMisses)
                .append(" miss")
                .append(unit.numMisses == 1 ? "" : "es")
                .append(": ")
                .append(unitRoll);
        unit.resultRolls.addAll(unit.secondaryRolls);
        unit.secondaryRolls.clear();
    }

    private static void resolveInitialKaltrimCommanderRerolls(UnitRollState unit) {
        if (!unit.pipeline.game.playerHasLeaderUnlockedOrAlliance(unit.pipeline.player, "kaltrimcommander")) return;
        int ones = (int)
                unit.resultRolls.stream().filter(die -> die.getResult() == 1).count();
        if (ones < 1) return;
        unit.secondaryRolls = DiceHelper.rollDice(unit.toHit - unit.modifierToHit, ones);
        unit.pipeline.player.setExpectedHitsTimes10(
                unit.pipeline.player.getExpectedHitsTimes10() + (ones * (11 - unit.toHit + unit.modifierToHit)));
        int rerollHits = DiceHelper.countSuccesses(unit.secondaryRolls);
        unit.pipeline.totalHits += rerollHits;
        String unitRoll = CombatMessageHelper.displayUnitRoll(
                unit.unitModel,
                unit.toHit,
                unit.modifierToHit,
                unit.numOfUnit,
                unit.numRollsPerUnit,
                0,
                unit.secondaryRolls,
                rerollHits);
        unit.pipeline.payloadBuilder.addUnitRoll(
                unit.unitModel,
                unit.toHit,
                unit.modifierToHit,
                unit.numOfUnit,
                unit.numRollsPerUnit,
                0,
                RollSegmentType.KALTRIM_COMMANDER_REROLL_ONES,
                unit.secondaryRolls,
                rerollHits,
                DieRollSource.REROLL_ONE);
        unit.pipeline
                .resultBuilder
                .append("Rerolling ")
                .append(ones)
                .append(" roll")
                .append(ones == 1 ? "" : "s")
                .append(" of 1 due to the Kaltrim Commander:\n ")
                .append(unitRoll);
    }

    private static void resolvePostMunitionsKaltrimCommanderRerolls(UnitRollState unit) {
        if (!unit.pipeline.game.playerHasLeaderUnlockedOrAlliance(unit.pipeline.player, "kaltrimcommander")) return;
        int ones = (int)
                unit.resultRolls.stream().filter(die -> die.getResult() == 1).count();
        unit.resultRolls.removeIf(die -> die.getResult() == 1);
        if (ones < 1) return;
        unit.secondaryRolls = DiceHelper.rollDice(unit.toHit - unit.modifierToHit, ones);
        unit.pipeline.player.setExpectedHitsTimes10(
                unit.pipeline.player.getExpectedHitsTimes10() + (ones * (11 - unit.toHit + unit.modifierToHit)));
        int rerollHits = DiceHelper.countSuccesses(unit.secondaryRolls);
        unit.pipeline.totalHits += rerollHits;
        String unitRoll = CombatMessageHelper.displayUnitRoll(
                unit.unitModel,
                unit.toHit,
                unit.modifierToHit,
                unit.numOfUnit,
                unit.numRollsPerUnit,
                0,
                unit.secondaryRolls,
                rerollHits);
        unit.pipeline
                .resultBuilder
                .append("Rerolling ")
                .append(ones)
                .append(" roll")
                .append(ones == 1 ? "" : "s")
                .append(" of 1 due to the Kaltrim Commander:\n ")
                .append(unitRoll);
    }

    private static void resolveDragonFreedBombardment(UnitRollState unit) {
        UnitRollPipelineState state = unit.pipeline;
        if (state.rollType != CombatRollType.bombardment
                || !"tf-dragonfreed".equalsIgnoreCase(unit.unitModel.getId())
                || state.game.isFowMode()
                || unit.hitRolls < 1) return;
        String target = state.game.getStoredValue("bombardmentTarget" + state.player.getFaction());
        Tile origin = target.isEmpty()
                ? state.game.getTileByPosition(state.game.getActiveSystem())
                : state.game.getTileFromPlanet(target);
        for (String position :
                FoWHelper.getAdjacentTiles(state.game, origin.getPosition(), state.player, false, true)) {
            offerDragonBombardmentAssignments(unit, state.game.getTileByPosition(position), target);
        }
    }

    private static void offerDragonBombardmentAssignments(UnitRollState unit, Tile tile, String excludedPlanet) {
        UnitRollPipelineState state = unit.pipeline;
        for (UnitHolder holder : tile.getPlanetUnitHolders()) {
            if (holder.getName().equalsIgnoreCase(excludedPlanet)) continue;
            for (Player target : state.game.getRealPlayersNNeutral()) {
                if (!FoWHelper.playerHasUnitsOnPlanet(target, holder)) continue;
                List<Button> buttons = target.isRealPlayer()
                        ? List.of(Buttons.red(
                                "getDamageButtons_" + tile.getPosition() + "_bombardment",
                                "Assign Hit" + (unit.hitRolls == 1 ? "" : "s")))
                        : List.of(Buttons.green(
                                target.dummyPlayerSpoof() + "autoAssignGroundHits_" + holder.getName() + "_"
                                        + unit.hitRolls,
                                "Auto-assign Hit" + (unit.hitRolls == 1 ? "" : "s") + " For Dummy"));
                String message = (target.isRealPlayer() ? target.getRepresentation() : state.player.getRepresentation())
                        + ", please assign the Dragon BOMBARDMENT hit" + (unit.hitRolls == 1 ? "" : "s")
                        + (target.isRealPlayer() ? " on " : " for the dummy player on ")
                        + Helper.getPlanetRepresentation(holder.getName(), state.game) + ".";
                MessageHelper.sendMessageToChannelWithButtons(state.event.getMessageChannel(), message, buttons);
            }
        }
    }

    private static void resolveSigmaJolNarFlagshipDice(UnitRollState unit) {
        String id = unit.unitModel.getId();
        if (!"sigma_jolnar_flagship_1".equalsIgnoreCase(id) && !"sigma_jolnar_flagship_2".equalsIgnoreCase(id)) return;
        int additionalDice = unit.hitRolls;
        while (unit.hitRolls < 100 && additionalDice > 0) {
            List<Die> rolls = DiceHelper.rollDice(unit.toHit - unit.modifierToHit, additionalDice);
            additionalDice = DiceHelper.countSuccesses(rolls);
            unit.hitRolls += additionalDice;
            unit.resultRolls.addAll(rolls);
        }
    }

    private static void resolveValorExtraHits(UnitRollState unit) {
        Player gloryHolder = Helper.getPlayerFromAbility(unit.pipeline.game, "valor");
        if (gloryHolder == null) {
            gloryHolder = unit.pipeline.game.getRealPlayers().stream()
                    .filter(player -> player.hasTech("tf-glorioushalls"))
                    .findFirst()
                    .orElse(null);
        }
        boolean systemValor = unit.pipeline.rollType == CombatRollType.combatround
                && gloryHolder != null
                && ButtonHelperAgents.getGloryTokenTiles(unit.pipeline.game).contains(unit.pipeline.activeSystem);
        List<String> valorAbilities = new ArrayList<>();
        if (systemValor) {
            ButtonHelperAbilities.readyBannerHalls(unit.pipeline.game);
            valorAbilities.add(unit.pipeline.game.isTwilightsFallMode() ? "Glorious Halls" : "Valor");
        }
        if (unit.pipeline.player.hasTech("tf-valortf")) valorAbilities.add("Valor");

        for (String abilityName : valorAbilities) {
            unit.chanceOfAllHits *=
                    Math.pow(1.0 / (11 - unit.toHit + unit.modifierToHit), unit.numRolls * unit.multiplier);
            for (Die die : unit.resultRolls) {
                if (die.getResult() >= 10) {
                    unit.hitRolls++;
                    MessageHelper.sendMessageToChannel(
                            unit.pipeline.event.getMessageChannel(),
                            unit.pipeline.player.getRepresentation() + " got an extra hit due to the **" + abilityName
                                    + "** ability (it has been accounted for in the hit count).");
                }
                unit.maximumHits++;
            }
        }
    }

    private static boolean hasValorAbilityHolder(Game game) {
        return Helper.getPlayerFromAbility(game, "valor") != null
                || game.getRealPlayers().stream().anyMatch(player -> player.hasTech("tf-glorioushalls"));
    }

    private static void resolveVadenFlagshipTradeGood(UnitRollState unit) {
        UnitRollPipelineState state = unit.pipeline;
        if (!"vaden_flagship".equalsIgnoreCase(unit.unitModel.getId())
                || state.rollType != CombatRollType.bombardment
                || unit.resultRolls.stream().noneMatch(die -> die.getResult() > 4)) return;
        state.player.setTg(state.player.getTg() + 1);
        ButtonHelperAbilities.pillageCheck(state.player, state.game);
        ButtonHelperAgents.resolveArtunoCheck(state.player, 1);
        MessageHelper.sendMessageToChannel(
                state.player.getCorrectChannel(),
                state.player.getRepresentation()
                        + " gained 1 trade good due to hitting on a BOMBARDMENT roll with the Aurum Vadra (the Vaden flagship).");
    }

    private static void resolveUzeanWardogAbility(UnitRollState unit) {
        if (!"belkosea_mech".equalsIgnoreCase(unit.unitModel.getId()) || unit.hitRolls < 1) return;
        ButtonHelperFactionSpecific.offerMahactInfButtons(unit.pipeline.player, unit.pipeline.game);
        MessageHelper.sendMessageToChannel(
                unit.pipeline.event.getMessageChannel(),
                unit.pipeline.player.getRepresentation() + " please gain or convert 1 commodity a total of "
                        + StringHelper.pluralize(unit.hitRolls, "time")
                        + " due to your Uzean Wardog mech unit.");
    }

    private static void prepareSingleUnitRollBoost(UnitRollPipelineState state) {
        int boost = 0;
        String highestValueSingleUnitKey = "highestValueSingleUnit" + state.player.getFaction();
        String storedHighestValueUnit = state.game.getStoredValue(highestValueSingleUnitKey);
        boolean unitUndecided = storedHighestValueUnit.isEmpty()
                || state.playerUnits.keySet().stream()
                        .noneMatch(k -> k.getLeft().getAsyncId().equalsIgnoreCase(storedHighestValueUnit));
        if (!storedHighestValueUnit.isEmpty() && unitUndecided) {
            // A manual Gravleash/Supercharge choice (chooseGravleash_) that isn't part of this combat
            // round - wrong tile, or the chosen unit has since died/retreated - would otherwise block
            // auto-pick forever, since this flag never becomes true again once set.
            state.game.removeStoredValue(highestValueSingleUnitKey);
        }
        if (state.rollType == CombatRollType.combatround
                && (state.player.hasTech("tf-supercharge")
                        || (state.player.hasUnlockedBreakthrough("letnevbt")
                                && "space".equalsIgnoreCase(state.unitHolder.getName())))) {
            int max = 0;
            for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : state.playerUnits.entrySet()) {
                UnitModel unitModel = entry.getKey().getLeft();
                UnitHolder perUnitHolder = entry.getKey().getRight();
                int numOfUnit = entry.getValue();
                int extraRollsForUnit = CombatModHelper.getCombinedModifierForUnit(
                        unitModel,
                        numOfUnit,
                        state.extraRolls,
                        state.player,
                        state.opponent,
                        state.game,
                        state.playerUnitsList,
                        CombatRollType.combatround,
                        state.activeSystem,
                        perUnitHolder);
                unitModel.getCombatDieCountForAbility(CombatRollType.combatround, state.player);
                int numRollsPerUnit;
                CombatStatsService.CombatRoundProfile combatRoundProfile = CombatStatsService.getCombatRoundProfile(
                        true, unitModel, state.player, state.activeSystem, state.opponent, false);
                numRollsPerUnit = combatRoundProfile.diceCount();
                if (numRollsPerUnit + Math.min(1, extraRollsForUnit) > max && unitUndecided) {
                    max = numRollsPerUnit + Math.min(1, extraRollsForUnit);
                    state.game.setStoredValue(
                            "highestValueSingleUnit" + state.player.getFaction(), unitModel.getAsyncId());
                }
                if (state.player.hasUnlockedBreakthrough("letnevbt") && unitModel.getIsShip()) {
                    boost++;
                }
            }
            if (state.player.hasTech("tf-supercharge")) {
                state.resultBuilder.append("Applied +2 to the rolls of 1 unit with _Supercharge_.\n");
                state.payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                        CombatRollNoteType.SINGLE_UNIT_ROLL_MOD_APPLIED,
                        CombatRollNotePlacement.BEFORE_UNIT_ROLLS,
                        "tf-supercharge",
                        state.game.getStoredValue("highestValueSingleUnit" + state.player.getFaction()),
                        1,
                        Map.of("modifier", "2")));
                boost = 2;
            } else {
                state.resultBuilder
                        .append("Applied +")
                        .append(boost)
                        .append(" to the rolls of 1 unit with _Gravleash Maneuvers_.\n");
                state.payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                        CombatRollNoteType.SINGLE_UNIT_ROLL_MOD_APPLIED,
                        CombatRollNotePlacement.BEFORE_UNIT_ROLLS,
                        "letnevbt",
                        state.game.getStoredValue("highestValueSingleUnit" + state.player.getFaction()),
                        1,
                        Map.of("modifier", Integer.toString(boost))));
            }
        }

        state.letnevBTBoost = boost;
    }

    private static void recordRollStatistics(UnitRollPipelineState state) {
        state.player.setActualHits(state.player.getActualHits() + state.totalHits);
        if (state.chanceOfAllHits <= 2.0 && state.totalHits == state.maximumHits) {
            state.game.setStoredValue("surprisingDiceRoll", "hits");
        } else if (state.chanceOfAllMiss <= 2.0 && state.totalHits == 0) {
            state.game.setStoredValue("surprisingDiceRoll", "miss");
        } else {
            state.game.setStoredValue("surprisingDiceRoll", "none");
        }
        state.whiff = state.maximumHits > 0 && state.totalHits == 0;
        state.slam = state.maximumHits > 0 && state.totalHits == state.maximumHits;
    }

    private static void applyHitMultipliers(UnitRollPipelineState state) {
        if (state.usesX89c4) state.totalHits *= 2;
        if (state.game.isConventionsOfWarAbandonedMode() && state.rollType == CombatRollType.bombardment) {
            state.totalHits *= 3;
        }
        state.useDoubleBoomEmoji = state.usesX89c4;
        if (state.player.hasStoredValue("RazeFaction") && state.rollType == CombatRollType.bombardment) {
            state.useDoubleBoomEmoji = true;
            state.totalHits *= 2;
        }
        if (state.totalHits < 1) state.useDoubleBoomEmoji = false;
        if (state.totalHits > 0 && state.rollType == CombatRollType.bombardment && state.player.hasTech("dszelir"))
            state.totalHits++;
        if (state.totalHits > 0
                && state.rollType != CombatRollType.combatround
                && state.player.hasTech("tf-shardsaturation")) state.totalHits++;
    }

    private static void appendHitResults(UnitRollPipelineState state) {
        state.resultBuilder.append(CombatMessageHelper.displayHitResults(state.totalHits, state.useDoubleBoomEmoji));
    }

    private static void appendX89HitMessage(UnitRollPipelineState state) {
        if (state.totalHits < 1 || !state.usesX89c4) return;
        state.resultBuilder
                .append("\n")
                .append(state.player.getFactionEmoji())
                .append(" produced ")
                .append(StringHelper.pluralize(state.totalHits / 2, "additional hit"))
                .append(" using ")
                .append(Mapper.getTech("x89c4").getNameRepresentation())
                .append(".");
    }

    private static void offerHacanFlagshipRerolls(UnitRollPipelineState state) {
        if ((!state.hacanFlagship && !state.tkHacanWarsun) || state.nearMisses < 1 || state.isThalnosReroll) return;
        HacanFlagshipService.startHacanFlagshipNormal(
                state.event, state.game, state.player, state.activeSystem, state.nearMisses);
    }

    private static void appendThalnosRerollOffer(UnitRollPipelineState state) {
        if (!state.player.hasRelic("thalnos")
                || state.rollType != CombatRollType.combatround
                || state.totalMisses < 1
                || state.isThalnosReroll) return;
        state.resultBuilder
                .append("\n")
                .append(state.player.getFactionEmoji())
                .append(" You have _The Crown of Thalnos_ and may reroll ")
                .append(state.totalMisses == 1 ? "the miss" : "misses")
                .append(", adding +1, at the risk of your ")
                .append(state.totalMisses == 1 ? "troop's life" : "troops' lives")
                .append(".");
        state.payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                CombatRollNoteType.REROLL_AVAILABLE,
                CombatRollNotePlacement.AFTER_TOTAL,
                "thalnos",
                null,
                state.totalMisses,
                Map.of("actorEmoji", state.player.getFactionEmoji())));
    }

    private static void appendAdditionalHitMessages(UnitRollPipelineState state) {
        if (state.totalHits > 0 && state.rollType == CombatRollType.bombardment && state.player.hasTech("dszelir")) {
            state.resultBuilder
                    .append("\n")
                    .append(state.player.getFactionEmoji())
                    .append(" You have _Shard Volley_ and thus produced an additional hit to the ones rolled above.");
        }
        if (state.totalHits > 0
                && state.rollType != CombatRollType.combatround
                && state.player.hasTech("tf-shardsaturation")) {
            state.resultBuilder
                    .append("\n")
                    .append(state.player.getFactionEmoji())
                    .append(
                            " You have _Shard Saturation_ and thus produced an additional hit to the ones rolled above.");
        }
    }

    private static void appendDelayedRollNotes(UnitRollPipelineState state) {
        state.delayedAfterTotalNotes.forEach(state.payloadBuilder::addNote);
    }

    private static void appendExtraRollMessages(UnitRollPipelineState state) {
        if (!state.extra.isEmpty()) state.resultBuilder.append("\n\n").append(state.extra);
    }

    private static void clearMunitionsReserves(UnitRollPipelineState state) {
        if (state.game.getStoredValue("munitionsReserves").equalsIgnoreCase(state.player.getFaction())
                && state.rollType == CombatRollType.combatround) state.game.setStoredValue("munitionsReserves", "");
    }

    private static CombatRollResult buildCombatRollResult(UnitRollPipelineState state) {
        CombatRollPayload payload = state.payloadBuilder.build(state.totalHits, state.totalMisses, state.maximumHits);
        return new CombatRollResult(state.resultBuilder.toString(), state.totalHits, state.whiff, state.slam, payload);
    }

    private static void prepareRollModifiers(UnitRollPipelineState state) {
        PreparedModifiers prepared = prepareAndDisplayModifiers(state);
        state.mods = prepared.rollModifiers();
        state.resultBuilder.append(prepared.display());
    }

    private static void repairUnitsAtStartOfCombatRound(UnitRollPipelineState state) {
        String repairs = buildStartOfCombatRoundRepairs(state);
        state.resultBuilder.insert(0, repairs);
    }

    private static void mergeDivergingUnitModels(UnitRollPipelineState state) {
        MergeResult merged = mergeAndDetectDivergence(
                state.playerUnits,
                state.mods,
                state.rollType,
                state.player,
                state.opponent,
                state.game,
                state.playerUnitsList,
                state.activeSystem);
        state.playerUnits = merged.units();
        state.divergingModels = merged.divergingModels();
    }

    private static PreparedModifiers prepareAndDisplayModifiers(UnitRollPipelineState state) {
        Set<NamedCombatModifierModel> rollModifierSet = new HashSet<>(state.autoMods);
        rollModifierSet.addAll(state.tempMods);
        List<NamedCombatModifierModel> rollModifiers = new ArrayList<>(rollModifierSet);

        Set<NamedCombatModifierModel> displayedModifierSet = new HashSet<>(rollModifiers);
        displayedModifierSet.addAll(state.extraRolls);
        List<NamedCombatModifierModel> displayedModifiers = new ArrayList<>(displayedModifierSet);
        Map<UnitModel, Integer> playerUnitsFlat = new HashMap<>();
        state.playerUnits.forEach((unit, count) -> playerUnitsFlat.merge(unit.getLeft(), count, Integer::sum));
        String display =
                CombatMessageHelper.displayModifiers("With modifiers: \n", playerUnitsFlat, displayedModifiers);
        state.payloadBuilder.addModifierDisplays(
                displayedModifiers,
                playerUnitsFlat,
                state.player,
                state.opponent,
                state.game,
                state.rollType,
                state.activeSystem,
                state.unitHolder);
        return new PreparedModifiers(rollModifiers, display);
    }

    private static String buildStartOfCombatRoundRepairs(UnitRollPipelineState state) {
        String repairs = "";
        if (state.rollType == CombatRollType.combatround
                && ButtonHelper.doesPlayerHaveFSHere("letnev_flagship", state.player, state.activeSystem)
                && Constants.SPACE.equalsIgnoreCase(state.unitHolder.getName())
                && state.unitHolder.getDamagedUnitCount(UnitType.Flagship, state.player.getColorID()) > 0) {
            repairs = "Repaired the Arc Secundus at start of this combat round with its ability.\n" + repairs;
            addUnitRepairedNote(state.payloadBuilder, "letnev_flagship");
            state.activeSystem.removeUnitDamage(
                    state.unitHolder.getName(),
                    Mapper.getUnitKey(AliasHandler.resolveUnit("fs"), state.player.getColorID()),
                    1);
        }
        if (state.rollType == CombatRollType.combatround
                && state.player.ownsUnit("naaz_voltron")
                && Constants.SPACE.equalsIgnoreCase(state.unitHolder.getName())
                && state.unitHolder.getDamagedUnitCount(UnitType.Mech, state.player.getColorID()) > 0) {
            repairs = "The Eidolon Maximum self-repaired at the start of this combat round.\n" + repairs;
            addUnitRepairedNote(state.payloadBuilder, "naaz_voltron");
            state.activeSystem.removeUnitDamage(
                    state.unitHolder.getName(),
                    Mapper.getUnitKey(AliasHandler.resolveUnit("mf"), state.player.getColorID()),
                    1);
        }
        return repairs;
    }

    private static void addUnitRepairedNote(RollPayloadBuilder payloadBuilder, String unitId) {
        payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                CombatRollNoteType.UNIT_REPAIRED,
                CombatRollNotePlacement.BEFORE_MODIFIERS,
                unitId,
                unitId,
                1,
                Map.of("timing", "START_OF_COMBAT_ROUND")));
    }

    /** Builds a button with ID {@code FFCC_hacanFlagshipThalnos_<unittype>_X} where {@code X} is the number of units that can score a hit given a +1 */
    private Button buildHacanFlagshipThalnosButton(Player player, UnitType type, List<Die> results) {
        int amt = results.stream().filter(Die::eligibleForHeartPlus).toList().size();

        String id = player.factionButtonChecker() + "hacanFlagship_" + type.getValue() + "_" + amt;
        String label = " (" + amt + ")";
        return Buttons.green(id, label, type.getUnitTypeEmoji());
    }

    /** Builds a button with ID {@code FFCC_tkHacanWsThalnos_<unittype>_X,X,X,X,X,X,X,X,X,X} where _Xᵢ_ is the number of units that can rolled a result of _i_*/
    private Button buildTkHacanWSThalnosButton(List<Die> results) {
        return null;
    }

    private void thalnosUnits(
            GenericInteractionCreateEvent event, Game game, Player player, int misses, UnitType type) {
        for (String thalnosUnit : game.getThalnosUnits().keySet()) {
            String pos = thalnosUnit.split("_")[0];
            String unitHolderName = thalnosUnit.split("_")[1];
            Tile tile = game.getTileByPosition(pos);
            String unitName = type.plainName();
            thalnosUnit = thalnosUnit.split("_")[2].replace("damaged", "");
            if (thalnosUnit.equals(unitName)) {
                DestroyUnitService.destroyUnits(
                        event, tile, game, player.getColor(), misses + " " + unitName + " " + unitHolderName, true);
                break;
            }
        }
    }

    private CombatRollPayload.RollHeader buildRollHeader(
            Game game,
            Player player,
            Player opponent,
            Tile tile,
            UnitHolder combatOnHolder,
            CombatRollType rollType,
            String combatSummary) {
        String combatDisplayName = substringBetween(combatSummary, "rolls for ", " " + MiscEmojis.RollDice + " :");
        if (combatDisplayName == null) {
            combatDisplayName = substringBetween(combatSummary, "rolls for ", " :");
        }
        Integer combatRound = null;
        if (rollType == CombatRollType.combatround) {
            String combatName =
                    "combatRoundTracker" + player.getFaction() + tile.getPosition() + combatOnHolder.getName();
            if (!game.getStoredValue(combatName).isBlank()) {
                combatRound = Integer.parseInt(game.getStoredValue(combatName));
            }
        }
        boolean thalnosReroll = "true".equalsIgnoreCase(game.getStoredValue("thalnosPlusOne"));
        return new CombatRollPayload.RollHeader(
                player.getFaction(),
                player.getColor(),
                player.getFactionEmoji(),
                opponent == null ? null : opponent.getFaction(),
                opponent == null ? null : opponent.getColor(),
                tile.getPosition(),
                tile.getTileID(),
                combatOnHolder.getName(),
                combatDisplayName,
                rollType,
                combatRound,
                thalnosReroll,
                game.isFowMode());
    }

    private static Map<UnitModel, Integer> getCombatRoundUnits(
            Tile tile, UnitHolder unitHolder, Player player, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        Map<String, Integer> unitsByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        Map<UnitModel, Integer> output = CombatUnitSelectionHelper.collectCombatRoundUnits(tile, unitHolder, player);
        checkBadUnits(player, event, unitsByAsyncId, output);
        return output;
    }

    static Map<Pair<UnitModel, UnitHolder>, Integer> getUnitsInAFB(
            Tile tile, Player player, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        UnitHolder spaceHolder = tile.getUnitHolders().get("space");

        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        Map<Pair<UnitModel, UnitHolder>, Integer> output = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            Map<String, Integer> holderUnits = new HashMap<>();
            getUnitsOnHolderByAsyncId(colorID, holderUnits, unitHolder);
            holderUnits.forEach((k, v) -> unitsByAsyncId.merge(k, v, Integer::sum));
            for (var entry : holderUnits.entrySet()) {
                UnitModel model = player.getPriorityUnitByAsyncID(entry.getKey(), null);
                if (model != null && model.getAfbDieCount(player) > 0) {
                    output.merge(new ImmutablePair<>(model, unitHolder), entry.getValue(), Integer::sum);
                }
            }
        }
        if (player.hasUnit("iron_flagship")) {
            IronUnitsHandler.getIronFlagshipAfbUnits(player, tile)
                    .forEach((model, count) -> output.put(new ImmutablePair<>(model, spaceHolder), count));
        }
        Map<UnitModel, Integer> flatOutput = new HashMap<>();
        output.forEach((k, v) -> flatOutput.merge(k.getLeft(), v, Integer::sum));
        checkBadUnits(player, event, unitsByAsyncId, flatOutput);

        return output;
    }

    private static Map<UnitModel, Integer> getUnitsInCombat(Player player, Map<String, Integer> unitsByAsyncId) {
        return unitsByAsyncId.entrySet().stream()
                .map(entry ->
                        new ImmutablePair<>(player.getPriorityUnitByAsyncID(entry.getKey(), null), entry.getValue()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private static void getUnitsOnHolderByAsyncId(
            String colorID, Map<String, Integer> unitsByAsyncId, UnitHolder unitHolder) {
        Map<String, Integer> unitsOnHolderByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        for (Map.Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {
            Integer existingCount = 0;
            if (unitsByAsyncId.containsKey(unitEntry.getKey())) {
                existingCount = unitsByAsyncId.get(unitEntry.getKey());
            }
            unitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
        }
    }

    private static void getUnitsOnHolderByAsyncIdForSpaceCannon(
            String colorID, Map<String, Integer> unitsByAsyncId, UnitHolder unitHolder, Player player) {
        Map<String, Integer> unitsOnHolderByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        for (Map.Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {

            if (player.hasUnit("ralnel_destroyer2") && "space".equalsIgnoreCase(unitHolder.getName())) {
                if ("pd".equalsIgnoreCase(unitEntry.getKey()) || "sd".equalsIgnoreCase(unitEntry.getKey())) {
                    continue;
                }
                if ("dd".equalsIgnoreCase(unitEntry.getKey()) && (unitHolder.getUnitCount(UnitType.Pds, player) < 1)) {
                    continue;
                }
            }
            Integer existingCount = 0;
            if (unitsByAsyncId.containsKey(unitEntry.getKey())) {
                existingCount = unitsByAsyncId.get(unitEntry.getKey());
            }
            unitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
        }
    }

    private static Map<UnitModel, Integer> getUnitsInSpaceCannonDefence(
            Planet planet, Player player, GenericInteractionCreateEvent event) {
        Game game = player.getGame();
        String colorID = Mapper.getColorID(player.getColor());

        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        if (planet == null) {
            return new HashMap<>();
        }

        Map<String, Integer> unitsOnHolderByAsyncId = planet.getUnitAsyncIdsOnHolder(colorID);
        for (Map.Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {
            Integer existingCount = 0;
            if (unitsByAsyncId.containsKey(unitEntry.getKey())) {
                existingCount = unitsByAsyncId.get(unitEntry.getKey());
            }
            unitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
        }

        Map<UnitModel, Integer> unitsOnPlanet = unitsByAsyncId.entrySet().stream()
                .map(entry ->
                        new ImmutablePair<>(player.getPriorityUnitByAsyncID(entry.getKey(), null), entry.getValue()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        // Check for space cannon die on planet
        PlanetModel planetModel = Mapper.getPlanet(planet.getName());
        String ccID = Mapper.getControlID(player.getColor());
        if (player.controlsMecatol(true) && game.mecatols().contains(planet.getName()) && player.hasIIHQ()) {
            PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
            planet.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
            planet.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
        }
        if (planet.getControlList().contains(ccID) && planet.getSpaceCannonDieCount() > 0) {
            UnitModel planetFakeUnit = new UnitModel();
            planetFakeUnit.setSpaceCannonHitsOn(planet.getSpaceCannonHitsOn());
            planetFakeUnit.setSpaceCannonDieCount(planet.getSpaceCannonDieCount());
            planetFakeUnit.setName(Helper.getPlanetRepresentationPlusEmoji(planetModel.getId()) + " space cannon");
            planetFakeUnit.setAsyncId(planet.getName() + "pds");
            planetFakeUnit.setId(planet.getName() + "pds");
            planetFakeUnit.setBaseType("pds");
            planetFakeUnit.setFaction(player.getFaction());
            unitsOnPlanet.put(planetFakeUnit, 1);
        }

        Map<UnitModel, Integer> output = new HashMap<>(unitsOnPlanet.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().getSpaceCannonDieCount(player) > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        checkBadUnits(player, event, unitsByAsyncId, output);

        return output;
    }

    static Map<Pair<UnitModel, UnitHolder>, Integer> getUnitsInSpaceCannonOffense(
            Tile tile, Player player, GenericInteractionCreateEvent event, Game game) {
        String colorID = Mapper.getColorID(player.getColor());
        UnitHolder spaceHolder = tile.getUnitHolders().get("space");

        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        Map<Pair<UnitModel, UnitHolder>, Integer> unitsOnTile = new HashMap<>();

        Collection<UnitHolder> unitHolders = tile.getUnitHolders().values();
        for (UnitHolder unitHolder : unitHolders) {
            Map<String, Integer> holderUnits = new HashMap<>();
            getUnitsOnHolderByAsyncIdForSpaceCannon(colorID, holderUnits, unitHolder, player);
            holderUnits.forEach((k, v) -> unitsByAsyncId.merge(k, v, Integer::sum));
            for (var entry : holderUnits.entrySet()) {
                UnitModel model = player.getPriorityUnitByAsyncID(entry.getKey(), null);
                if (model != null)
                    unitsOnTile.merge(new ImmutablePair<>(model, unitHolder), entry.getValue(), Integer::sum);
            }
        }

        Map<String, Integer> adjacentUnitsByAsyncId = new HashMap<>();
        Map<Pair<UnitModel, UnitHolder>, Integer> unitsOnAdjacentTiles = new HashMap<>();
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false);
        for (String adjacentTilePosition : adjTiles) {
            if (adjacentTilePosition.equals(tile.getPosition())) {
                continue;
            }
            Tile adjTile = game.getTileByPosition(adjacentTilePosition);
            if (TeHelperUnits.affectedByQuietus(game, player, adjTile) || adjTile.isScar(game)) {
                continue;
            }
            for (UnitHolder unitHolder : adjTile.getUnitHolders().values()) {
                Map<String, Integer> holderUnits = new HashMap<>();
                getUnitsOnHolderByAsyncIdForSpaceCannon(colorID, holderUnits, unitHolder, player);
                holderUnits.forEach((k, v) -> adjacentUnitsByAsyncId.merge(k, v, Integer::sum));
                for (var entry : holderUnits.entrySet()) {
                    UnitModel model = player.getPriorityUnitByAsyncID(entry.getKey(), null);
                    if (model != null)
                        unitsOnAdjacentTiles.merge(
                                new ImmutablePair<>(model, unitHolder), entry.getValue(), Integer::sum);
                }
            }
        }

        // Check for space cannon die on planets

        for (UnitHolder unitHolder : unitHolders) {
            if (unitHolder instanceof Planet planet) {
                if (player.controlsMecatol(true) && game.mecatols().contains(planet.getName()) && player.hasIIHQ()) {
                    PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
                    planet.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
                    planet.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
                }
                PlanetModel planetModel = Mapper.getPlanet(planet.getName());
                String ccID = Mapper.getControlID(player.getColor());
                if (planet.getControlList().contains(ccID) && planet.getSpaceCannonDieCount() > 0) {
                    UnitModel planetFakeUnit = new UnitModel();
                    planetFakeUnit.setSpaceCannonHitsOn(planet.getSpaceCannonHitsOn());
                    planetFakeUnit.setSpaceCannonDieCount(planet.getSpaceCannonDieCount());
                    planetFakeUnit.setName(
                            Helper.getPlanetRepresentationPlusEmoji(planetModel.getId()) + " space cannon");
                    planetFakeUnit.setAsyncId(planet.getName() + "pds");
                    planetFakeUnit.setId(planet.getName() + "pds");
                    planetFakeUnit.setBaseType("pds");
                    planetFakeUnit.setFaction(player.getFaction());
                    unitsOnTile.put(new ImmutablePair<>(planetFakeUnit, unitHolder), 1);
                }
                boolean spaceStation =
                        (player.hasUnlockedBreakthrough("gledgebt") || player.hasTech("tf-mantlecracking"))
                                && planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG);
                if ((planet.isSpaceStation(game) || spaceStation)
                        && player.getPlanets().contains(planet.getName())) {
                    if (player.hasUnlockedBreakthrough("gledgebt")) {
                        UnitModel planetFakeUnit = new UnitModel();
                        planetFakeUnit.setSpaceCannonHitsOn(5);
                        planetFakeUnit.setSpaceCannonDieCount(1);
                        planetFakeUnit.setName(
                                Helper.getPlanetRepresentationPlusEmoji(planetModel.getId()) + " space cannon");
                        planetFakeUnit.setAsyncId(planet.getName() + "pds");
                        planetFakeUnit.setId(planet.getName() + "pds");
                        planetFakeUnit.setBaseType("pds");
                        planetFakeUnit.setFaction(player.getFaction());
                        unitsOnTile.put(new ImmutablePair<>(planetFakeUnit, unitHolder), 1);
                    }
                    if (player.hasTech("tf-deepinstallations")) {
                        UnitModel planetFakeUnit = new UnitModel();
                        planetFakeUnit.setSpaceCannonHitsOn(5);
                        planetFakeUnit.setSpaceCannonDieCount(2);
                        planetFakeUnit.setName(
                                Helper.getPlanetRepresentationPlusEmoji(planetModel.getId()) + " space cannon");
                        planetFakeUnit.setAsyncId(planet.getName() + "pds");
                        planetFakeUnit.setId(planet.getName() + "pds");
                        planetFakeUnit.setBaseType("pds");
                        planetFakeUnit.setFaction(player.getFaction());
                        unitsOnTile.put(new ImmutablePair<>(planetFakeUnit, unitHolder), 1);
                    }
                }
            }
        }
        if (player.hasAbility("starfall_gunnery")) {
            if (player == game.getActivePlayer()) {
                int count = Math.min(3, ButtonHelper.checkNumberNonFighterShipsWithoutSpaceCannon(player, tile));
                if (count > 0) {
                    UnitModel starfallFakeUnit = new UnitModel();
                    starfallFakeUnit.setSpaceCannonHitsOn(8);
                    starfallFakeUnit.setSpaceCannonDieCount(1);
                    starfallFakeUnit.setName("Starfall Gunnery space cannon");
                    starfallFakeUnit.setAsyncId("starfallpds");
                    starfallFakeUnit.setId("starfallpds");
                    starfallFakeUnit.setBaseType("pds");
                    starfallFakeUnit.setFaction(player.getFaction());
                    unitsOnTile.put(new ImmutablePair<>(starfallFakeUnit, spaceHolder), count);
                }
            } else {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getFactionEmoji()
                                + ", a reminder that due to the **Starfall Gunnery** ability, the SPACE CANNON of only 1 unit should be counted at this point."
                                + " Hopefully you declared beforehand what that unit was, but by default it's probably the best one. Only look at/count the rolls of that one unit.");
            }
        }

        if (player.hasTech("tf-kinematicstarfall")) {
            if (player == game.getActivePlayer()) {
                int count = Math.min(2, ButtonHelper.checkNumberNonFighterShipsWithoutSpaceCannon(player, tile));
                if (count > 0) {
                    UnitModel starfallFakeUnit = new UnitModel();
                    starfallFakeUnit.setSpaceCannonHitsOn(9);
                    starfallFakeUnit.setSpaceCannonDieCount(1);
                    starfallFakeUnit.setName("Starfall Gunnery space cannon");
                    starfallFakeUnit.setAsyncId("starfallpds");
                    starfallFakeUnit.setId("starfallpds");
                    starfallFakeUnit.setBaseType("pds");
                    starfallFakeUnit.setFaction(player.getFaction());
                    unitsOnTile.put(new ImmutablePair<>(starfallFakeUnit, spaceHolder), count);
                }
            }
        }

        Map<Pair<UnitModel, UnitHolder>, Integer> output = new HashMap<>(unitsOnTile.entrySet().stream()
                .filter(entry -> entry.getKey().getLeft() != null
                        && entry.getKey().getLeft().getSpaceCannonDieCount(player) > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        Map<Pair<UnitModel, UnitHolder>, Integer> adjacentOutput =
                new HashMap<>(unitsOnAdjacentTiles.entrySet().stream()
                        .filter(entry -> entry.getKey().getLeft() != null
                                && entry.getKey().getLeft().getSpaceCannonDieCount(player) > 0
                                && (entry.getKey().getLeft().getDeepSpaceCannon(player)
                                        || game.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander")
                                        || "spacedock"
                                                .equalsIgnoreCase(
                                                        entry.getKey().getLeft().getBaseType())))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        int limit = 0;
        for (var entry : adjacentOutput.entrySet()) {
            if (entry.getKey().getLeft().getDeepSpaceCannon(player)) {
                output.merge(entry.getKey(), entry.getValue(), Integer::sum);
            } else {
                if (limit < 1) {
                    limit = 1;
                    output.merge(entry.getKey(), 1, Integer::sum);
                }
            }
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "netrunnerscommander")) {
            NetrunnersLeadersHandler.getCommanderSpaceCannonUnits(game, player, tile)
                    .forEach((model, count) ->
                            output.merge(new ImmutablePair<>(model, spaceHolder), count, Integer::sum));
        }

        Map<UnitModel, Integer> flatOutput = new HashMap<>();
        output.forEach((k, v) -> flatOutput.merge(k.getLeft(), v, Integer::sum));
        checkBadUnits(player, event, unitsByAsyncId, flatOutput);

        return output;
    }

    static MergeResult mergeAndDetectDivergence(
            Map<Pair<UnitModel, UnitHolder>, Integer> playerUnits,
            List<NamedCombatModifierModel> mods,
            CombatRollType rollType,
            Player player,
            Player opponent,
            Game game,
            List<UnitModel> playerUnitsList,
            Tile activeSystem) {

        IdentityHashMap<Pair<UnitModel, UnitHolder>, Integer> countByIdentity = new IdentityHashMap<>();
        playerUnits.forEach(countByIdentity::put);
        Map<String, List<Pair<UnitModel, UnitHolder>>> modelKeys = new LinkedHashMap<>();
        for (Pair<UnitModel, UnitHolder> key : countByIdentity.keySet()) {
            modelKeys
                    .computeIfAbsent(key.getLeft().getId(), k -> new ArrayList<>())
                    .add(key);
        }
        Set<String> divergingModels = new HashSet<>();
        Map<Pair<UnitModel, UnitHolder>, Integer> merged = new LinkedHashMap<>();
        for (Map.Entry<String, List<Pair<UnitModel, UnitHolder>>> modelEntry : modelKeys.entrySet()) {
            List<Pair<UnitModel, UnitHolder>> keys = modelEntry.getValue();
            if (keys.size() == 1) {
                Pair<UnitModel, UnitHolder> k = keys.get(0);
                merged.put(k, countByIdentity.get(k));
                continue;
            }
            IdentityHashMap<Pair<UnitModel, UnitHolder>, Integer> perKeyToHit = new IdentityHashMap<>();
            for (Pair<UnitModel, UnitHolder> key : keys) {
                UnitModel m = key.getLeft();
                UnitHolder h = key.getRight();
                int toHit = m.getCombatDieHitsOnForAbility(rollType, player);
                if (rollType == CombatRollType.combatround) {
                    toHit = CombatStatsService.getCombatRoundProfile(true, m, player, activeSystem, opponent, false)
                            .hitsOn();
                }
                int mod = CombatModHelper.getCombinedModifierForUnit(
                        m,
                        countByIdentity.get(key),
                        mods,
                        player,
                        opponent,
                        game,
                        playerUnitsList,
                        rollType,
                        activeSystem,
                        h);
                perKeyToHit.put(key, toHit - mod);
            }
            Set<Integer> distinctToHits = new HashSet<>(perKeyToHit.values());
            if (distinctToHits.size() > 1) {
                divergingModels.add(modelEntry.getKey());
                keys.sort(Comparator.comparingInt(perKeyToHit::get));
                for (Pair<UnitModel, UnitHolder> k : keys) merged.put(k, countByIdentity.get(k));
            } else {
                int totalCount = keys.stream().mapToInt(countByIdentity::get).sum();
                merged.put(keys.get(0), totalCount);
            }
        }
        return new MergeResult(merged, divergingModels);
    }

    private static void checkBadUnits(
            Player player,
            GenericInteractionCreateEvent event,
            Map<String, Integer> unitsByAsyncId,
            Map<UnitModel, Integer> output) {
        Set<String> duplicates = new HashSet<>();
        List<String> dupes = output.keySet().stream()
                .filter(unit -> !duplicates.add(unit.getAsyncId()))
                .map(UnitModel::getBaseType)
                .toList();
        List<String> missing = unitsByAsyncId.keySet().stream()
                .filter(unit -> player.getUnitsByAsyncID(unit.toLowerCase()).isEmpty())
                .collect(Collectors.toList());

        if (!dupes.isEmpty()) {
            CombatMessageHelper.displayDuplicateUnits(event, missing);
        }
        if (!missing.isEmpty()) {
            CombatMessageHelper.displayMissingUnits(event, missing);
        }
    }

    private static class CombatRollPipelineState {
        private final Player player;
        private final Game game;
        private final GenericInteractionCreateEvent event;
        private final Tile tile;
        private final String unitHolderName;
        private final CombatRollType rollType;
        private final boolean automated;
        private UnitHolder combatOnHolder;
        private Map<Pair<UnitModel, UnitHolder>, Integer> playerUnits;
        private Player opponent;
        private RollModifiers modifiers;
        private String bombardPlanet = "";
        private CombatRollResult rollResult;
        private String message;
        private CombatRollPayload payload;
        private int opponentRound;
        private int playerRound;
        private int hits;
        private boolean stopped;

        private CombatRollPipelineState(
                Player player,
                Game game,
                GenericInteractionCreateEvent event,
                Tile tile,
                String unitHolderName,
                CombatRollType rollType,
                boolean automated) {
            this.player = player;
            this.game = game;
            this.event = event;
            this.tile = tile;
            this.unitHolderName = unitHolderName;
            this.rollType = rollType;
            this.automated = automated;
        }
    }

    private record AdjustedRollResult(String message, int hits) {}

    private record GroundCombatResultContext(
            Player player,
            Player opponent,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            UnitHolder combatOnHolder,
            int hits,
            int opponentRound,
            int playerRound) {}

    private record SpaceCombatResultContext(
            Player opponent,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            UnitHolder combatOnHolder,
            int hits,
            int opponentRound,
            int playerRound) {}

    private record BombardmentContext(String planet, Player opponent) {}

    private record RollModifiers(
            List<NamedCombatModifierModel> combatModifiers,
            List<NamedCombatModifierModel> extraRolls,
            List<NamedCombatModifierModel> temporaryModifiers) {}

    private static class UnitRollState {
        // Unit-lifetime fields are populated once by prepareUnitRoll.
        private final UnitRollPipelineState pipeline;
        private final UnitModel unitModel;
        private final UnitHolder perUnitHolder;
        private int toHit;
        private int baseModifierToHit;
        private int numOfUnit;
        private int numRollsPerUnit;
        private int extraRollsForUnit;
        private boolean extraRollsCount;
        private List<String> singleUnitUse;
        private int ogNumOfUnit;
        private List<NamedCombatModifierModel> availableExtraRolls = List.of();

        // Segment-lifetime fields are reset by prepareUnitRollSegment before every segment.
        private RollSegmentType segmentType;
        private List<Die> resultRolls;
        private int hitRolls;
        private int modifierToHit;
        private int numRolls;
        private int multiplier;
        private List<Die> secondaryRolls = new ArrayList<>();
        private int numMisses;
        private int maximumHits;
        private double chanceOfAllHits;

        private UnitRollState(UnitRollPipelineState pipeline, Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry) {
            this.pipeline = pipeline;
            this.unitModel = entry.getKey().getLeft();
            this.perUnitHolder = entry.getKey().getRight();
            this.segmentType = null;
            this.resultRolls = List.of();
            this.hitRolls = 0;
            this.toHit = unitModel.getCombatDieHitsOnForAbility(pipeline.rollType, pipeline.player);
            this.modifierToHit = 0;
            this.baseModifierToHit = 0;
            this.numRolls = 0;
            this.multiplier = 1;
            this.numOfUnit = entry.getValue();
            this.numRollsPerUnit = unitModel.getCombatDieCountForAbility(pipeline.rollType, pipeline.player);
            this.extraRollsForUnit = 0;
            this.extraRollsCount = false;
            this.singleUnitUse = new ArrayList<>(List.of("no"));
            this.ogNumOfUnit = numOfUnit;
        }
    }

    private static class UnitRollPipelineState {
        private Map<Pair<UnitModel, UnitHolder>, Integer> playerUnits;
        private final List<NamedCombatModifierModel> extraRolls;
        private final List<NamedCombatModifierModel> autoMods;
        private final List<NamedCombatModifierModel> tempMods;
        private final Player player;
        private final Player opponent;
        private final Game game;
        private final CombatRollType rollType;
        private final GenericInteractionCreateEvent event;
        private final Tile activeSystem;
        private final UnitHolder unitHolder;
        private List<NamedCombatModifierModel> mods;
        private final List<UnitModel> playerUnitsList;
        private Set<String> divergingModels = Set.of();
        private final Set<String> consumedBestMods = new HashSet<>();
        private final RollPayloadBuilder payloadBuilder = new RollPayloadBuilder();
        private final List<CombatRollPayload.CombatRollNote> delayedAfterTotalNotes = new ArrayList<>();
        private StringBuilder resultBuilder = new StringBuilder();
        private int letnevBTBoost;
        private final boolean hacanFlagship;
        private final boolean tkHacanWarsun;
        private final List<Button> hacanFsButtons = new ArrayList<>();
        private final List<UnitType> hacanFsThalnosDestroyTypes = new ArrayList<>();
        private final boolean isThalnosReroll;
        private final UnitHolder space;
        private final StringBuilder extra = new StringBuilder();
        private final boolean usesX89c4;
        private int totalHits;
        private int totalMisses;
        private int maximumHits;
        private int nearMisses;
        private double chanceOfAllHits = Math.nextDown(100.0);
        private double chanceOfAllMiss = Math.nextDown(100.0);
        private boolean whiff;
        private boolean slam;
        private boolean useDoubleBoomEmoji;
        private boolean metaliVoidCounted;

        private UnitRollPipelineState(
                Map<Pair<UnitModel, UnitHolder>, Integer> playerUnits,
                List<NamedCombatModifierModel> extraRolls,
                List<NamedCombatModifierModel> autoMods,
                List<NamedCombatModifierModel> tempMods,
                Player player,
                Player opponent,
                Game game,
                CombatRollType rollType,
                GenericInteractionCreateEvent event,
                Tile activeSystem,
                UnitHolder unitHolder) {
            this.playerUnits = playerUnits;
            this.extraRolls = extraRolls;
            this.autoMods = autoMods;
            this.tempMods = tempMods;
            this.player = player;
            this.opponent = opponent;
            this.game = game;
            this.rollType = rollType;
            this.event = event;
            this.activeSystem = activeSystem;
            this.unitHolder = unitHolder;
            playerUnitsList = playerUnits.keySet().stream().map(Pair::getLeft).collect(Collectors.toList());
            List<UnitType> unitTypes =
                    playerUnitsList.stream().map(UnitModel::getUnitType).toList();
            hacanFlagship = player.hasUnit("hacan_flagship") && unitTypes.contains(UnitType.Flagship);
            tkHacanWarsun = player.hasUnit("tk-fallofkenara") && unitTypes.contains(UnitType.Warsun);
            isThalnosReroll = "true".equalsIgnoreCase(game.getStoredValue("thalnosPlusOne"));
            space = activeSystem.getUnitHolders().get(Constants.SPACE);
            usesX89c4 = player.hasTech("x89c4")
                    && (rollType == CombatRollType.combatround || rollType == CombatRollType.bombardment)
                    && (!Constants.SPACE.equalsIgnoreCase(unitHolder.getName())
                            || rollType == CombatRollType.bombardment);
        }
    }

    private record PreparedModifiers(List<NamedCombatModifierModel> rollModifiers, String display) {}

    private static class RollPayloadBuilder {
        private final List<CombatRollPayload.CombatRollNote> notes = new ArrayList<>();
        private final List<CombatRollPayload.ModifierDisplay> modifiers = new ArrayList<>();
        private final List<CombatRollPayload.UnitRoll> unitRolls = new ArrayList<>();
        private int diceRolled;

        void addNote(CombatRollPayload.CombatRollNote note) {
            if (note != null) {
                notes.add(note);
            }
        }

        void addModifierDisplays(
                List<NamedCombatModifierModel> namedModifiers,
                Map<UnitModel, Integer> units,
                Player player,
                Player opponent,
                Game game,
                CombatRollType rollType,
                Tile activeSystem,
                UnitHolder unitHolder) {
            if (namedModifiers.isEmpty()) return;

            List<UnitModel> playerUnits = new ArrayList<>(units.keySet());
            for (NamedCombatModifierModel namedModifier : namedModifiers) {
                CombatModifierModel modifier = namedModifier.getModifier();
                Map<String, Integer> effectiveValues = new HashMap<>();
                for (Map.Entry<UnitModel, Integer> unitEntry : units.entrySet()) {
                    UnitModel unit = unitEntry.getKey();
                    int effectiveValue = CombatModHelper.getCombinedModifierForUnit(
                            unit,
                            unitEntry.getValue(),
                            List.of(namedModifier),
                            player,
                            opponent,
                            game,
                            playerUnits,
                            rollType,
                            activeSystem,
                            unitHolder);
                    if (effectiveValue != 0) {
                        effectiveValues.put(unit.getAsyncId(), effectiveValue);
                    }
                }
                modifiers.add(new CombatRollPayload.ModifierDisplay(
                        modifier.getAlias(),
                        namedModifier.getName(),
                        modifier.getValue(),
                        modifier.getType(),
                        modifier.getScope(),
                        resolveScopeDisplay(modifier, units),
                        effectiveValues));
            }
        }

        void addUnitRoll(
                UnitModel unitModel,
                int toHit,
                int modifier,
                int unitQuantity,
                int numRollsPerUnit,
                int extraRolls,
                RollSegmentType segmentType,
                List<DiceHelper.Die> resultRolls,
                int hits,
                DieRollSource source) {
            diceRolled += resultRolls.size();
            unitRolls.add(new CombatRollPayload.UnitRoll(
                    unitModel.getId(),
                    unitModel.getAsyncId(),
                    unitModel.getBaseType(),
                    unitModel.getName(),
                    getDisplayedUnitName(unitModel),
                    unitModel.getUnitEmoji().toString(),
                    unitQuantity,
                    numRollsPerUnit,
                    extraRolls,
                    toHit,
                    modifier,
                    toHit - modifier,
                    segmentType,
                    toDieRolls(resultRolls, source),
                    hits));
        }

        CombatRollPayload build(int displayedTotalHits, int misses, int maximumHits) {
            return new CombatRollPayload(
                    null,
                    notes,
                    modifiers,
                    unitRolls,
                    new CombatRollPayload.RollTotal(diceRolled, displayedTotalHits, misses, maximumHits));
        }

        private List<CombatRollPayload.DieRoll> toDieRolls(List<DiceHelper.Die> resultRolls, DieRollSource source) {
            if (resultRolls.isEmpty()) return List.of();
            return resultRolls.stream()
                    .map(die ->
                            new CombatRollPayload.DieRoll(die.getResult(), die.getThreshold(), die.isSuccess(), source))
                    .toList();
        }

        private String getDisplayedUnitName(UnitModel unitModel) {
            if (unitModel.getUpgradesFromUnitId().isPresent()
                    || unitModel.getFaction().isPresent()) {
                return unitModel.getName();
            }
            return "";
        }

        private String resolveScopeDisplay(CombatModifierModel modifier, Map<UnitModel, Integer> units) {
            String unitScope = modifier.getScope();
            if (isBlank(unitScope)) return "all";
            return units.keySet().stream()
                    .filter(unit -> unit.getAsyncId().equals(unitScope))
                    .findFirst()
                    .map(unit -> unit.getUnitEmoji().toString())
                    .orElse(unitScope);
        }
    }

    record CombatRollResult(String message, int totalHits, boolean whiff, boolean slam, CombatRollPayload payload) {}

    record MergeResult(Map<Pair<UnitModel, UnitHolder>, Integer> units, Set<String> divergingModels) {}
}
