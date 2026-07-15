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
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.*;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.*;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum.*;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.*;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ardentia.ArdentiaUnitHandler;
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
import ti4.service.leader.UnlockLeaderService;
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
        String sb = "";
        UnitHolder combatOnHolder = tile.getUnitHolders().get(unitHolderName);
        if (combatOnHolder == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Cannot find the planet " + unitHolderName + " on tile " + tile.getPosition() + ".");
            return 0;
        }

        if (rollType == CombatRollType.SpaceCannonDefence && !(combatOnHolder instanceof Planet)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Planet needs to be specified to fire SPACE CANNON against ships on tile " + tile.getPosition()
                            + ".");
            return 0;
        }
        Player opponent = null;

        Map<Pair<UnitModel, UnitHolder>, Integer> playerUnitsByQuantity =
                getUnitsInCombatByHolder(tile, combatOnHolder, player, event, rollType, game);
        if (rollType == CombatRollType.AFB && player.hasRelic("metalivoidarmaments")) {
            playerUnitsByQuantity.put(new ImmutablePair<>(getMetaliAFBUnit(player), combatOnHolder), 1);
        }
        if (rollType == CombatRollType.AFB && player.hasTech("tf-projectionofpow")) {
            playerUnitsByQuantity.put(new ImmutablePair<>(getProjectionUnit(player, true), combatOnHolder), 1);
        }
        if (player.hasAbility("projection_of_power")) {
            boolean adj = false;
            for (Tile tile2 : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock)) {
                if (FoWHelper.getAdjacentTiles(game, tile2.getPosition(), player, false, true)
                        .contains(tile.getPosition())) {
                    adj = true;
                    break;
                }
            }
            if (adj) {
                playerUnitsByQuantity.put(new ImmutablePair<>(getProjectionUnit(player, false), combatOnHolder), 1);
            }
        }
        if (rollType == CombatRollType.combatround && player.hasActiveBreakthrough("zelianbt")) {
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                if (player.getPlanetsAllianceMode().contains(uH.getName())
                        && ("space".equalsIgnoreCase(unitHolderName)
                                || uH.getName().equalsIgnoreCase(unitHolderName))) {
                    int resource = Helper.getPlanetResources(uH.getName(), game);
                    playerUnitsByQuantity.put(
                            new ImmutablePair<>(
                                    getZelianPlanetUnit(player, Helper.getPlanetName(uH.getName()), 10 - resource),
                                    combatOnHolder),
                            1);
                }
            }
        }
        if (rollType == CombatRollType.combatround
                && player.hasTech("tf-hostileplanetoids")
                && "space".equalsIgnoreCase(unitHolderName)) {
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                if (player.getPlanetsAllianceMode().contains(uH.getName())) {
                    int resource = Helper.getPlanetResources(uH.getName(), game);
                    playerUnitsByQuantity.put(
                            new ImmutablePair<>(
                                    getZelianPlanetUnit(player, Helper.getPlanetName(uH.getName()), 10 - resource),
                                    combatOnHolder),
                            1);
                }
            }
        }
        String bombardPlanet = "";
        if (rollType == CombatRollType.bombardment
                && !game.getStoredValue("bombardmentTarget" + player.getFaction())
                        .isEmpty()) {
            bombardPlanet = game.getStoredValue("bombardmentTarget" + player.getFaction());
            if (player.hasUnit("ashen_flagship")) {
                AshenUnitHandler.prepareFlagshipBombardmentContext(game, player, bombardPlanet);
            }
            List<BombardmentAssignment> assignedUnits = MAPPER.readValue(
                    game.getStoredValue("assignedBombardment" + player.getFaction()),
                    new TypeReference<List<BombardmentAssignment>>() {});
            Map<String, Integer> remainingAssignedByAsyncId = new HashMap<>();
            for (BombardmentAssignment assignedUnit : assignedUnits) {
                if (assignedUnit.planet().equals(bombardPlanet) && assignedUnit.sourceId() != null) {
                    String asyncId = assignedUnit.sourceId();
                    remainingAssignedByAsyncId.merge(asyncId, 1, Integer::sum);
                }
            }
            List<Pair<UnitModel, UnitHolder>> unitMods = new ArrayList<>(playerUnitsByQuantity.keySet());
            for (Pair<UnitModel, UnitHolder> mod : unitMods) {
                // The same asyncId can span multiple holders here, so split the assigned total across them
                // instead of giving every holder the full matched count.
                String asyncId = mod.getLeft().getAsyncId();
                int available = remainingAssignedByAsyncId.getOrDefault(asyncId, 0);
                int count = Math.min(available, playerUnitsByQuantity.get(mod));
                if (count > 0) {
                    remainingAssignedByAsyncId.put(asyncId, available - count);
                    playerUnitsByQuantity.put(mod, count);
                } else {
                    playerUnitsByQuantity.remove(mod);
                }
            }
            for (Player p2 : game.getRealPlayersNNeutral()) {
                if (p2.getPlanets().contains(bombardPlanet)) {
                    opponent = p2;
                    break;
                }
            }
        }

        if (ButtonHelper.isLawInPlay(game, "articles_war")) {
            if (playerUnitsByQuantity.keySet().stream()
                    .anyMatch(pair -> "naaz_mech_space".equals(pair.getLeft().getAlias()))) {
                playerUnitsByQuantity = new HashMap<>(playerUnitsByQuantity.entrySet().stream()
                        .filter(e ->
                                !"naaz_mech_space".equals(e.getKey().getLeft().getAlias()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Skipping Z-Grav Eidolon (Naaz-Rokha mech) combat rolls due to _Articles of War_.");
            }
            if (rollType == CombatRollType.SpaceCannonDefence || rollType == CombatRollType.SpaceCannonOffence) {
                if (playerUnitsByQuantity.keySet().stream()
                        .anyMatch(pair -> "xxcha_mech".equals(pair.getLeft().getAlias()))) {
                    playerUnitsByQuantity = new HashMap<>(playerUnitsByQuantity.entrySet().stream()
                            .filter(e ->
                                    !"xxcha_mech".equals(e.getKey().getLeft().getAlias()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            "Skipping Indomitus (Xxcha mech) SPACE CANNON rolls due to _Articles of War_.");
                }
            }
            if (rollType == CombatRollType.bombardment) {
                if (playerUnitsByQuantity.keySet().stream()
                        .anyMatch(pair -> "l1z1x_mech".equals(pair.getLeft().getAlias()))) {
                    playerUnitsByQuantity = new HashMap<>(playerUnitsByQuantity.entrySet().stream()
                            .filter(e ->
                                    !"l1z1x_mech".equals(e.getKey().getLeft().getAlias()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            "Skipping Annihilator (L1Z1X mech) BOMBARDMENT rolls due to _Articles of War_.");
                }
            }
        }

        if (playerUnitsByQuantity.isEmpty()) {
            String fightingOnUnitHolderName = unitHolderName;
            if (!Constants.SPACE.equalsIgnoreCase(unitHolderName)) {
                fightingOnUnitHolderName = Helper.getPlanetRepresentation(unitHolderName, game);
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "There are no units in " + fightingOnUnitHolderName + " on tile " + tile.getPosition()
                            + " for player " + player.getColor() + " "
                            + player.getFactionEmoji() + " for the combat roll type " + rollType.toString() + "\n"
                            + "Ping bothelper if this seems to be in error.");
            return 0;
        }

        List<UnitHolder> combatHoldersForOpponent = new ArrayList<>(List.of(combatOnHolder));
        if (rollType == CombatRollType.SpaceCannonDefence || rollType == CombatRollType.SpaceCannonOffence) {
            combatHoldersForOpponent.add(tile.getUnitHolders().get(Constants.SPACE));
        }
        if (opponent == null) {
            opponent = getOpponent(player, combatHoldersForOpponent, game);
            if (opponent == null) {
                opponent = player;
            }
        }
        if (game.getRealPlayers().stream().anyMatch(player_ -> player_.hasUnit("netrunners_flagship"))
                && NetrunnersUnitsHandler.resolveEmpSpaceCannonBlock(event, game, player, tile, rollType)) {
            return 0;
        }
        Map<UnitModel, Integer> opponentUnitsByQuantity =
                getUnitsInCombat(tile, combatOnHolder, opponent, event, rollType, game);

        TileModel tileModel = TileHelper.getTileById(tile.getTileID());
        Map<UnitModel, Integer> playerUnitsFlat = new HashMap<>();
        playerUnitsByQuantity.forEach((k, v) -> playerUnitsFlat.merge(k.getLeft(), v, Integer::sum));
        List<NamedCombatModifierModel> modifiers = CombatModHelper.getModifiers(
                player,
                opponent,
                playerUnitsFlat,
                opponentUnitsByQuantity,
                tileModel,
                game,
                rollType,
                combatOnHolder,
                Constants.COMBAT_MODIFIERS);

        List<NamedCombatModifierModel> extraRolls = CombatModHelper.getModifiers(
                player,
                opponent,
                playerUnitsFlat,
                opponentUnitsByQuantity,
                tileModel,
                game,
                rollType,
                combatOnHolder,
                Constants.COMBAT_EXTRA_ROLLS);

        List<NamedCombatModifierModel> extraRollsDup = new ArrayList<>(extraRolls);

        String gameAssignedBombardment = game.getStoredValue("assignedBombardment" + player.getFaction());
        if (!gameAssignedBombardment.isEmpty() && rollType == CombatRollType.bombardment) {
            List<BombardmentAssignment> assignedBombardment =
                    MAPPER.readValue(gameAssignedBombardment, new TypeReference<List<BombardmentAssignment>>() {});
            String tempBombardPlanet = bombardPlanet;
            for (NamedCombatModifierModel mod : extraRollsDup) {
                if ("plus1_roll_plasmascoring"
                        .equalsIgnoreCase(mod.getModifier().getAlias())) {
                    if (assignedBombardment.stream()
                            .filter(a -> a.planet().equals(tempBombardPlanet))
                            .noneMatch(a -> "plasmascoring".equals(a.sourceId()))) {
                        extraRolls.remove(mod);
                    }
                }
                if ("plus1_roll_argent_commander_bombard"
                        .equalsIgnoreCase(mod.getModifier().getAlias())) {
                    if (assignedBombardment.stream()
                            .filter(a -> a.planet().equals(tempBombardPlanet))
                            .noneMatch(a -> "argentcommander".equals(a.sourceId()))) {
                        extraRolls.remove(mod);
                    }
                }
                if ("roll_1_for_galvanize_bombard"
                        .equalsIgnoreCase(mod.getModifier().getAlias())) {
                    if (assignedBombardment.stream()
                            .filter(a -> a.planet().equals(tempBombardPlanet))
                            .noneMatch(a -> a.galvanized())) {
                        extraRolls.remove(mod);
                    }
                }
            }
        }

        // Check for temp mods
        CombatTempModHelper.ensureValidTempMods(player, tileModel, combatOnHolder);
        CombatTempModHelper.initializeNewTempMods(player, tileModel, combatOnHolder);
        List<NamedCombatModifierModel> tempMods =
                new ArrayList<>(CombatTempModHelper.buildCurrentRoundTempNamedModifiers(
                        player, tileModel, combatOnHolder, false, rollType));
        List<NamedCombatModifierModel> tempOpponentMods = CombatTempModHelper.buildCurrentRoundTempNamedModifiers(
                opponent, tileModel, combatOnHolder, true, rollType);
        tempMods.addAll(tempOpponentMods);
        if (game.getRealPlayers().stream().anyMatch(player_ -> player_.hasAbility("control_network"))) {
            tempMods.addAll(NetrunnersAbilitiesHandler.getPendingControlNetworkSpaceCannonModifier(
                    game, player, tile, combatOnHolder, rollType));
        }
        if (player.hasTech("beironats")) {
            extraRolls.addAll(IronFactionTechsHandler.getAdvancedTargetingSystemsExtraRollModifier(
                    game, player, opponent, tile, combatOnHolder, rollType));
        }

        CombatRollResult rollResult = rollForUnitsWithResult(
                playerUnitsByQuantity,
                extraRolls,
                modifiers,
                tempMods,
                player,
                opponent,
                game,
                rollType,
                event,
                tile,
                combatOnHolder);
        String combatSummary = CombatMessageHelper.displayCombatSummary(player, tile, combatOnHolder, rollType);
        String message = combatSummary + rollResult.message();
        CombatRollPayload.RollHeader rollHeader =
                buildRollHeader(game, player, opponent, tile, combatOnHolder, rollType, combatSummary);
        CombatRollPayload payload = rollResult.payload().withHeader(rollHeader);
        FOWCombatThreadMirroring.mirrorCombatMessage(event, player, game, message);
        int h = rollResult.totalHits();
        int round;
        String combatName =
                "combatRoundTracker" + opponent.getFaction() + tile.getPosition() + combatOnHolder.getName();
        if (game.getStoredValue(combatName).isEmpty()) {
            round = 0;
        } else {
            round = Integer.parseInt(game.getStoredValue(combatName));
        }
        int round2;
        String combatName2 = "combatRoundTracker" + player.getFaction() + tile.getPosition() + combatOnHolder.getName();
        if (game.getStoredValue(combatName2).isEmpty()) {
            round2 = 1;
        } else {
            round2 = Integer.parseInt(game.getStoredValue(combatName2));
        }

        if (round2 > round && rollType == CombatRollType.combatround) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "## __Start of Combat Round #" + round2 + "__");
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb);
        if (rollType == CombatRollType.bombardment && opponent != player && opponent.hasTech("proxima") && h > 0) {
            if (opponent.hasTech("tf-proxima")) {
                message += "\n_Proxima Targeting VI_ canceled 1 hit automatically.";
                h--;
            } else {
                if (!bombardPlanet.isEmpty()) {
                    UnitHolder planet = game.getUnitHolderFromPlanet(bombardPlanet);
                    if (planet != null && planet.getGalvanizedUnitCount(player.getColorID()) > 0) {
                        int oldH = h;
                        h = Math.max(0, h - planet.getGalvanizedUnitCount(player.getColorID()));
                        message += "\n_Proxima Targeting VI_ canceled " + (oldH - h) + " hit"
                                + (oldH - h == 1 ? "" : "s") + " automatically.";
                    }
                }
            }
        }
        if (message.endsWith(";\n")) {
            message = message.substring(0, message.length() - 2);
        }
        if (player.hasBreakthrough("ashenbt")) {
            message = AshenBreakthroughHandler.appendBombardmentManualReminder(player, rollType, message);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        if (rollType == CombatRollType.combatround
                && Constants.SPACE.equalsIgnoreCase(unitHolderName)
                && opponent != player) {
            ArdentiaUnitHandler.offerSovereignsGavelButton(event, game, player, opponent, tile);
        }
        CombatReplayService combatReplayService = SpringContext.getBean(CombatReplayService.class);
        boolean trackedCandidateRoll =
                combatReplayService.isTrackedCandidateRoll(game, player, opponent, tile, rollType);
        combatReplayService.mirrorCombatRoll(
                game, player, opponent, tile, message, rollType, rollResult.whiff(), rollResult.slam(), payload);
        if (message.contains("adding +1, at the risk of your")) {
            Button thalnosButton = Buttons.green(
                    "startThalnos_" + tile.getPosition() + "_" + unitHolderName, "Roll Thalnos", ExploreEmojis.Relic);
            Button decline = Buttons.gray("deleteButtons", "Decline");
            String thalnosMessage =
                    "Use this button to roll for Thalnos.\n-# Note that if it matters, the dice were just rolled in the following format: (normal dice for unit 1)+(normal dice for unit 2)...etc...+(extra dice for unit 1)+(extra dice for unit 2)...etc.\n-# Sol and Letnev agents automatically are given as extra dice for unit 1.";
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(), thalnosMessage, List.of(thalnosButton, decline));
        }

        if (!game.isFowMode()) {
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
            List<Button> buttons = new ArrayList<>();
            if (rollType == CombatRollType.combatround && opponent != player) {
                if (combatOnHolder instanceof Planet) {
                    String msg2 = "\n" + opponent.getRepresentation(true, true, true, true) + ", you suffered "
                            + StringHelper.pluralize(h, "hit") + " in round #" + round2 + ".";
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2);
                    if (!automated) {
                        if (h > 0) {
                            String msg = opponent.getRepresentationUnfogged() + " you may autoassign "
                                    + StringHelper.pluralize(h, "hit") + ".";
                            if (opponent.isDummy() || opponent.isNpc()) {
                                if (round2 > round) {
                                    buttons.add(Buttons.blue(
                                            opponent.dummyPlayerSpoof() + "combatRoll_" + tile.getPosition() + "_"
                                                    + combatOnHolder.getName(),
                                            "Roll Dice For Dummy for Combat Round #" + (round + 1)));
                                }
                                buttons.add(Buttons.green(
                                        opponent.dummyPlayerSpoof() + "autoAssignGroundHits_" + combatOnHolder.getName()
                                                + "_" + h,
                                        "Auto-assign Hit" + (h == 1 ? "" : "s") + " For Dummy"));
                            } else {
                                if (round2 > round) {
                                    buttons.add(Buttons.blue(
                                            "combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(),
                                            "Roll Dice For Combat Round #" + (round + 1)));
                                }
                                buttons.add(Buttons.green(
                                        opponent.factionButtonChecker() + "autoAssignGroundHits_"
                                                + combatOnHolder.getName() + "_" + h,
                                        "Auto-assign Hit" + (h == 1 ? "" : "s")));
                                buttons.add(Buttons.red(
                                        "getDamageButtons_" + tile.getPosition() + "deleteThis_groundcombat",
                                        "Manually Assign Hit" + (h == 1 ? "" : "s")));

                                buttons.add(Buttons.gray(
                                        opponent.factionButtonChecker() + "cancelGroundHits_" + tile.getPosition() + "_"
                                                + h,
                                        "Cancel a Hit"));
                                AshenPromissoryHandler.addFromTheAshesButton(
                                        buttons, game, opponent, player, tile, combatOnHolder, h);
                                if (opponent.hasUnit("crystellum_mech")) {
                                    CrystellumUnitHandler.offerRefractumButtonIfRelevant(
                                            buttons, opponent, game, tile, combatOnHolder, h);
                                }
                            }
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
                            if (opponent.hasTech("vpw")) {
                                msg = player.getRepresentationUnfogged()
                                        + " you got hit by _Valkyrie Particle Weave_. You may autoassign 1 hit.";
                                buttons = new ArrayList<>();
                                buttons.add(Buttons.green(
                                        player.factionButtonChecker() + "autoAssignGroundHits_"
                                                + combatOnHolder.getName() + "_1",
                                        "Auto-assign Hit" + (h == 1 ? "" : "s")));
                                buttons.add(Buttons.red(
                                        "getDamageButtons_" + tile.getPosition() + "deleteThis_groundcombat",
                                        "Manually Assign Hit" + (h == 1 ? "" : "s")));
                                buttons.add(Buttons.gray(
                                        player.factionButtonChecker() + "cancelGroundHits_" + tile.getPosition() + "_1",
                                        "Cancel a Hit"));
                                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
                            }
                        } else {
                            String msg = opponent.getRepresentationUnfogged() + " you may roll dice for Combat Round #"
                                    + (round + 1) + ".";
                            if (opponent.isDummy() || opponent.isNpc()) {
                                if (round2 > round) {
                                    buttons.add(Buttons.blue(
                                            opponent.dummyPlayerSpoof() + "combatRoll_" + tile.getPosition() + "_"
                                                    + combatOnHolder.getName(),
                                            "Roll Dice For Dummy for Combat Round #" + (round + 1)));
                                    MessageHelper.sendMessageToChannelWithButtons(
                                            event.getMessageChannel(), msg, buttons);
                                }

                            } else {
                                if (round2 > round) {
                                    buttons.add(Buttons.blue(
                                            "combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(),
                                            "Roll Dice For Combat Round #" + (round + 1)));
                                    MessageHelper.sendMessageToChannelWithButtons(
                                            event.getMessageChannel(), msg, buttons);
                                }
                            }
                        }
                    } else if (opponent.hasTech("vpw") && h > 0) {
                        MessageHelper.sendMessageToChannel(
                                event.getMessageChannel(),
                                player.getRepresentation() + " suffered 1 hit due to _Valkyrie Particle Weave_.");
                    }
                } else {
                    if (round2 > round) {
                        if (opponent.isDummy() || opponent.isNpc()) {
                            buttons.add(Buttons.blue(
                                    opponent.dummyPlayerSpoof() + "combatRoll_" + tile.getPosition() + "_"
                                            + combatOnHolder.getName(),
                                    "Roll Dice For Dummy For Combat Round #" + (round + 1)));
                        } else {
                            buttons.add(Buttons.blue(
                                    "combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(),
                                    "Roll Dice For Combat Round #" + (round + 1)));
                        }
                    }
                    String msg = "\n" + opponent.getRepresentation(true, true, true, true) + ", you suffered "
                            + StringHelper.pluralize(h, "hit") + " in round #" + round2 + ".";
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                    if (h > 0) {
                        String factionChecker = "FFCC_" + opponent.getFaction() + "_";
                        if (opponent.isDummy() || opponent.isNpc()) {
                            buttons.add(Buttons.green(
                                    opponent.dummyPlayerSpoof() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h,
                                    "Auto-assign Hit" + (h == 1 ? "" : "s") + " For Dummy"));

                        } else if (opponent.hasAbility("refraction")) {
                            buttons.add(Buttons.green(
                                    factionChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h,
                                    "Auto-assign Hit" + (h == 1 ? "" : "s")));
                            buttons.add(Buttons.red(
                                    "getDamageButtons_" + tile.getPosition() + "deleteThis_spacecombat",
                                    "Manually Assign Hit" + (h == 1 ? "" : "s")));
                            buttons.add(Buttons.gray(
                                    factionChecker + "cancelSpaceHits_" + tile.getPosition() + "_" + h,
                                    "Cancel a Hit"));
                            CrystellumAbilityHandler.addRefractionButtonIfRelevant(buttons, opponent, game, tile, h);
                        } else {
                            buttons.add(Buttons.green(
                                    factionChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h,
                                    "Auto-assign Hit" + (h == 1 ? "" : "s")));
                            buttons.add(Buttons.red(
                                    "getDamageButtons_" + tile.getPosition() + "deleteThis_spacecombat",
                                    "Manually Assign Hit" + (h == 1 ? "" : "s")));
                            buttons.add(Buttons.gray(
                                    factionChecker + "cancelSpaceHits_" + tile.getPosition() + "_" + h,
                                    "Cancel a Hit"));
                        }

                        String msg2 = opponent.getRepresentationNoPing() + ", you may automatically assign "
                                + (h == 1 ? "the hit" : "hits") + ". "
                                + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                                        opponent, game, tile, h, event, true);
                        if (opponent.hasRelic("metalivoidshielding")) {
                            RelicModel relicModel = Mapper.getRelic("metalivoidshielding");
                            msg2 += "\nReminder: You have the _" + relicModel.getName()
                                    + "_ relic, you may SUSTAIN DAMAGE on one of your non-fighter ships instead of taking a hit.";
                        }
                        if (opponent.hasUnlockedBreakthrough("crystellumbt") && round2 == 1) {
                            msg2 +=
                                    "\nReminder: You have _Defensive Architecture_.\nFor each unit in the active system that is at capacity, you may give one other non-fighter ship in the same system SUSTAIN DAMAGE until the end of this combat. This is not tracked by the bot.";
                        }
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);
                    } else {
                        String msg2 = opponent.getRepresentationUnfogged() + " you may roll dice for Combat Round #"
                                + (round + 1) + ".";
                        if (round2 > round) {
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);
                        }
                    }
                }
            }
            if (rollType == CombatRollType.AFB && h > 0) {
                String msg2 = opponent.getRepresentation() + ", you may automatically assign "
                        + (h == 1 ? "the hit" : "hits") + " from AFB.";
                if (opponent.isNpc() || opponent.isDummy()) {
                    buttons.add(Buttons.green(
                            opponent.dummyPlayerSpoof() + "autoAssignAFBHits_" + tile.getPosition() + "_" + h,
                            "Auto-assign Hit" + (h == 1 ? "" : "s For Dummy")));
                } else {
                    buttons.add(Buttons.green(
                            opponent.factionButtonChecker() + "autoAssignAFBHits_" + tile.getPosition() + "_" + h,
                            "Auto-assign Hit" + (h == 1 ? "" : "s")));
                    buttons.add(Buttons.red(
                            opponent.factionButtonChecker() + "getDamageButtons_" + tile.getPosition() + "_afb",
                            "Manually Assign Hit" + (h == 1 ? "" : "s")));
                    buttons.add(Buttons.gray(
                            opponent.factionButtonChecker() + "cancelAFBHits_" + tile.getPosition() + "_" + h,
                            "Cancel a Hit"));
                }
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2, buttons);
            }
        } else {
            if (isFoWPrivateChannelRoll(player, event)) {
                if (rollType == CombatRollType.SpaceCannonOffence) {
                    // If roll was from pds button in private channel, send the result to the target
                    MessageHelper.sendMessageToChannel(
                            opponent.getCorrectChannel(),
                            opponent.getRepresentationUnfogged() + " "
                                    + FOWCombatThreadMirroring.parseCombatRollMessage(message, player));
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            "Roll result was sent to " + opponent.getRepresentationNoPing());
                } else if (rollType == CombatRollType.bombardment) {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentationUnfogged()
                                    + " This roll result is not automatically relayed. Please communicate the hits to the opponent manually.");
                }
            }
            if ((opponent.isDummy() || opponent.isNpc()) && h > 0) {
                List<Button> buttons = new ArrayList<>();
                if (combatOnHolder instanceof Planet) {
                    if (round2 > round) {
                        buttons.add(Buttons.blue(
                                opponent.dummyPlayerSpoof() + "combatRoll_" + tile.getPosition() + "_"
                                        + combatOnHolder.getName(),
                                "Roll Dice For Dummy for Combat Round #" + (round + 1)));
                    }
                    buttons.add(Buttons.green(
                            opponent.dummyPlayerSpoof() + "autoAssignGroundHits_" + combatOnHolder.getName() + "_" + h,
                            "Auto-assign Hit" + (h == 1 ? "" : "s") + " For Dummy"));
                    String msg = opponent.getRepresentationUnfogged() + " you may autoassign "
                            + StringHelper.pluralize(h, "hit") + ".";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
                } else {
                    String msg2 = opponent.getRepresentationNoPing() + ", you may automatically assign "
                            + (h == 1 ? "the hit" : "hits") + ".";
                    if (rollType == CombatRollType.AFB) {
                        buttons.add(Buttons.green(
                                opponent.dummyPlayerSpoof() + "autoAssignAFBHits_" + tile.getPosition() + "_" + h,
                                "Auto-assign Hit" + (h == 1 ? "" : "s For Dummy")));
                    } else {
                        buttons.add(Buttons.green(
                                opponent.dummyPlayerSpoof() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h,
                                "Auto-assign Hits For Dummy"));
                        msg2 += ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, h, event, true);
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);
                }
            }
        }

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

        if (rollType == CombatRollType.AFB && player.hasUnlockedBreakthrough("vyserixbt")) {
            VyserixBreakthroughHandler.offerMoraySystemButtons(event, game, player, tile, h);
        }

        if (rollType != CombatRollType.combatround
                && h >= 3
                && player.hasLeader("xytheriscommander")
                && !player.hasLeaderUnlocked("xytheriscommander")) {
            UnlockLeaderService.unlockLeader("xytheriscommander", game, player);
        }

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

        return h;
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

    // This roll was made from fow private channel and not from a combat thread
    private static boolean isFoWPrivateChannelRoll(Player player, GenericInteractionCreateEvent event) {
        return event.getMessageChannel().equals(player.getPrivateChannel());
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
        return rollForUnitsWithResult(
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
                        unitHolder)
                .message();
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
        String result = "";
        RollPayloadBuilder payloadBuilder = new RollPayloadBuilder();
        List<CombatRollPayload.CombatRollNote> delayedAfterTotalNotes = new ArrayList<>();

        List<NamedCombatModifierModel> mods = new ArrayList<>(autoMods);
        mods.addAll(tempMods);
        Set<NamedCombatModifierModel> set2 = new HashSet<>(mods);
        mods = new ArrayList<>(set2);

        List<NamedCombatModifierModel> modAndExtraRolls = new ArrayList<>(mods);
        modAndExtraRolls.addAll(extraRolls);
        Set<NamedCombatModifierModel> set = new HashSet<>(modAndExtraRolls);
        List<NamedCombatModifierModel> uniqueList = new ArrayList<>(set);
        Map<UnitModel, Integer> playerUnitsFlat = new HashMap<>();
        playerUnits.forEach((k, v) -> playerUnitsFlat.merge(k.getLeft(), v, Integer::sum));
        result += CombatMessageHelper.displayModifiers("With modifiers: \n", playerUnitsFlat, uniqueList);
        payloadBuilder.addModifierDisplays(
                uniqueList, playerUnitsFlat, player, opponent, game, rollType, activeSystem, unitHolder);

        // Actually roll for each unit
        int totalHits = 0;
        int letnevBTBoost = 0;
        double chanceOfAllHits = Math.nextDown(100.0);
        double chanceOfAllMiss = Math.nextDown(100.0);
        int maximumHits = 0;

        List<UnitModel> playerUnitsList =
                playerUnits.keySet().stream().map(Pair::getLeft).collect(Collectors.toList());
        List<UnitType> playerUnitTypes =
                playerUnitsList.stream().map(UnitModel::getUnitType).toList();
        boolean hacanFlagship = player.hasUnit("hacan_flagship") && playerUnitTypes.contains(UnitType.Flagship);
        boolean tkHacanWarsun = player.hasUnit("tk-fallofkenara") && playerUnitTypes.contains(UnitType.Warsun);
        List<Button> hacanFsButtons = new ArrayList<>();
        List<UnitType> hacanFsThalnosDestroyTypes = new ArrayList<>();
        int nearMisses = 0;
        boolean isThalnosReroll = "true".equalsIgnoreCase(game.getStoredValue("thalnosPlusOne"));

        int totalMisses = 0;
        UnitHolder space = activeSystem.getUnitHolders().get("space");
        StringBuilder extra = new StringBuilder();
        boolean usesX89c4 = player.hasTech("x89c4")
                && (rollType == CombatRollType.combatround || rollType == CombatRollType.bombardment)
                && (!"space".equalsIgnoreCase(unitHolder.getName()) || rollType == CombatRollType.bombardment);
        if (rollType == CombatRollType.combatround
                && ButtonHelper.doesPlayerHaveFSHere("letnev_flagship", player, activeSystem)
                && "space".equalsIgnoreCase(unitHolder.getName())
                && unitHolder.getDamagedUnitCount(UnitType.Flagship, player.getColorID()) > 0) {
            result = "Repaired the Arc Secundus at start of this combat round with its ability.\n" + result;
            payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                    CombatRollNoteType.UNIT_REPAIRED,
                    CombatRollNotePlacement.BEFORE_MODIFIERS,
                    "letnev_flagship",
                    "letnev_flagship",
                    1,
                    Map.of("timing", "START_OF_COMBAT_ROUND")));
            activeSystem.removeUnitDamage(
                    unitHolder.getName(), Mapper.getUnitKey(AliasHandler.resolveUnit("fs"), player.getColorID()), 1);
        }
        if (rollType == CombatRollType.combatround
                && player.ownsUnit("naaz_voltron")
                && "space".equalsIgnoreCase(unitHolder.getName())
                && unitHolder.getDamagedUnitCount(UnitType.Mech, player.getColorID()) > 0) {
            result = "The Eidolon Maximum self-repaired at the start of this combat round.\n" + result;
            payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                    CombatRollNoteType.UNIT_REPAIRED,
                    CombatRollNotePlacement.BEFORE_MODIFIERS,
                    "naaz_voltron",
                    "naaz_voltron",
                    1,
                    Map.of("timing", "START_OF_COMBAT_ROUND")));
            activeSystem.removeUnitDamage(
                    unitHolder.getName(), Mapper.getUnitKey(AliasHandler.resolveUnit("mf"), player.getColorID()), 1);
        }
        StringBuilder resultBuilder = new StringBuilder(result);
        boolean metaliVoidCounted = false;
        String highestValueSingleUnitKey = "highestValueSingleUnit" + player.getFaction();
        String storedHighestValueUnit = game.getStoredValue(highestValueSingleUnitKey);
        boolean unitUndecided = storedHighestValueUnit.isEmpty()
                || playerUnits.keySet().stream()
                        .noneMatch(k -> k.getLeft().getAsyncId().equalsIgnoreCase(storedHighestValueUnit));
        if (!storedHighestValueUnit.isEmpty() && unitUndecided) {
            // A manual Gravleash/Supercharge choice (chooseGravleash_) that isn't part of this combat
            // round - wrong tile, or the chosen unit has since died/retreated - would otherwise block
            // auto-pick forever, since this flag never becomes true again once set.
            game.removeStoredValue(highestValueSingleUnitKey);
        }
        if (rollType == CombatRollType.combatround
                && (player.hasTech("tf-supercharge")
                        || (player.hasUnlockedBreakthrough("letnevbt")
                                && "space".equalsIgnoreCase(unitHolder.getName())))) {
            int max = 0;
            for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : playerUnits.entrySet()) {
                UnitModel unitModel = entry.getKey().getLeft();
                UnitHolder perUnitHolder = entry.getKey().getRight();
                int numOfUnit = entry.getValue();
                int extraRollsForUnit = CombatModHelper.getCombinedModifierForUnit(
                        unitModel,
                        numOfUnit,
                        extraRolls,
                        player,
                        opponent,
                        game,
                        playerUnitsList,
                        CombatRollType.combatround,
                        activeSystem,
                        perUnitHolder);
                unitModel.getCombatDieCountForAbility(CombatRollType.combatround, player);
                int numRollsPerUnit;
                CombatStatsService.CombatRoundProfile combatRoundProfile = CombatStatsService.getCombatRoundProfile(
                        true, unitModel, player, activeSystem, opponent, false);
                numRollsPerUnit = combatRoundProfile.diceCount();
                if (numRollsPerUnit + Math.min(1, extraRollsForUnit) > max && unitUndecided) {
                    max = numRollsPerUnit + Math.min(1, extraRollsForUnit);
                    game.setStoredValue("highestValueSingleUnit" + player.getFaction(), unitModel.getAsyncId());
                }
                if (player.hasUnlockedBreakthrough("letnevbt") && unitModel.getIsShip()) {
                    letnevBTBoost++;
                }
            }
            if (player.hasTech("tf-supercharge")) {
                resultBuilder.append("Applied +2 to the rolls of 1 unit with _Supercharge_.\n");
                payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                        CombatRollNoteType.SINGLE_UNIT_ROLL_MOD_APPLIED,
                        CombatRollNotePlacement.BEFORE_UNIT_ROLLS,
                        "tf-supercharge",
                        game.getStoredValue("highestValueSingleUnit" + player.getFaction()),
                        1,
                        Map.of("modifier", "2")));
                letnevBTBoost = 2;
            } else {
                resultBuilder
                        .append("Applied +")
                        .append(letnevBTBoost)
                        .append(" to the rolls of 1 unit with _Gravleash Maneuvers_.\n");
                payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                        CombatRollNoteType.SINGLE_UNIT_ROLL_MOD_APPLIED,
                        CombatRollNotePlacement.BEFORE_UNIT_ROLLS,
                        "letnevbt",
                        game.getStoredValue("highestValueSingleUnit" + player.getFaction()),
                        1,
                        Map.of("modifier", Integer.toString(letnevBTBoost))));
            }
        }
        MergeResult mergeResult = mergeAndDetectDivergence(
                playerUnits, mods, rollType, player, opponent, game, playerUnitsList, activeSystem);
        playerUnits = mergeResult.units();
        Set<String> divergingModels = mergeResult.divergingModels();
        Set<String> consumedBestMods = new HashSet<>();
        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : playerUnits.entrySet()) {
            UnitModel unitModel = entry.getKey().getLeft();
            UnitHolder perUnitHolder = entry.getKey().getRight();
            int numOfUnit = entry.getValue();
            UnitType unitType = unitModel.getUnitType();

            int toHit = unitModel.getCombatDieHitsOnForAbility(rollType, player);
            int modifierToHit = CombatModHelper.getCombinedModifierForUnit(
                    unitModel,
                    numOfUnit,
                    mods,
                    player,
                    opponent,
                    game,
                    playerUnitsList,
                    rollType,
                    activeSystem,
                    perUnitHolder);
            List<NamedCombatModifierModel> availableExtraRolls = extraRolls.stream()
                    .filter(m -> !consumedBestMods.contains(m.getModifier().getAlias()))
                    .collect(Collectors.toList());
            int extraRollsForUnit = CombatModHelper.getCombinedModifierForUnit(
                    unitModel,
                    numOfUnit,
                    availableExtraRolls,
                    player,
                    opponent,
                    game,
                    playerUnitsList,
                    rollType,
                    activeSystem,
                    perUnitHolder);
            if (extraRollsForUnit > 0) {
                for (NamedCombatModifierModel m : availableExtraRolls) {
                    String sc = m.getModifier().getScope();
                    if (("_best_".equals(sc) || "_bestCap_".equals(sc) || (sc != null && sc.contains("_mostdice_")))
                            && Boolean.TRUE.equals(m.getModifier()
                                    .isInScopeForUnit(unitModel, playerUnitsList, rollType, game, player))) {
                        consumedBestMods.add(m.getModifier().getAlias());
                    }
                }
            }

            int numRollsPerUnit = unitModel.getCombatDieCountForAbility(rollType, player);
            if (rollType == CombatRollType.combatround) {
                CombatStatsService.CombatRoundProfile combatRoundProfile = CombatStatsService.getCombatRoundProfile(
                        true, unitModel, player, activeSystem, opponent, false);
                toHit = combatRoundProfile.hitsOn();
                numRollsPerUnit = combatRoundProfile.diceCount();
            }

            boolean extraRollsCount = false;
            if ((numRollsPerUnit > 1 || extraRollsForUnit > 0) && isThalnosReroll) {
                extraRollsCount = true;
                numRollsPerUnit = 1;
                extraRollsForUnit = 0;
            }
            if (rollType == CombatRollType.SpaceCannonOffence
                    && numRollsPerUnit == 3
                    && "spacedock".equalsIgnoreCase(unitModel.getBaseType())) {
                numOfUnit = 1;
                game.setStoredValue("EBSFaction", "");
            }
            if (rollType == CombatRollType.bombardment
                    && numRollsPerUnit > 1
                    && "destroyer".equalsIgnoreCase(unitModel.getBaseType())) {
                numOfUnit = 1;
                game.setStoredValue("TnelisAgentFaction", "");
            }
            boolean usingMetali = unitModel.getAfbDieCount() == 0 && unitModel.getAfbDieCount(player) == 3;
            if (rollType == CombatRollType.AFB && usingMetali) {
                numOfUnit = 1;
                if (!metaliVoidCounted) {
                    metaliVoidCounted = true;
                } else {
                    continue;
                }
            }
            List<String> singleUnitUse = new ArrayList<>(List.of("no"));
            if (rollType == CombatRollType.combatround
                    && !isThalnosReroll
                    && (player.hasTech("tf-supercharge")
                            || (player.hasUnlockedBreakthrough("letnevbt")
                                    && "space".equalsIgnoreCase(unitHolder.getName())))) {
                if (game.getStoredValue("highestValueSingleUnit" + player.getFaction())
                        .equalsIgnoreCase(unitModel.getAsyncId())) {
                    singleUnitUse = new ArrayList<>(List.of("singleUnit", "RestOfUnits"));
                    game.removeStoredValue("highestValueSingleUnit" + player.getFaction());
                }
            }
            int ogNumOfUnit = numOfUnit;
            for (String singleUnit : singleUnitUse) {
                int numRolls = (ogNumOfUnit * numRollsPerUnit) + extraRollsForUnit;
                if ("singleUnit".equals(singleUnit)) {
                    numRolls = numRollsPerUnit + Math.min(1, extraRollsForUnit);
                    modifierToHit += letnevBTBoost;
                    numOfUnit = 1;
                }
                if ("RestOfUnits".equals(singleUnit)) {
                    modifierToHit -= letnevBTBoost;
                    numOfUnit = ogNumOfUnit - 1;
                    numRolls -= numRollsPerUnit + Math.min(1, extraRollsForUnit);
                }
                if (numRolls == 0) {
                    continue;
                }
                RollSegmentType segmentType =
                        switch (singleUnit) {
                            case "singleUnit" ->
                                player.hasTech("tf-supercharge")
                                        ? RollSegmentType.SUPERCHARGE_SELECTED_UNIT
                                        : RollSegmentType.GRAVLEASH_SELECTED_UNIT;
                            case "RestOfUnits" ->
                                player.hasTech("tf-supercharge")
                                        ? RollSegmentType.SUPERCHARGE_REST
                                        : RollSegmentType.GRAVLEASH_REST;
                            default -> RollSegmentType.PRIMARY;
                        };
                List<Die> resultRolls = DiceHelper.rollDice(toHit - modifierToHit, numRolls);
                int mult = 1;

                player.setExpectedHitsTimes10(
                        player.getExpectedHitsTimes10() + (numRolls * mult * (11 - toHit + modifierToHit)));
                chanceOfAllHits *= Math.pow((11 - toHit + modifierToHit) / 10.0, numRolls * mult);
                chanceOfAllMiss *= Math.pow((toHit - modifierToHit - 1) / 10.0, numRolls * mult);
                maximumHits += numRolls * mult;
                if (usesX89c4) {
                    mult = 2;
                }
                int hitRolls = DiceHelper.countSuccesses(resultRolls);
                if (unitModel.getUnitType() == UnitType.Flagship
                        && ValefarZService.hasFlagshipAbility(game, player, "jolnar_flagship")) {
                    chanceOfAllHits *= Math.pow(2.0 / (11 - toHit + modifierToHit), numRolls * mult);
                    for (Die die : resultRolls) {
                        if (die.getResult() >= 9) {
                            hitRolls += 2;
                        }
                        maximumHits += 2;
                    }
                }
                if (unitModel.getUnitType() == UnitType.Infantry && player.hasUnit("tk-tekklarelite")) {
                    chanceOfAllHits *= Math.pow(2.0 / (11 - toHit + modifierToHit), numRolls * mult);
                    for (Die die : resultRolls) {
                        if (die.isSuccess()) {
                            hitRolls += 1;
                        }
                        maximumHits += 1;
                    }
                }

                if ((rollType == CombatRollType.SpaceCannonDefence || rollType == CombatRollType.SpaceCannonOffence)
                        && game.playerHasLeaderUnlockedOrAlliance(player, "zephyrioncommander")) {
                    for (Die die : resultRolls) {
                        if (die.getResult() == 10) hitRolls += 1;
                        maximumHits += 1;
                    }
                }
                if (rollType == CombatRollType.bombardment && "tf-dragonfreed".equalsIgnoreCase(unitModel.getId())) {
                    if (!game.isFowMode() && hitRolls > 0) {

                        String bombardPlanet2 = game.getStoredValue("bombardmentTarget" + player.getFaction());
                        Tile tile = game.getTileByPosition(game.getActiveSystem());
                        if (!bombardPlanet2.isEmpty()) {
                            tile = game.getTileFromPlanet(bombardPlanet2);
                        }
                        for (String pos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true)) {
                            Tile tile2 = game.getTileByPosition(pos);
                            for (UnitHolder uh : tile2.getPlanetUnitHolders()) {
                                List<Button> buttons = new ArrayList<>();
                                if (uh.getName().equalsIgnoreCase(bombardPlanet2)) {
                                    continue;
                                }
                                buttons.add(Buttons.red(
                                        "getDamageButtons_" + tile2.getPosition() + "_bombardment",
                                        "Assign Hit" + (hitRolls == 1 ? "" : "s")));
                                for (Player p2 : game.getRealPlayersNNeutral()) {
                                    if (FoWHelper.playerHasUnitsOnPlanet(p2, uh)) {
                                        if (p2.isRealPlayer()) {
                                            MessageHelper.sendMessageToChannelWithButtons(
                                                    game.isFowMode()
                                                            ? p2.getCorrectChannel()
                                                            : event.getMessageChannel(),
                                                    p2.getRepresentation()
                                                            + ", please assign the Dragon BOMBARDMENT hit"
                                                            + (hitRolls == 1 ? "" : "s") + " on "
                                                            + Helper.getPlanetRepresentation(uh.getName(), game) + ".",
                                                    buttons);
                                        } else {
                                            List<Button> buttons2 = new ArrayList<>();
                                            buttons2.add(Buttons.green(
                                                    p2.dummyPlayerSpoof() + "autoAssignGroundHits_" + uh.getName() + "_"
                                                            + hitRolls,
                                                    "Auto-assign Hit" + (hitRolls == 1 ? "" : "s") + " For Dummy"));
                                            MessageHelper.sendMessageToChannelWithButtons(
                                                    game.isFowMode()
                                                            ? player.getCorrectChannel()
                                                            : event.getMessageChannel(),
                                                    player.getRepresentation()
                                                            + ", please assign the Dragon BOMBARDMENT hit"
                                                            + (hitRolls == 1 ? "" : "s") + " for the dummy player on "
                                                            + Helper.getPlanetRepresentation(uh.getName(), game) + ".",
                                                    buttons2);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if ("sigma_jolnar_flagship_1".equalsIgnoreCase(unitModel.getId())
                        || "sigma_jolnar_flagship_2".equalsIgnoreCase(unitModel.getId())) {
                    int additionalDice = hitRolls;
                    while (hitRolls < 100 && additionalDice > 0) {
                        List<Die> additionalResultRolls = DiceHelper.rollDice(toHit - modifierToHit, additionalDice);
                        additionalDice = DiceHelper.countSuccesses(additionalResultRolls);
                        hitRolls += additionalDice;
                        resultRolls.addAll(additionalResultRolls);
                    }
                }
                Player gloryHolder = Helper.getPlayerFromAbility(game, "valor");
                if (gloryHolder == null) {
                    for (Player p2 : game.getRealPlayers()) {
                        if (p2.hasTech("tf-glorioushalls")) {
                            gloryHolder = p2;
                        }
                    }
                }
                if (rollType == CombatRollType.combatround
                        && gloryHolder != null
                        && ButtonHelperAgents.getGloryTokenTiles(game).contains(activeSystem)) {
                    ButtonHelperAbilities.readyBannerHalls(game);
                    chanceOfAllHits *= Math.pow(1.0 / (11 - toHit + modifierToHit), numRolls * mult);
                    for (Die die : resultRolls) {
                        if (die.getResult() >= 10) {
                            hitRolls += 1;
                            String valor = "Valor";
                            if (game.isTwilightsFallMode()) {
                                valor = "Glorious Halls";
                            }
                            MessageHelper.sendMessageToChannel(
                                    event.getMessageChannel(),
                                    player.getRepresentation() + " got an extra hit due to the **" + valor
                                            + "** ability (it has been accounted for in the hit count).");
                        }
                        maximumHits += 1;
                    }
                }
                if (player.hasTech("tf-valortf")) {
                    chanceOfAllHits *= Math.pow(1.0 / (11 - toHit + modifierToHit), numRolls * mult);
                    for (Die die : resultRolls) {
                        if (die.getResult() >= 10) {
                            hitRolls += 1;
                            MessageHelper.sendMessageToChannel(
                                    event.getMessageChannel(),
                                    player.getRepresentation()
                                            + " got an extra hit due to the **Valor** ability (it has been accounted for in the hit count).");
                        }
                        maximumHits += 1;
                    }
                }
                if ("vaden_flagship".equalsIgnoreCase(unitModel.getId()) && rollType == CombatRollType.bombardment) {
                    for (Die die : resultRolls) {
                        if (die.getResult() > 4) {
                            player.setTg(player.getTg() + 1);
                            ButtonHelperAbilities.pillageCheck(player, game);
                            ButtonHelperAgents.resolveArtunoCheck(player, 1);
                            MessageHelper.sendMessageToChannel(
                                    player.getCorrectChannel(),
                                    player.getRepresentation()
                                            + " gained 1 trade good due to hitting on a BOMBARDMENT roll with the Aurum Vadra (the Vaden flagship).");
                            break;
                        }
                    }
                }
                if ("belkosea_mech".equalsIgnoreCase(unitModel.getId())) {
                    if (hitRolls > 0) {
                        ButtonHelperFactionSpecific.offerMahactInfButtons(player, game);
                        MessageHelper.sendMessageToChannel(
                                event.getMessageChannel(),
                                player.getRepresentation() + " please gain or convert 1 commodity a total of "
                                        + StringHelper.pluralize(hitRolls, "time")
                                        + " due to your Uzean Wardog mech ability.");
                    }
                }
                int misses = numRolls - hitRolls;
                totalMisses += misses;

                // Handle Thalnos Reroll things here
                if (isThalnosReroll) {
                    if (hacanFlagship) {
                        misses -= resultRolls.stream()
                                .filter(Die::eligibleForHeartPlus)
                                .toList()
                                .size();
                        hacanFsButtons.add(buildHacanFlagshipThalnosButton(player, unitType, resultRolls));
                        if (!extraRollsCount) {
                            // later we will build one destroy button to rule them all
                            hacanFsThalnosDestroyTypes.add(unitType);
                        }
                    } else if (tkHacanWarsun) {
                        // do not immediately destroy anything here
                        misses = 0;
                        hacanFsButtons.add(buildTkHacanWSThalnosButton(resultRolls));
                    }
                    if ((hacanFlagship || tkHacanWarsun) && !extraRollsCount) {
                        // later we will build one destroy button to rule them all
                        hacanFsThalnosDestroyTypes.add(unitType);
                    }

                    if (misses > 0 && !extraRollsCount) {
                        extra.append(player.getFactionEmoji())
                                .append(" destroyed ")
                                .append(misses)
                                .append(" of their own ")
                                .append(unitModel.getName())
                                .append(misses == 1 ? "" : "s")
                                .append(" due to ")
                                .append(misses == 1 ? "a Thalnos miss" : "Thalnos misses")
                                .append(".");
                        delayedAfterTotalNotes.add(new CombatRollPayload.CombatRollNote(
                                CombatRollNoteType.UNIT_DESTROYED_FROM_ROLL,
                                CombatRollNotePlacement.AFTER_TOTAL,
                                "thalnos",
                                unitModel.getId(),
                                misses,
                                Map.of("actorEmoji", player.getFactionEmoji(), "unitName", unitModel.getName())));
                        thalnosUnits(event, game, player, misses, unitModel.getUnitType());
                    } else {
                        if (misses > 0) {
                            MessageHelper.sendMessageToChannel(
                                    event.getMessageChannel(),
                                    player.getFactionEmoji() + " had " + misses + " " + unitModel.getName()
                                            + (misses == 1 ? "" : "s") + " miss" + (misses == 1 ? "" : "es")
                                            + " on a Thalnos roll, but no units were removed due to extra rolls being unaccounted for.");
                        }
                    }
                }

                totalHits += hitRolls;

                String holderLabel = divergingModels.contains(unitModel.getId()) && perUnitHolder instanceof Planet p
                        ? "on **" + Helper.getPlanetRepresentationNoResInf(p.getName(), game) + "**"
                        : "";
                String unitRoll = CombatMessageHelper.displayUnitRoll(
                        unitModel,
                        toHit,
                        modifierToHit,
                        numOfUnit,
                        numRollsPerUnit,
                        extraRollsForUnit,
                        resultRolls,
                        hitRolls,
                        holderLabel);
                resultBuilder.append(unitRoll);
                payloadBuilder.addUnitRoll(
                        unitModel,
                        toHit,
                        modifierToHit,
                        numOfUnit,
                        numRollsPerUnit,
                        extraRollsForUnit,
                        segmentType,
                        resultRolls,
                        hitRolls,
                        DieRollSource.PRIMARY);
                List<DiceHelper.Die> resultRolls2 = new ArrayList<>();
                int numMisses = numRolls - hitRolls;
                if (player.ownsUnit("tf-justicerrail") && rollType == CombatRollType.SpaceCannonOffence) {
                    game.setStoredValue(player.getFaction() + "graviton", "yes");
                }
                if ((game.playerHasLeaderUnlockedOrAlliance(player, "jolnarcommander")
                                || player.hasTech("tf-tacticalbrilliance"))
                        && rollType != CombatRollType.combatround) {

                    if (opponent == player && rollType == CombatRollType.bombardment && player.hasTech("proxima")) {
                        if (hitRolls > 0) {
                            resultRolls2 = DiceHelper.rollDice(toHit - modifierToHit, hitRolls);
                            // Very important to remove the rerolled dice from the original dice pool
                            resultRolls.removeIf(Die::isSuccess);
                            player.setExpectedHitsTimes10(
                                    player.getExpectedHitsTimes10() + (hitRolls * (11 - toHit + modifierToHit)));
                            int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                            totalHits += hitRolls2;
                            totalHits -= hitRolls;
                            String unitRoll2 = CombatMessageHelper.displayUnitRoll(
                                    unitModel,
                                    toHit,
                                    modifierToHit,
                                    numOfUnit,
                                    numRollsPerUnit,
                                    extraRollsForUnit,
                                    resultRolls2,
                                    hitRolls2);
                            payloadBuilder.addUnitRoll(
                                    unitModel,
                                    toHit,
                                    modifierToHit,
                                    numOfUnit,
                                    numRollsPerUnit,
                                    extraRollsForUnit,
                                    RollSegmentType.JOL_NAR_COMMANDER_REROLL_HITS,
                                    resultRolls2,
                                    hitRolls2,
                                    DieRollSource.REROLL_HIT);
                            resultBuilder
                                    .append("Rerolling ")
                                    .append(hitRolls)
                                    .append(" hit")
                                    .append(hitRolls == 1 ? "" : "s")
                                    .append(" due to Ta Zern, the Jol-Nar Commander:\n")
                                    .append(unitRoll2);
                        }
                    } else {
                        if (numMisses > 0) {
                            resultRolls2 = DiceHelper.rollDice(toHit - modifierToHit, numMisses);
                            // Very important to remove the rerolled dice from the original dice pool
                            resultRolls.removeIf(Predicate.not(Die::isSuccess));
                            player.setExpectedHitsTimes10(
                                    player.getExpectedHitsTimes10() + (numMisses * (11 - toHit + modifierToHit)));
                            chanceOfAllHits *= Math.pow((11 - toHit + modifierToHit) / 10.0, numMisses);
                            chanceOfAllMiss *= Math.pow((toHit - modifierToHit - 1) / 10.0, numMisses);
                            maximumHits += numRolls * mult;
                            int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                            totalHits += hitRolls2;
                            String unitRoll2 = CombatMessageHelper.displayUnitRoll(
                                    unitModel,
                                    toHit,
                                    modifierToHit,
                                    numOfUnit,
                                    numRollsPerUnit,
                                    0,
                                    resultRolls2,
                                    hitRolls2);
                            payloadBuilder.addUnitRoll(
                                    unitModel,
                                    toHit,
                                    modifierToHit,
                                    numOfUnit,
                                    numRollsPerUnit,
                                    0,
                                    RollSegmentType.JOL_NAR_COMMANDER_REROLL_MISSES,
                                    resultRolls2,
                                    hitRolls2,
                                    DieRollSource.REROLL_MISS);
                            resultBuilder
                                    .append("Rerolling ")
                                    .append(numMisses)
                                    .append(" miss")
                                    .append(numMisses == 1 ? "" : "es")
                                    .append(" due to Ta Zern, the Jol-Nar Commander:\n")
                                    .append(unitRoll2);
                        }
                    }
                }
                if (IronLeadersHandler.shouldAutoRerollCommanderMechMisses(game, player, unitModel, rollType)
                        && numMisses > 0) {
                    resultRolls2 = DiceHelper.rollDice(toHit - modifierToHit, numMisses);
                    // Very important to remove the rerolled dice from the original dice pool
                    resultRolls.removeIf(Predicate.not(Die::isSuccess));
                    player.setExpectedHitsTimes10(
                            player.getExpectedHitsTimes10() + (numMisses * (11 - toHit + modifierToHit)));
                    chanceOfAllHits *= Math.pow((11 - toHit + modifierToHit) / 10.0, numMisses);
                    chanceOfAllMiss *= Math.pow((toHit - modifierToHit - 1) / 10.0, numMisses);
                    maximumHits += numRolls * mult;
                    int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                    totalHits += hitRolls2;
                    String unitRoll2 = CombatMessageHelper.displayUnitRoll(
                            unitModel, toHit, modifierToHit, numOfUnit, numRollsPerUnit, 0, resultRolls2, hitRolls2);
                    payloadBuilder.addUnitRoll(
                            unitModel,
                            toHit,
                            modifierToHit,
                            numOfUnit,
                            numRollsPerUnit,
                            0,
                            RollSegmentType.IRON_COMMANDER_REROLL_MISSES,
                            resultRolls2,
                            hitRolls2,
                            DieRollSource.REROLL_MISS);
                    resultBuilder
                            .append("Rerolling ")
                            .append(numMisses)
                            .append(" miss")
                            .append(numMisses == 1 ? "" : "es")
                            .append(" due to Captain Vakros, the Iron Tide Commander:\n")
                            .append(unitRoll2);
                    resultRolls.addAll(resultRolls2);
                    numMisses -= hitRolls2;
                    resultRolls2 = new ArrayList<>();
                }
                if (rollType == CombatRollType.SpaceCannonOffence || rollType == CombatRollType.SpaceCannonDefence) {
                    if (player.ownsUnit("gledge_pds2") && totalHits > 0) {
                        String msg = player.getRepresentation()
                                + ", use the buttons to explore a planet with the PDS that got the hit. It should be "
                                + "noted that the bot has no idea which PDS rolled which dice, but default practice would be to go from lowest tile position to highest"
                                + ", with _Plasma Scoring_ applying to the last die. You can specify any order before rolling though.";
                        for (int x = 0; x < totalHits; x++) {
                            List<Button> buttons = new ArrayList<>();
                            for (Tile tile : CheckUnitContainmentService.getTilesContainingPlayersUnits(
                                    game, player, UnitType.Pds)) {
                                for (String planet : ButtonHelper.getPlanetsWithSpecificUnit(player, tile, "pds")) {
                                    Planet planetUnit = game.getUnitHolderFromPlanet(planet);
                                    if (planetUnit == null) {
                                        continue;
                                    }
                                    planet = planetUnit.getName();
                                    if (isNotBlank(planetUnit.getOriginalPlanetType())
                                            && player.getPlanetsAllianceMode().contains(planet)
                                            && FoWHelper.playerHasUnitsOnPlanet(player, tile, planet)) {
                                        List<Button> planetButtons =
                                                ButtonHelper.getPlanetExplorationButtons(game, planetUnit, player);
                                        buttons.addAll(planetButtons);
                                    }
                                }
                            }
                            buttons.add(Buttons.red("deleteButtons", "No Valid Exploration"));
                            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                        }
                    }
                    if (player.ownsUnit("gledge_pds")) {
                        String msg = player.getRepresentation()
                                + " use the buttons to explore a planet with the PDS that got the hit.";
                        for (DiceHelper.Die die : resultRolls) {
                            if (die.getResult() < 9) {
                                continue;
                            }
                            List<Button> buttons = new ArrayList<>();
                            for (String planet : ButtonHelper.getPlanetsWithSpecificUnit(player, activeSystem, "pds")) {
                                Planet planetUnit = game.getUnitHolderFromPlanet(planet);
                                if (planetUnit == null) {
                                    continue;
                                }
                                planet = planetUnit.getName();
                                if (isNotBlank(planetUnit.getOriginalPlanetType())
                                        && player.getPlanetsAllianceMode().contains(planet)
                                        && FoWHelper.playerHasUnitsOnPlanet(player, activeSystem, planet)) {
                                    List<Button> planetButtons =
                                            ButtonHelper.getPlanetExplorationButtons(game, planetUnit, player);
                                    buttons.addAll(planetButtons);
                                }
                            }
                            buttons.add(Buttons.red("deleteButtons", "No Valid Exploration"));
                            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                        }
                    }
                }
                if (game.playerHasLeaderUnlockedOrAlliance(player, "kaltrimcommander")) {
                    int num1s = 0;
                    for (DiceHelper.Die die : resultRolls) {
                        if (die.getResult() == 1) {
                            num1s++;
                        }
                    }
                    if (num1s > 0) {
                        resultRolls2 = DiceHelper.rollDice(toHit - modifierToHit, num1s);
                        player.setExpectedHitsTimes10(
                                player.getExpectedHitsTimes10() + (num1s * (11 - toHit + modifierToHit)));
                        int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                        totalHits += hitRolls2;
                        String unitRoll2 = CombatMessageHelper.displayUnitRoll(
                                unitModel,
                                toHit,
                                modifierToHit,
                                numOfUnit,
                                numRollsPerUnit,
                                0,
                                resultRolls2,
                                hitRolls2);
                        payloadBuilder.addUnitRoll(
                                unitModel,
                                toHit,
                                modifierToHit,
                                numOfUnit,
                                numRollsPerUnit,
                                0,
                                RollSegmentType.KALTRIM_COMMANDER_REROLL_ONES,
                                resultRolls2,
                                hitRolls2,
                                DieRollSource.REROLL_ONE);
                        resultBuilder
                                .append("Rerolling ")
                                .append(num1s)
                                .append(" roll")
                                .append(num1s == 1 ? "" : "s")
                                .append(" of 1 due to the Kaltrim Commander:\n ")
                                .append(unitRoll2);
                    }
                }

                if (game.getStoredValue("munitionsReserves").equalsIgnoreCase(player.getFaction())
                        && rollType == CombatRollType.combatround
                        && numMisses > 0
                        && !isThalnosReroll) { // do not munitions after thalnos
                    resultRolls2 = DiceHelper.rollDice(toHit - modifierToHit, numMisses);
                    // Very important to remove the rerolled dice from the original dice pool
                    resultRolls.removeIf(Predicate.not(Die::isSuccess));

                    player.setExpectedHitsTimes10(
                            player.getExpectedHitsTimes10() + (numMisses * (11 - toHit + modifierToHit)));
                    chanceOfAllHits *= Math.pow((11 - toHit + modifierToHit) / 10.0, numMisses);
                    chanceOfAllMiss *= Math.pow((toHit - modifierToHit - 1) / 10.0, numMisses);
                    maximumHits += numRolls * mult;
                    int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                    if (gloryHolder != null
                            && ButtonHelperAgents.getGloryTokenTiles(game).contains(activeSystem)) {
                        for (DiceHelper.Die die : resultRolls2) {
                            if (die.getResult() > 9) {
                                hitRolls2 += 1;
                                MessageHelper.sendMessageToChannel(
                                        event.getMessageChannel(),
                                        player.getRepresentation()
                                                + " got an extra hit due to the **Valor** ability (it has been accounted for in the hit count).");
                            }
                        }
                    }
                    totalHits += hitRolls2;
                    String unitRoll2 = CombatMessageHelper.displayUnitRoll(
                            unitModel, toHit, modifierToHit, numOfUnit, numRollsPerUnit, 0, resultRolls2, hitRolls2);
                    payloadBuilder.addUnitRoll(
                            unitModel,
                            toHit,
                            modifierToHit,
                            numOfUnit,
                            numRollsPerUnit,
                            0,
                            RollSegmentType.MUNITIONS_RESERVES_REROLL,
                            resultRolls2,
                            hitRolls2,
                            DieRollSource.MUNITIONS_RESERVES);
                    resultBuilder
                            .append("**Munitions Reserve** rerolling ")
                            .append(numMisses)
                            .append(" miss")
                            .append(numMisses == 1 ? "" : "es")
                            .append(": ")
                            .append(unitRoll2);
                    // these dice are eligible for further rerolls
                    resultRolls.addAll(resultRolls2);
                    resultRolls2.clear();
                }

                if (game.playerHasLeaderUnlockedOrAlliance(player, "kaltrimcommander")) {
                    int num1s = 0;
                    for (DiceHelper.Die die : resultRolls) {
                        if (die.getResult() == 1) {
                            num1s++;
                        }
                    }
                    // Very important to remove the rerolled dice from the original dice pool
                    resultRolls.removeIf(d -> d.getResult() == 1);

                    if (num1s > 0) {
                        resultRolls2 = DiceHelper.rollDice(toHit - modifierToHit, num1s);
                        player.setExpectedHitsTimes10(
                                player.getExpectedHitsTimes10() + (num1s * (11 - toHit + modifierToHit)));
                        int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                        totalHits += hitRolls2;
                        String unitRoll2 = CombatMessageHelper.displayUnitRoll(
                                unitModel,
                                toHit,
                                modifierToHit,
                                numOfUnit,
                                numRollsPerUnit,
                                0,
                                resultRolls2,
                                hitRolls2);
                        resultBuilder
                                .append("Rerolling ")
                                .append(num1s)
                                .append(" roll")
                                .append(num1s == 1 ? "" : "s")
                                .append(" of 1 due to the Kaltrim Commander:\n ")
                                .append(unitRoll2);
                    }
                }

                int argentInfKills = 0;
                if (player != opponent
                        && ("argent_destroyer2".equalsIgnoreCase(unitModel.getId())
                                || "tf-swa".equalsIgnoreCase(unitModel.getId()))
                        && rollType == CombatRollType.AFB
                        && space.getUnitCount(Units.UnitType.Infantry, opponent.getColor()) > 0) {
                    for (DiceHelper.Die die : resultRolls) {
                        if (die.getResult() > 8) {
                            argentInfKills++;
                        }
                    }
                    for (DiceHelper.Die die : resultRolls2) {
                        if (die.getResult() > 8) {
                            argentInfKills++;
                        }
                    }
                    argentInfKills =
                            Math.min(argentInfKills, space.getUnitCount(Units.UnitType.Infantry, opponent.getColor()));
                }
                if (totalHits > 0
                        && "neutral".equalsIgnoreCase(player.getFaction())
                        && game.getStoredValue("mercenarycaptaintrigged").isEmpty()) {
                    for (Player p : game.getRealPlayers()) {
                        if (p.hasTech("tf-mercenarycaptains")) {
                            p.setCommodities(p.getCommodities() + 1);
                            MessageHelper.sendMessageToChannel(
                                    p.getCorrectChannel(),
                                    p.getRepresentation()
                                            + " you gained 1 commodity due to the mercenary captains ability.");
                            game.setStoredValue("mercenarycaptaintrigged", "yes");
                        }
                    }
                }
                if (argentInfKills > 0) {
                    String kills = "\nDue to the Strike Wing Alpha II destroyer ability, " + argentInfKills + " of "
                            + opponent.getRepresentation(false, true) + " infantry were destroyed\n";
                    resultBuilder.append(kills);
                    payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                            CombatRollNoteType.OPPONENT_UNIT_DESTROYED_FROM_ROLL,
                            CombatRollNotePlacement.AFTER_UNIT_ROLLS,
                            unitModel.getId(),
                            "infantry",
                            argentInfKills,
                            Map.of("opponent", opponent.getRepresentation(false, true))));
                    UnitKey inf = Units.getUnitKey(UnitType.Infantry, opponent.getColorID());
                    DestroyUnitService.destroyUnit(event, activeSystem, game, inf, argentInfKills, space, true);
                }

                // Accumulate all the near misses so we can send a hacan flagship button at the very end of rolling
                nearMisses += (int) IterableUtils.countMatches(resultRolls, Die::eligibleForHeartPlus);
                nearMisses += (int) IterableUtils.countMatches(resultRolls2, Die::eligibleForHeartPlus);
            }
        }
        result = resultBuilder.toString();
        player.setActualHits(player.getActualHits() + totalHits);
        if ((chanceOfAllHits <= 2.0) && (totalHits == maximumHits)) {
            game.setStoredValue("surprisingDiceRoll", "hits");
        } else if ((chanceOfAllMiss <= 2.0) && (totalHits == 0)) {
            game.setStoredValue("surprisingDiceRoll", "miss");
        } else {
            game.setStoredValue("surprisingDiceRoll", "none");
        }

        boolean whiff = maximumHits > 0 && totalHits == 0;
        boolean slam = maximumHits > 0 && totalHits == maximumHits;
        if (usesX89c4) {
            totalHits *= 2;
        }
        if (game.isConventionsOfWarAbandonedMode() && rollType == CombatRollType.bombardment) {
            totalHits *= 3;
        }
        boolean useDoubleBoomEmoji = usesX89c4;
        if (player.hasStoredValue("RazeFaction") && rollType == CombatRollType.bombardment) {
            useDoubleBoomEmoji = true;
            totalHits *= 2;
        }
        if (totalHits < 1) {
            useDoubleBoomEmoji = false;
        }
        if (totalHits > 0 && rollType == CombatRollType.bombardment && player.hasTech("dszelir")) {
            totalHits++;
        }
        if (totalHits > 0 && rollType != CombatRollType.combatround && player.hasTech("tf-shardsaturation")) {
            totalHits++;
        }
        result += CombatMessageHelper.displayHitResults(totalHits, useDoubleBoomEmoji);

        if (totalHits > 0 && usesX89c4) {
            result += "\n" + player.getFactionEmoji() + " produced "
                    + StringHelper.pluralize((totalHits / 2), "additional hit") + " using "
                    + Mapper.getTech("x89c4").getNameRepresentation() + ".";
        }

        if ((hacanFlagship || tkHacanWarsun) && nearMisses > 0 && !isThalnosReroll) {
            HacanFlagshipService.startHacanFlagshipNormal(event, game, player, activeSystem, nearMisses);
        }
        if (player.hasRelic("thalnos")
                && rollType == CombatRollType.combatround
                && totalMisses > 0
                && !isThalnosReroll) {
            result += "\n" + player.getFactionEmoji() + " You have _The Crown of Thalnos_ and may reroll "
                    + (totalMisses == 1 ? "the miss" : "misses")
                    + ", adding +1, at the risk of your " + (totalMisses == 1 ? "troop's life" : "troops' lives") + ".";
            payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                    CombatRollNoteType.REROLL_AVAILABLE,
                    CombatRollNotePlacement.AFTER_TOTAL,
                    "thalnos",
                    null,
                    totalMisses,
                    Map.of("actorEmoji", player.getFactionEmoji())));
        }
        if (totalHits > 0 && rollType == CombatRollType.bombardment && player.hasTech("dszelir")) {
            result += "\n" + player.getFactionEmoji()
                    + " You have _Shard Volley_ and thus produced an additional hit to the ones rolled above.";
        }
        if (totalHits > 0 && rollType != CombatRollType.combatround && player.hasTech("tf-shardsaturation")) {
            result += "\n" + player.getFactionEmoji()
                    + " You have _Shard Saturation_ and thus produced an additional hit to the ones rolled above.";
        }
        delayedAfterTotalNotes.forEach(payloadBuilder::addNote);
        if (!extra.isEmpty()) {
            result += "\n\n" + extra;
        }
        if (game.getStoredValue("munitionsReserves").equalsIgnoreCase(player.getFaction())
                && rollType == CombatRollType.combatround) {
            game.setStoredValue("munitionsReserves", "");
        }
        CombatRollPayload payload = payloadBuilder.build(totalHits, totalMisses, maximumHits);
        return new CombatRollResult(result, totalHits, whiff, slam, payload);
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

    public static Map<UnitModel, Integer> flattenUnitMap(Map<Pair<UnitModel, UnitHolder>, Integer> map) {
        Map<UnitModel, Integer> result = new HashMap<>();
        map.forEach((k, v) -> result.merge(k.getLeft(), v, Integer::sum));
        return result;
    }

    record MergeResult(Map<Pair<UnitModel, UnitHolder>, Integer> units, Set<String> divergingModels) {}

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
}
