package ti4.service.combat;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringBetween;

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
import java.util.regex.Pattern;
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
import ti4.discord.interactions.buttons.handlers.actioncards.acd2.FracturedRealityAcd2ButtonHandler;
import ti4.discord.interactions.buttons.handlers.actioncards.theodisi.MassHypnosisLLButtonHandler;
import ti4.discord.interactions.buttons.handlers.actioncards.theodisi.RiggedExplosivesLLButtonHandler;
import ti4.discord.interactions.buttons.handlers.combat.CancelGroundHitsButtonId;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronFactionTechsHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronUnitsHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenPromissoryHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.SarcosaBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.SarcosaHeroHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.SarcosaUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.ToldarTechHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.XinCommanderHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.XinUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.aeterna.AeternaBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.arcanum.ArcanumBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.arcanum.ArcanumTechHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.arcanum.ArcanumUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.ardentia.ArdentiaUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.kryxos.KryxosBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.kryxos.KryxosUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.revenant.RevenantLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.revenant.RevenantTechHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.scrapyard.ScrapyardLeaderHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thrones.ThronesUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thurviali.ThurvialiTechHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thurviali.ThurvialiUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.vanguard.VanguardUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.xytheris.XytherisAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.xytheris.XytherisLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.xytheris.XytherisPromissoryHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.xytheris.XytherisTechHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.kalora.KaloraBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.kalora.KaloraLeaderHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.kalora.KaloraUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.vyserix.VyserixBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.vyserix.VyserixUnitHandler;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsBRButtonHandler;
import ti4.discord.interactions.buttons.handlers.unit.monuments.TwilightsFallMonumentsButtonHandler;
import ti4.discord.interactions.buttons.ids.AutoAssignGroundHitsButtonIds;
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
import ti4.json.JsonMapperManager;
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
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.UnlockLeaderService;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.HacanFlagshipService;
import ti4.service.unit.UnitModelValueInjectionService;
import ti4.spring.context.SpringContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@UtilityClass
public class CombatRollService {
    private static final ObjectMapper MAPPER = JsonMapperManager.basic();
    private static final Pattern TOTAL_HITS_LINE_PATTERN = Pattern.compile("\\n\\*\\*Total hits \\d+\\*\\*[^\\n]*\\n");

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
                    game.getStoredValue("assignedBombardment" + player.getFaction()), new TypeReference<>() {});

            boolean hasValidBombardment = false;
            List<String> bombardedPlanets = new ArrayList<>();
            for (String planet : BombardmentService.getBombardablePlanets(player, game, tile)) {
                if (assignedUnits.stream().anyMatch(a -> a.planet().equals(planet))) {
                    game.setStoredValue("bombardmentTarget" + player.getFaction(), planet);
                    XinCommanderHandler.warnAboutCoexistingUnitsBombardment(game, player, tile, planet, event);
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

    public static int secondHalfOfSelectedSpaceCannonRoll(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile targetTile,
            UnitModel selectedUnit,
            UnitHolder sourceUnitHolder,
            Player targetPlayer) {
        if (selectedUnit == null || sourceUnitHolder == null || targetPlayer == null) {
            return 0;
        }
        Map<Pair<UnitModel, UnitHolder>, Integer> selectedUnits = new HashMap<>();
        selectedUnits.put(new ImmutablePair<>(selectedUnit, sourceUnitHolder), 1);
        return secondHalfOfCombatRoll(
                player,
                game,
                event,
                targetTile,
                Constants.SPACE,
                CombatRollType.SpaceCannonOffence,
                false,
                selectedUnits,
                targetPlayer);
    }

    private static UnitModel fakeUnit(Player player, String id, String name, String baseType) {
        UnitModel unit = new UnitModel();
        unit.setName(name);
        unit.setAsyncId(id);
        unit.setId(id);
        unit.setBaseType(baseType);
        unit.setFaction(player.getFaction());
        return unit;
    }

    private static UnitModel fakeAfbUnit(Player player, String id, String name, String baseType, int dice, int hitsOn) {
        UnitModel unit = fakeUnit(player, id, name, baseType);
        unit.setAfbDieCount(dice);
        unit.setAfbHitsOn(hitsOn);
        return unit;
    }

    private static UnitModel fakeSpaceCannonUnit(Player player, String id, String name, int dice, int hitsOn) {
        UnitModel unit = fakeUnit(player, id, name, "pds");
        unit.setSpaceCannonDieCount(dice);
        unit.setSpaceCannonHitsOn(hitsOn);
        return unit;
    }

    private static UnitModel planetSpaceCannonUnit(Player player, Planet planet, int dice, int hitsOn) {
        String name = Helper.getPlanetRepresentationPlusEmoji(
                        Mapper.getPlanet(planet.getName()).getId())
                + " space cannon";
        return fakeSpaceCannonUnit(player, planet.getName() + "pds", name, dice, hitsOn);
    }

    public static UnitModel getGlatisonAfbUnit(Player player) {
        return fakeAfbUnit(player, "glatison_afb", "Glatison", "pds", 2, 4);
    }

    public static UnitModel getMetaliAFBUnit(Player player) {
        return fakeAfbUnit(player, "MetaliAFB", "Metali Void Armaments", "dd", 3, 6);
    }

    public static UnitModel getProjectionUnit(Player player, boolean tf) {
        return fakeAfbUnit(player, "projectionafb", "Projection of Power", "dd", tf ? 2 : 1, 6);
    }

    public static UnitModel getZelianPlanetUnit(Player player, String planetName, int planetCombat) {
        UnitModel unit = fakeUnit(player, "zelianplanet", "Zelian Planet " + planetName, "dd");
        unit.setCombatDieCount(1);
        unit.setCombatHitsOn(planetCombat);
        return unit;
    }

    private static void skipUnitForArticlesOfWar(
            Map<Pair<UnitModel, UnitHolder>, Integer> units,
            String alias,
            GenericInteractionCreateEvent event,
            String message) {
        if (units.keySet().removeIf(pair -> alias.equals(pair.getLeft().getAlias()))) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        }
    }

    private static CombatRollPayload withTotalHits(CombatRollPayload payload, int hits) {
        CombatRollPayload.RollTotal total = payload.total();
        if (total == null) {
            return payload;
        }
        return new CombatRollPayload(
                payload.header(),
                payload.notes(),
                payload.modifiers(),
                payload.unitRolls(),
                new CombatRollPayload.RollTotal(total.diceRolled(), hits, total.misses(), total.maximumHits()));
    }

    public static int secondHalfOfCombatRoll(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType,
            boolean automated) {
        return secondHalfOfCombatRoll(player, game, event, tile, unitHolderName, rollType, automated, null, null);
    }

    private static int secondHalfOfCombatRoll(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType,
            boolean automated,
            Map<Pair<UnitModel, UnitHolder>, Integer> selectedUnits,
            Player targetPlayer) {
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
        if (XytherisLeadersHandler.offerHeroUnitAbilityRoll(event, game, player, tile, combatOnHolder, rollType)) {
            return 0;
        }
        Player opponent = targetPlayer;

        Map<Pair<UnitModel, UnitHolder>, Integer> playerUnitsByQuantity =
                getUnitsInCombatByHolder(tile, combatOnHolder, player, event, rollType, game);
        if (rollType != CombatRollType.combatround) {
            playerUnitsByQuantity
                    .entrySet()
                    .removeIf(entry -> ThurvialiUnitHandler.isStructureUnitAbilitySuppressed(
                            player, entry.getKey().getRight(), entry.getKey().getLeft()));
        }
        if (selectedUnits != null) {
            playerUnitsByQuantity = new HashMap<>(selectedUnits);
            if (rollType != CombatRollType.combatround) {
                playerUnitsByQuantity
                        .entrySet()
                        .removeIf(entry -> ThurvialiUnitHandler.isStructureUnitAbilitySuppressed(
                                player,
                                entry.getKey().getRight(),
                                entry.getKey().getLeft()));
            }
        }
        if (rollType == CombatRollType.AFB && player.hasRelic("metalivoidarmaments")) {
            playerUnitsByQuantity.put(new ImmutablePair<>(getMetaliAFBUnit(player), combatOnHolder), 1);
        }
        if (rollType == CombatRollType.AFB
                && player.hasRelic("superweaponglatison")
                && tile == ButtonHelperAbilities.getLocationOfSuperweapon(game, "glatison")) {
            playerUnitsByQuantity.put(new ImmutablePair<>(getGlatisonAfbUnit(player), combatOnHolder), 1);
        }
        if (rollType == CombatRollType.AFB && player.hasTech("tf-projectionofpow")) {
            playerUnitsByQuantity.put(new ImmutablePair<>(getProjectionUnit(player, true), combatOnHolder), 1);
        }
        if (player.hasAbility("projection_of_power")
                && ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock).stream()
                        .anyMatch(tile2 -> FoWHelper.getAdjacentTiles(game, tile2.getPosition(), player, false, true)
                                .contains(tile.getPosition()))) {
            playerUnitsByQuantity.put(new ImmutablePair<>(getProjectionUnit(player, false), combatOnHolder), 1);
        }
        boolean inSpace = "space".equalsIgnoreCase(unitHolderName);
        boolean zelianPlanets = player.hasActiveBreakthrough("zelianbt");
        if (rollType == CombatRollType.combatround
                && (zelianPlanets || (player.hasTech("tf-hostileplanetoids") && inSpace))) {
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                if (player.getPlanetsAllianceMode().contains(uH.getName())
                        && (inSpace || uH.getName().equalsIgnoreCase(unitHolderName))) {
                    int resource = Helper.getPlanetResources(uH.getName(), game);
                    playerUnitsByQuantity.put(
                            new ImmutablePair<>(
                                    getZelianPlanetUnit(player, Helper.getPlanetName(uH.getName()), 10 - resource),
                                    combatOnHolder),
                            1);
                }
            }
        }
        playerUnitsByQuantity = MassHypnosisLLButtonHandler.splitHypnotizedShipForRoll(
                game, player, tile, combatOnHolder, rollType, playerUnitsByQuantity);
        String bombardPlanet = "";
        if (rollType == CombatRollType.bombardment
                && !game.getStoredValue("bombardmentTarget" + player.getFaction())
                        .isEmpty()) {
            bombardPlanet = game.getStoredValue("bombardmentTarget" + player.getFaction());
            if (player.hasUnit("ashen_flagship")) {
                AshenUnitHandler.prepareFlagshipBombardmentContext(game, player, bombardPlanet);
            }
            List<BombardmentAssignment> assignedUnits = MAPPER.readValue(
                    game.getStoredValue("assignedBombardment" + player.getFaction()), new TypeReference<>() {});
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
            skipUnitForArticlesOfWar(
                    playerUnitsByQuantity,
                    "naaz_mech_space",
                    event,
                    "Skipping Z-Grav Eidolon (Naaz-Rokha mech) combat rolls due to _Articles of War_.");
            if (rollType == CombatRollType.SpaceCannonDefence || rollType == CombatRollType.SpaceCannonOffence) {
                skipUnitForArticlesOfWar(
                        playerUnitsByQuantity,
                        "xxcha_mech",
                        event,
                        "Skipping Indomitus (Xxcha mech) SPACE CANNON rolls due to _Articles of War_.");
            }
            if (rollType == CombatRollType.bombardment) {
                skipUnitForArticlesOfWar(
                        playerUnitsByQuantity,
                        "l1z1x_mech",
                        event,
                        "Skipping Annihilator (L1Z1X mech) BOMBARDMENT rolls due to _Articles of War_.");
            }
        }

        StringBuilder powerWordWishCombatNote = new StringBuilder();
        if (rollType == CombatRollType.combatround) {
            Map<Pair<UnitModel, UnitHolder>, Integer> adjustedUnits = new HashMap<>();
            for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : playerUnitsByQuantity.entrySet()) {
                UnitModel unit = entry.getKey().getLeft();
                UnitHolder holder = entry.getKey().getRight();
                int boostedCount = ArcanumBreakthroughHandler.getPowerWordWishCombatBonus(
                        game, player, tile, holder, unit, entry.getValue());
                if (boostedCount > 0) {
                    powerWordWishCombatNote
                            .append("> _Power Word: Wish_: ")
                            .append(boostedCount)
                            .append(" ")
                            .append(unit.getName())
                            .append(" receives +2 combat this round.\n");
                    if (entry.getValue() > boostedCount) {
                        adjustedUnits.put(entry.getKey(), entry.getValue() - boostedCount);
                    }
                    UnitModel boostedUnit = UnitModelValueInjectionService.injectValues(
                            unit,
                            UnitModelValueInjectionService.IntegerValueInjection.create()
                                    .combatHitsOn(-2));
                    adjustedUnits.merge(new ImmutablePair<>(boostedUnit, holder), boostedCount, Integer::sum);
                } else {
                    adjustedUnits.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            }
            playerUnitsByQuantity = adjustedUnits;
        }

        StringBuilder unitUpgradeRollNote = new StringBuilder();
        if (game.playerHasLeaderUnlockedOrAlliance(player, "kryxoscommander")) {
            Map<Pair<UnitModel, UnitHolder>, Integer> adjustedUnits = new HashMap<>();

            for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : playerUnitsByQuantity.entrySet()) {
                UnitModel unit = entry.getKey().getLeft();
                UnitHolder holder = entry.getKey().getRight();

                // Only the actual upgraded unit model receives the bonus.
                if (unit.getUpgradesFromUnitId().isEmpty()) {
                    adjustedUnits.merge(entry.getKey(), entry.getValue(), Integer::sum);
                    continue;
                }

                UnitModelValueInjectionService.IntegerValueInjection bonus =
                        UnitModelValueInjectionService.IntegerValueInjection.create();

                switch (rollType) {
                    case combatround -> bonus.combatHitsOn(-1);
                    case AFB -> bonus.afbHitsOn(-1);
                    case bombardment -> bonus.bombardHitsOn(-1);
                    case SpaceCannonOffence, SpaceCannonDefence -> bonus.spaceCannonHitsOn(-1);
                }

                UnitModel upgradedUnit = UnitModelValueInjectionService.injectValues(unit, bonus);

                adjustedUnits.merge(new ImmutablePair<>(upgradedUnit, holder), entry.getValue(), Integer::sum);
            }

            playerUnitsByQuantity = adjustedUnits;
            unitUpgradeRollNote.append(
                    "> Seraxis Thule, the Kryxos commander: Unit upgrades receive +1 to their rolls.\n");
        }

        KryxosBreakthroughHandler.refreshPrototypeInnovators(game, player, tile, playerUnitsByQuantity, rollType);

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
            combatHoldersForOpponent.add(tile.getSpaceUnitHolder());
        }
        if (opponent == null) {
            opponent = getOpponent(player, combatHoldersForOpponent, game);
            if (opponent == null) {
                opponent = player;
            }
        }
        Map<UnitModel, Integer> opponentUnitsByQuantity =
                getUnitsInCombat(tile, combatOnHolder, opponent, event, rollType, game);

        TileModel tileModel = TileHelper.getTileById(tile.getTileID());
        Map<UnitModel, Integer> playerUnitsFlat = flattenUnitMap(playerUnitsByQuantity);
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
                    MAPPER.readValue(gameAssignedBombardment, new TypeReference<>() {});
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
                            .noneMatch(BombardmentAssignment::galvanized)) {
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
        RevenantLeadersHandler.addRevXytherisCommanderModifier(tempMods, game, player, rollType);
        ScrapyardLeaderHandler.addCommanderModifier(tempMods, game, player, tile, combatOnHolder, rollType);
        XytherisPromissoryHandler.addSwarmSpawnModifier(tempMods, game, player, rollType);
        TwilightsFallMonumentsButtonHandler.addOrangeTfMonumentModifier(tempMods, game, player, tile, rollType);
        if (player.hasTech("beironats")) {
            extraRolls.addAll(IronFactionTechsHandler.getAdvancedTargetingSystemsExtraRollModifier(
                    game, player, opponent, tile, combatOnHolder, rollType));
        }
        if (player.ownsUnit("arcanum_flagship")) {
            extraRolls.addAll(
                    ArcanumUnitHandler.getAstralCodexExtraRollModifier(player, tile, combatOnHolder, rollType));
        }
        extraRolls.addAll(FracturedRealityAcd2ButtonHandler.consumeCombatExtraRoll(game, player, rollType));

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
        StringBuilder rollNotes =
                new StringBuilder().append(powerWordWishCombatNote).append(unitUpgradeRollNote);

        if (!rollNotes.isEmpty()) {
            String modifierHeader = "With modifiers: \n";
            int modifierHeaderIndex = message.indexOf(modifierHeader);
            if (modifierHeaderIndex >= 0) {
                int insertIndex = modifierHeaderIndex + modifierHeader.length();
                message = message.substring(0, insertIndex) + rollNotes + message.substring(insertIndex);
            } else {
                message = combatSummary + modifierHeader + rollNotes + rollResult.message();
            }
        }
        CombatRollPayload.RollHeader rollHeader =
                buildRollHeader(game, player, opponent, tile, combatOnHolder, rollType, combatSummary);
        CombatRollPayload payload = rollResult.payload().withHeader(rollHeader);
        RiggedExplosivesLLButtonHandler.destroyFailedRiggedExplosives(event, game, player, tile, rollType, payload);
        FOWCombatThreadMirroring.mirrorCombatMessage(event, player, game, message);
        int h = rollResult.totalHits();
        int massHypnosisHits =
                MassHypnosisLLButtonHandler.getRedirectedHits(game, player, tile, combatOnHolder, rollType, payload);

        if (massHypnosisHits > 0) {
            h = Math.max(0, h - massHypnosisHits);
            message = TOTAL_HITS_LINE_PATTERN.matcher(message).replaceFirst(CombatMessageHelper.displayHitResults(h));
            payload = withTotalHits(payload, h);
            message += "\n_Mass Hypnosis_ redirected " + StringHelper.pluralize(massHypnosisHits, "hit")
                    + " to its owner's ships.";
        }
        if (rollType == CombatRollType.combatround && SarcosaHeroHandler.isLeviathanCombatant(game, opponent)) {
            int cancelledHits =
                    SarcosaHeroHandler.consumeLeviathanCancellations(game, opponent, tile, combatOnHolder, h);
            if (cancelledHits > 0) {
                h -= cancelledHits;
                message =
                        TOTAL_HITS_LINE_PATTERN.matcher(message).replaceFirst(CombatMessageHelper.displayHitResults(h));
                payload = withTotalHits(payload, h);
                message += "\n**Leviathan** canceled " + cancelledHits + " hit" + (cancelledHits == 1 ? "." : "s.");
            }
        }
        XytherisAbilityHandler.beginStingOfTheHiveRoll(game, player, tile, rollType, h);
        String round1Value = game.getStoredValue(
                "combatRoundTracker" + opponent.getFaction() + tile.getPosition() + combatOnHolder.getName());
        int round = round1Value.isEmpty() ? 0 : Integer.parseInt(round1Value);
        String round2Value = game.getStoredValue(
                "combatRoundTracker" + player.getFaction() + tile.getPosition() + combatOnHolder.getName());
        int round2 = round2Value.isEmpty() ? 1 : Integer.parseInt(round2Value);

        if (round2 > round && rollType == CombatRollType.combatround) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "## __Start of Combat Round #" + round2 + "__");
        }

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
                        message += "\n_Proxima Targeting VI_ canceled " + StringHelper.pluralize(oldH - h, "hit")
                                + " automatically.";
                    }
                }
            }
        }
        if (message.endsWith(";\n")) {
            message = message.substring(0, message.length() - 2);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        XytherisPromissoryHandler.resolveSwarmSpawnAfterRoll(event, game, player, rollType);
        int colossusHits = rollType == CombatRollType.combatround
                        && combatOnHolder instanceof Planet
                        && player.hasUnit("sarcosa_mech")
                ? payload.unitRolls().stream()
                        .filter(unitRoll -> "sarcosa_mech".equals(unitRoll.unitId()))
                        .mapToInt(CombatRollPayload.UnitRoll::hits)
                        .sum()
                : 0;
        SarcosaUnitHandler.offerColossusInfantryDestruction(event, game, player, tile, combatOnHolder, colossusHits);
        int celestialDragonHits = Math.min(h, XinUnitHandler.getCelestialDragonHits(player, rollType, payload));
        if (celestialDragonHits > 0
                && XinUnitHandler.offerCelestialDragonHitAssignment(
                        event, game, player, opponent, tile, celestialDragonHits)) {
            h -= celestialDragonHits;
        }
        if (rollType == CombatRollType.combatround && opponent != player) {
            ToldarTechHandler.recordCombatRoundHits(game, player, tile, combatOnHolder, round2, h);
        }
        if (massHypnosisHits > 0 && !game.isFowMode()) {
            sendSpaceAssignHitsButtons(event, game, player, tile, massHypnosisHits);
        }
        ThronesUnitHandler.offerGholaAfterRoll(event, game, player, opponent, tile, combatOnHolder, rollType, payload);
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
            Button decline = Buttons.DECLINE;
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
                String sufferedMsg = "\n" + opponent.getRepresentation(true, true, true, true) + ", you suffered "
                        + StringHelper.pluralize(h, "hit") + " in round #" + round2 + ".";
                String rollNextRoundMsg = opponent.getRepresentationUnfogged() + " you may roll dice for Combat Round #"
                        + (round + 1) + ".";
                if (round2 > round) {
                    buttons.add(rollNextCombatRoundButton(opponent, tile, combatOnHolder, round + 1));
                }
                if (combatOnHolder instanceof Planet) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), sufferedMsg);
                    if (!automated) {
                        if (h > 0) {
                            String msg = opponent.getRepresentationUnfogged() + " you may autoassign "
                                    + StringHelper.pluralize(h, "hit") + ".";
                            if (isDummy(opponent)) {
                                buttons.add(dummyAutoAssignButton(
                                        opponent,
                                        AutoAssignGroundHitsButtonIds.format(combatOnHolder.getName(), h),
                                        h));
                            } else {
                                addGroundHitButtons(buttons, game, opponent, tile, combatOnHolder, h);
                                AshenPromissoryHandler.addFromTheAshesButton(
                                        buttons, game, opponent, player, tile, combatOnHolder, h);
                                if (round2 == 1 && opponent.hasTech("threvenantr")) {
                                    RevenantTechHandler.addEternalAegisButton(
                                            buttons, game, opponent, player, tile, combatOnHolder, h);
                                }
                                ToldarTechHandler.addExhaustVirTrainingButton(
                                        buttons, opponent, player, tile, combatOnHolder, h);
                            }
                            sendHitAssignmentMessage(
                                    event, game, opponent, player, tile, combatOnHolder, round2, h, msg, buttons);
                            if (opponent.hasTech("vpw")) {
                                List<Button> vpwButtons = new ArrayList<>();
                                addGroundHitButtons(vpwButtons, game, player, tile, combatOnHolder, 1);
                                MessageHelper.sendMessageToChannelWithButtons(
                                        event.getMessageChannel(),
                                        player.getRepresentationUnfogged()
                                                + " you got hit by _Valkyrie Particle Weave_. You may autoassign 1 hit.",
                                        vpwButtons);
                            }
                        } else {
                            if (round2 > round) {
                                MessageHelper.sendMessageToChannelWithButtons(
                                        event.getMessageChannel(), rollNextRoundMsg, buttons);
                            }
                            offerDuraniumRepairWithoutHits(event, opponent, tile, combatOnHolder);
                        }
                    } else if (opponent.hasTech("vpw") && h > 0) {
                        MessageHelper.sendMessageToChannel(
                                event.getMessageChannel(),
                                player.getRepresentation() + " suffered 1 hit due to _Valkyrie Particle Weave_.");
                    }
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), sufferedMsg);
                    if (h > 0) {
                        buttons.add(
                                autoAssignButton(opponent, "autoAssignSpaceHits_" + tile.getPosition() + "_" + h, h));
                        if (!isDummy(opponent)) {
                            addManualAndCancelHitButtons(
                                    buttons,
                                    game,
                                    opponent,
                                    tile,
                                    h,
                                    "getDamageButtons_" + tile.getPosition() + "deleteThis_spacecombat",
                                    "cancelSpaceHits_" + tile.getPosition() + "_" + h,
                                    "space");
                            VanguardUnitHandler.addSpaceCombatHitButtons(buttons, game, opponent, tile, h);
                        }
                        if (round2 == 1 && opponent.hasTech("threvenantr")) {
                            RevenantTechHandler.addEternalAegisButton(
                                    buttons, game, opponent, player, tile, combatOnHolder, h);
                        }
                        ToldarTechHandler.addExhaustVirTrainingButton(
                                buttons, opponent, player, tile, combatOnHolder, h);

                        String msg2 = opponent.getRepresentationNoPing() + ", you may automatically assign "
                                + (h == 1 ? "the hit" : "hits") + ". "
                                + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                                        opponent, game, tile, h, event, true);
                        if (opponent.hasRelic("metalivoidshielding")) {
                            RelicModel relicModel = Mapper.getRelic("metalivoidshielding");
                            msg2 += "\nReminder: You have the _" + relicModel.getName()
                                    + "_ relic, you may SUSTAIN DAMAGE on one of your non-fighter ships instead of taking a hit.";
                        }
                        sendHitAssignmentMessage(
                                event, game, opponent, player, tile, combatOnHolder, round2, h, msg2, buttons);
                    } else {
                        if (round2 > round) {
                            MessageHelper.sendMessageToChannelWithButtons(
                                    event.getMessageChannel(), rollNextRoundMsg, buttons);
                        }
                        offerDuraniumRepairWithoutHits(event, opponent, tile, combatOnHolder);
                    }
                }
            }
            if (rollType == CombatRollType.AFB && h > 0) {
                String msg2 = opponent.getRepresentation() + ", you may automatically assign "
                        + (h == 1 ? "the hit" : "hits") + " from AFB.";
                buttons.add(autoAssignButton(opponent, "autoAssignAFBHits_" + tile.getPosition() + "_" + h, h));
                if (!isDummy(opponent)) {
                    addManualAndCancelHitButtons(
                            buttons,
                            game,
                            opponent,
                            tile,
                            h,
                            opponent.factionButtonChecker() + "getDamageButtons_" + tile.getPosition() + "_afb",
                            "cancelAFBHits_" + tile.getPosition() + "_" + h,
                            "afb");
                }
                msg2 += addStingOfTheHiveButtons(buttons, game, player, tile, CombatRollType.AFB, opponent, h);
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
            if (isDummy(opponent) && h > 0) {
                List<Button> buttons = new ArrayList<>();
                if (combatOnHolder instanceof Planet) {
                    if (round2 > round) {
                        buttons.add(rollNextCombatRoundButton(opponent, tile, combatOnHolder, round + 1));
                    }
                    buttons.add(dummyAutoAssignButton(
                            opponent, AutoAssignGroundHitsButtonIds.format(combatOnHolder.getName(), h), h));
                    String msg = opponent.getRepresentationUnfogged() + " you may autoassign "
                            + StringHelper.pluralize(h, "hit") + ".";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
                } else {
                    String msg2 = opponent.getRepresentationNoPing() + ", you may automatically assign "
                            + (h == 1 ? "the hit" : "hits") + ".";
                    if (rollType == CombatRollType.AFB) {
                        buttons.add(dummyAutoAssignButton(
                                opponent, "autoAssignAFBHits_" + tile.getPosition() + "_" + h, h));
                    } else {
                        buttons.add(dummyAutoAssignButton(
                                opponent, "autoAssignSpaceHits_" + tile.getPosition() + "_" + h, h));
                        msg2 += ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, h, event, true);
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);
                }
            }
        }

        if ((!game.isFowMode() || isFoWPrivateChannelRoll(player, event))
                && rollType == CombatRollType.SpaceCannonOffence
                && h > 0
                && (opponent != player || targetPlayer != null)) {
            MessageChannel channel =
                    isFoWPrivateChannelRoll(player, event) ? opponent.getCorrectChannel() : event.getMessageChannel();
            String msg = "\n" + opponent.getRepresentation(true, true, true, true) + " suffered "
                    + StringHelper.pluralize(h, "hit") + " from SPACE CANNON against your ships.";
            MessageHelper.sendMessageToChannel(channel, msg);
            List<Button> buttons = new ArrayList<>();
            buttons.add(
                    autoAssignButton(opponent, "autoAssignSpaceCannonOffenceHits_" + tile.getPosition() + "_" + h, h));
            addManualAndCancelHitButtons(
                    buttons,
                    game,
                    opponent,
                    tile,
                    h,
                    "getDamageButtons_" + tile.getPosition() + "deleteThis_pds",
                    "cancelPdsOffenseHits_" + tile.getPosition() + "_" + h,
                    "pds");
            String stingNote = addStingOfTheHiveButtons(
                    buttons, game, player, tile, CombatRollType.SpaceCannonOffence, opponent, h);
            String msg2 = opponent.getRepresentationNoPing() + ", you may automatically assign "
                    + (h == 1 ? "the hit" : "hits") + "."
                    + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, h, event, true, true)
                    + stingNote;
            MessageHelper.sendMessageToChannelWithButtons(channel, msg2, buttons);
        }

        if (rollType == CombatRollType.SpaceCannonDefence && h > 0 && opponent != player) {
            List<Button> stingOfTheHiveButtons = XytherisAbilityHandler.getStingOfTheHiveHitReplacementButtons(
                    game, player, tile, CombatRollType.SpaceCannonDefence, opponent, h);
            if (!stingOfTheHiveButtons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation()
                                + ", you may use **Sting of the Hive** to cancel any number of these SPACE CANNON DEFENCE hits and place that many mine tokens.\n-# "
                                + opponent.getRepresentationNoPing()
                                + " should wait to assign hits until you have decided:",
                        stingOfTheHiveButtons);
            }
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
        if (rollType != CombatRollType.combatround && h >= 1 && player.hasTech("thxytherisr")) {
            MessageHelper.sendMessageToChannelWithButton(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", you may use _Biomechanical Nutrients_:",
                    XytherisTechHandler.getBiomechanicalButton(event, game, player, h));
        }

        if (rollType == CombatRollType.bombardment) {
            AshenLeadersHandler.offerCommanderBombardmentButtons(event, game, player, h);
            if (h >= 3) {
                CommanderUnlockCheckService.checkPlayer(player, "ashen");
            }
            if (AshenBreakthroughHandler.offerHitReplacement(event, game, player, tile, bombardPlanet, h)) {
                return h;
            }
            if (h > 0) {
                if (!game.isFowMode() && !bombardPlanet.isEmpty()) {
                    UnitHolder bombardedHolder = game.getUnitHolderFromPlanet(bombardPlanet);
                    String plural = h == 1 ? "" : "s";
                    for (Player p2 : game.getRealPlayersNNeutral()) {
                        if (p2 == player || !FoWHelper.playerHasUnitsOnPlanet(p2, bombardedHolder)) {
                            continue;
                        }
                        List<Button> targetButtons = new ArrayList<>();
                        String assignmentMessage;
                        if (p2.isRealPlayer()) {
                            targetButtons.add(Buttons.red(
                                    "getDamageButtons_" + tile.getPosition() + "_bombardment", "Assign Hit" + plural));
                            assignmentMessage =
                                    p2.getRepresentation() + ", please assign the BOMBARDMENT hit" + plural + ".";
                        } else {
                            targetButtons.add(dummyAutoAssignButton(
                                    p2, AutoAssignGroundHitsButtonIds.format(bombardedHolder.getName(), h), h));
                            assignmentMessage = player.getRepresentation() + ", please assign the BOMBARDMENT hit"
                                    + plural + " for the dummy player.";
                        }
                        assignmentMessage += addStingOfTheHiveButtons(
                                targetButtons, game, player, tile, CombatRollType.bombardment, p2, h);
                        MessageHelper.sendMessageToChannelWithButtons(
                                event.getMessageChannel(), assignmentMessage, targetButtons);
                    }
                }
                if (player.hasAbility("meteor_slings")
                        || player.getPromissoryNotes().containsKey("dspnkhra")) {
                    List<Button> buttons = new ArrayList<>();
                    String planet = game.getStoredValue("bombardmentTarget" + player.getFaction());
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + "meteorSlings_" + planet,
                            "Infantry on " + Helper.getPlanetRepresentation(planet, game)));

                    buttons.add(Buttons.DONE_DELETE_BUTTONS);
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

    private static boolean isDummy(Player player) {
        return player.isDummy() || player.isNpc();
    }

    private static Button rollNextCombatRoundButton(Player opponent, Tile tile, UnitHolder holder, int nextRound) {
        String id = "combatRoll_" + tile.getPosition() + "_" + holder.getName();
        if (isDummy(opponent)) {
            return Buttons.blue(opponent.dummyPlayerSpoof() + id, "Roll Dice For Dummy for Combat Round #" + nextRound);
        }
        return Buttons.blue(id, "Roll Dice For Combat Round #" + nextRound);
    }

    private static Button dummyAutoAssignButton(Player target, String autoAssignId, int hits) {
        return Buttons.green(
                target.dummyPlayerSpoof() + autoAssignId, "Auto-assign Hit" + (hits == 1 ? "" : "s") + " For Dummy");
    }

    private static Button autoAssignButton(Player target, String autoAssignId, int hits) {
        if (isDummy(target)) {
            return dummyAutoAssignButton(target, autoAssignId, hits);
        }
        return Buttons.green(target.factionButtonChecker() + autoAssignId, "Auto-assign Hit" + (hits == 1 ? "" : "s"));
    }

    private static void addManualAndCancelHitButtons(
            List<Button> buttons,
            Game game,
            Player target,
            Tile tile,
            int hits,
            String manualId,
            String cancelId,
            String hitContext) {
        buttons.add(Buttons.red(manualId, "Manually Assign Hit" + (hits == 1 ? "" : "s")));
        buttons.add(Buttons.gray(target.factionButtonChecker() + cancelId, "Cancel a Hit"));
        TwilightsFallMonumentsButtonHandler.addYellowTfMonumentCancelHitButton(
                buttons, game, target, tile, hitContext, hits);
    }

    private static void addGroundHitButtons(
            List<Button> buttons, Game game, Player target, Tile tile, UnitHolder holder, int hits) {
        String plural = hits == 1 ? "" : "s";
        buttons.add(Buttons.green(
                target.factionButtonChecker() + AutoAssignGroundHitsButtonIds.format(holder.getName(), hits),
                "Auto-assign Hit" + plural));
        buttons.add(Buttons.red(
                "getDamageButtons_" + tile.getPosition() + "deleteThis_groundcombat", "Manually Assign Hit" + plural));
        MonumentsBRButtonHandler.addSacredPoolsGroundCombatButton(buttons, game, target, tile, holder.getName());
        buttons.add(Buttons.gray(
                target.factionButtonChecker() + CancelGroundHitsButtonId.of(tile.getPosition(), hits, holder.getName()),
                "Cancel a Hit"));
        TwilightsFallMonumentsButtonHandler.addYellowTfMonumentCancelHitButton(
                buttons, game, target, tile, "ground", hits);
    }

    private static String addStingOfTheHiveButtons(
            List<Button> buttons,
            Game game,
            Player player,
            Tile tile,
            CombatRollType rollType,
            Player target,
            int hits) {
        List<Button> stingOfTheHiveButtons = XytherisAbilityHandler.getStingOfTheHiveHitReplacementButtons(
                game, player, tile, rollType, target, hits);
        buttons.addAll(stingOfTheHiveButtons);
        if (stingOfTheHiveButtons.isEmpty()) {
            return "";
        }
        return "\n-# Wait for " + player.getRepresentationNoPing()
                + " to decide whether to cancel hits and place mine tokens before assigning hits.";
    }

    private static void sendHitAssignmentMessage(
            GenericInteractionCreateEvent event,
            Game game,
            Player target,
            Player player,
            Tile tile,
            UnitHolder holder,
            int round,
            int hits,
            String msg,
            List<Button> buttons) {
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                msg,
                null,
                buttons,
                assignmentMessage -> ToldarTechHandler.recordAssignmentMessage(
                        game, target, player, tile, holder, round, hits, assignmentMessage));
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
            TwilightsFallMonumentsButtonHandler.addYellowTfMonumentCancelHitButton(
                    buttons, game, opponent, tile, "space", hits);
            VanguardUnitHandler.addSpaceCombatHitButtons(buttons, game, opponent, tile, hits);
        }

        String msg2 = opponent.getRepresentationNoPing() + ", you may automatically assign ";
        msg2 += (hits == 1 ? "the hit" : "hits") + ". ";
        msg2 += ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, hits, event, true);
        if (opponent.hasRelic("metalivoidshielding")) {
            RelicModel relicModel = Mapper.getRelic("metalivoidshielding");
            msg2 += "\nReminder: You have the _" + relicModel.getName() + "_ relic,";
            msg2 += " you may SUSTAIN DAMAGE on one of your non-fighter ships instead of taking a hit.";
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);
    }

    private static void offerDuraniumRepairWithoutHits(
            GenericInteractionCreateEvent event, Player opponent, Tile tile, UnitHolder combatOnHolder) {
        if (!opponent.hasTech("da") || !hasDamagedUnit(opponent, combatOnHolder)) return;
        String buttonPrefix =
                opponent.isDummy() || opponent.isNpc() ? opponent.dummyPlayerSpoof() : opponent.factionButtonChecker();
        String repairId = combatOnHolder instanceof Planet
                ? AutoAssignGroundHitsButtonIds.format(combatOnHolder.getName(), 0)
                : "autoAssignSpaceHits_" + tile.getPosition() + "_0";
        List<Button> buttons =
                List.of(Buttons.green(buttonPrefix + repairId, "Repair With Duranium Armor"), Buttons.DECLINE);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                opponent.getRepresentationUnfogged()
                        + ", you may repair 1 damaged unit with _Duranium Armor_ even though you suffered no hits.",
                buttons);
    }

    private static boolean hasDamagedUnit(Player player, UnitHolder unitHolder) {
        return unitHolder.getUnitKeys().stream()
                .anyMatch(
                        unitKey -> player.unitBelongsToPlayer(unitKey) && unitHolder.getDamagedUnitCount(unitKey) > 0);
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
        Map<UnitModel, Integer> playerUnitsFlat = flattenUnitMap(playerUnits);
        result += CombatMessageHelper.displayModifiers("With modifiers: \n", playerUnitsFlat, uniqueList);
        payloadBuilder.addModifierDisplays(
                uniqueList, playerUnitsFlat, player, opponent, game, rollType, activeSystem, unitHolder);

        List<NamedCombatModifierModel> cappedDiceModifiers = mods.stream()
                .filter(modifier -> modifier.getModifier().getMaxDice() != null)
                .toList();
        List<NamedCombatModifierModel> appliedMods = mods.stream()
                .filter(modifier -> modifier.getModifier().getMaxDice() == null)
                .toList();
        Map<NamedCombatModifierModel, Integer> cappedDiceRemaining = new IdentityHashMap<>();
        for (NamedCombatModifierModel modifier : cappedDiceModifiers) {
            cappedDiceRemaining.put(modifier, modifier.getModifier().getMaxDice());
        }

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
        UnitHolder space = activeSystem.getSpaceUnitHolder();
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
        boolean xytherisHeroBonus =
                XytherisLeadersHandler.hasHeroUnitAbilityRollBonus(game, player, activeSystem, unitHolder, rollType);
        if (xytherisHeroBonus) {
            resultBuilder.append("Applied +4 to all dice with _Call of the Queen - The Endless Swarm_.\n");
        }
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
                playerUnits, appliedMods, rollType, player, opponent, game, playerUnitsList, activeSystem);
        playerUnits = mergeResult.units();
        Set<String> divergingModels = mergeResult.divergingModels();
        Set<String> consumedBestMods = new HashSet<>();
        UnitModel toldarRerollUnit = null;
        int toldarRerollToHit = 0;
        int toldarRerollModifier = 0;
        int toldarRerollUnitCount = 0;
        int toldarRerollDiceCount = 0;
        game.removeStoredValue("warFundingRolls" + player.getFaction());
        List<Map.Entry<Pair<UnitModel, UnitHolder>, Integer>> rollingUnits = new ArrayList<>(playerUnits.entrySet());
        if (!cappedDiceModifiers.isEmpty()) {
            rollingUnits.sort(Comparator.comparingInt(entry -> {
                UnitModel unit = entry.getKey().getLeft();
                int hitsOn = unit.getCombatDieHitsOnForAbility(rollType, player);
                if (rollType == CombatRollType.combatround) {
                    hitsOn = CombatStatsService.getCombatRoundProfile(true, unit, player, activeSystem, opponent, false)
                            .hitsOn();
                }
                int modifier = CombatModHelper.getCombinedModifierForUnit(
                        unit,
                        entry.getValue(),
                        appliedMods,
                        player,
                        opponent,
                        game,
                        playerUnitsList,
                        rollType,
                        activeSystem,
                        entry.getKey().getRight());
                if (xytherisHeroBonus) {
                    modifier += 4;
                }
                return hitsOn - modifier;
            }));
        }
        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : rollingUnits) {
            UnitModel unitModel = entry.getKey().getLeft();
            UnitHolder perUnitHolder = entry.getKey().getRight();
            int numOfUnit = entry.getValue();
            UnitType unitType = unitModel.getUnitType();

            int toHit = unitModel.getCombatDieHitsOnForAbility(rollType, player);
            int modifierToHit = CombatModHelper.getCombinedModifierForUnit(
                    unitModel,
                    numOfUnit,
                    appliedMods,
                    player,
                    opponent,
                    game,
                    playerUnitsList,
                    rollType,
                    activeSystem,
                    perUnitHolder);
            if (xytherisHeroBonus) {
                modifierToHit += 4;
            }
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
                List<Die> resultRolls;
                int[] cappedModifiersByDie = new int[numRolls];
                if (cappedDiceModifiers.isEmpty()) {
                    resultRolls = DiceHelper.rollDice(toHit - modifierToHit, numRolls);
                } else {
                    for (NamedCombatModifierModel cappedModifier : cappedDiceModifiers) {
                        int remainingDice = cappedDiceRemaining.getOrDefault(cappedModifier, 0);
                        if (remainingDice < 1
                                || !cappedModifier
                                        .getModifier()
                                        .isInScopeForUnit(unitModel, playerUnitsList, rollType, game, player)) {
                            continue;
                        }
                        int diceToModify = Math.min(remainingDice, numRolls);
                        int cappedModifierValue = CombatModHelper.getCombinedModifierForUnit(
                                unitModel,
                                numOfUnit,
                                List.of(cappedModifier),
                                player,
                                opponent,
                                game,
                                playerUnitsList,
                                rollType,
                                activeSystem,
                                perUnitHolder);
                        for (int dieIndex = 0; dieIndex < diceToModify; dieIndex++) {
                            cappedModifiersByDie[dieIndex] += cappedModifierValue;
                        }
                        cappedDiceRemaining.put(cappedModifier, remainingDice - diceToModify);
                    }
                    resultRolls = new ArrayList<>();
                    for (int cappedModifier : cappedModifiersByDie) {
                        resultRolls.addAll(DiceHelper.rollDice(Math.max(1, toHit - modifierToHit - cappedModifier), 1));
                    }
                }
                int mult = 1;

                if (cappedDiceModifiers.isEmpty()) {
                    player.setExpectedHitsTimes10(
                            player.getExpectedHitsTimes10() + (numRolls * mult * (11 - toHit + modifierToHit)));
                    chanceOfAllHits *= Math.pow((11 - toHit + modifierToHit) / 10.0, numRolls * mult);
                    chanceOfAllMiss *= Math.pow((toHit - modifierToHit - 1) / 10.0, numRolls * mult);
                } else {
                    for (Die die : resultRolls) {
                        player.setExpectedHitsTimes10(
                                player.getExpectedHitsTimes10() + (mult * (11 - die.getThreshold())));
                        chanceOfAllHits *= (11 - die.getThreshold()) / 10.0;
                        chanceOfAllMiss *= (die.getThreshold() - 1) / 10.0;
                    }
                }
                maximumHits += numRolls * mult;
                if (usesX89c4) {
                    mult = 2;
                }
                int hitRolls = DiceHelper.countSuccesses(resultRolls);
                int hitsBeforeLeviathan = hitRolls;
                hitRolls = SarcosaHeroHandler.doubleFirstRoundLeviathanHits(
                        game, player, activeSystem, unitHolder, unitModel, rollType, hitRolls);
                SarcosaHeroHandler.recordLeviathanCancellation(
                        game, player, activeSystem, unitHolder, unitModel, rollType, hitsBeforeLeviathan);
                if (hitRolls > hitsBeforeLeviathan) {
                    maximumHits += numRolls;
                    resultBuilder
                            .append(player.getFactionEmoji())
                            .append(" doubled ")
                            .append(hitsBeforeLeviathan)
                            .append(" hit")
                            .append(hitsBeforeLeviathan == 1 ? "" : "s")
                            .append(" with **Leviathan**.\n");
                }
                if ("kryxos_mech3".equals(unitModel.getId()) && numOfUnit > 0) {
                    int[] hitsPerMech = new int[numOfUnit];
                    int nativeRollCount = numOfUnit * numRollsPerUnit;
                    for (int dieIndex = 0; dieIndex < resultRolls.size(); dieIndex++) {
                        int mechIndex = dieIndex < nativeRollCount
                                ? dieIndex / numRollsPerUnit
                                : (dieIndex - nativeRollCount) % numOfUnit;
                        if (mechIndex < numOfUnit && resultRolls.get(dieIndex).isSuccess()) {
                            hitsPerMech[mechIndex]++;
                        }
                    }
                    List<Integer> hitsByMech = new ArrayList<>();
                    for (int hits : hitsPerMech) {
                        if (hits > 0) {
                            hitsByMech.add(hits);
                        }
                    }
                    KryxosUnitHandler.offerWarspawnJuggernautHitButtons(
                            event, game, player, activeSystem, perUnitHolder, hitsByMech);
                }
                if (rollType == CombatRollType.combatround && numRolls != hitRolls) {
                    String key = "warFundingRolls" + player.getFaction();
                    game.setStoredValue(
                            key,
                            game.getStoredValue(key) + ";" + (numRolls - hitRolls) + "_" + (toHit - modifierToHit) + "_"
                                    + unitModel.getBaseType());
                }
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
                                                    event.getMessageChannel(),
                                                    p2.getRepresentation()
                                                            + ", please assign the Dragon BOMBARDMENT hit"
                                                            + (hitRolls == 1 ? "" : "s") + " on "
                                                            + Helper.getPlanetRepresentation(uh.getName(), game) + ".",
                                                    buttons);
                                        } else {
                                            List<Button> buttons2 = new ArrayList<>(List.of(dummyAutoAssignButton(
                                                    p2,
                                                    AutoAssignGroundHitsButtonIds.format(uh.getName(), hitRolls),
                                                    hitRolls)));
                                            MessageHelper.sendMessageToChannelWithButtons(
                                                    event.getMessageChannel(),
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
                List<Integer> modifiersByDie = List.of();
                if (!cappedDiceModifiers.isEmpty()) {
                    int displayModifierToHit = modifierToHit;
                    modifiersByDie = new ArrayList<>(java.util.Arrays.stream(cappedModifiersByDie)
                            .map(cappedModifier -> displayModifierToHit + cappedModifier)
                            .boxed()
                            .toList());
                    while (modifiersByDie.size() < resultRolls.size()) {
                        modifiersByDie.add(displayModifierToHit);
                    }
                }
                String unitRoll = CombatMessageHelper.displayUnitRoll(
                        unitModel,
                        toHit,
                        modifierToHit,
                        numOfUnit,
                        numRollsPerUnit,
                        extraRollsForUnit,
                        resultRolls,
                        hitRolls,
                        holderLabel,
                        modifiersByDie);
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
                        DieRollSource.PRIMARY,
                        modifiersByDie);
                List<DiceHelper.Die> resultRolls2 = new ArrayList<>();
                int numMisses = numRolls - hitRolls;
                if (player.ownsUnit("tf-justicerrail") && rollType == CombatRollType.SpaceCannonOffence) {
                    game.setStoredValue(player.getFaction() + "graviton", "yes");
                }
                RerollTarget reroll = new RerollTarget(unitModel, toHit, modifierToHit, numOfUnit, numRollsPerUnit);
                if ((game.playerHasLeaderUnlockedOrAlliance(player, "jolnarcommander")
                                || player.hasTech("tf-tacticalbrilliance"))
                        && rollType != CombatRollType.combatround) {

                    if (opponent == player && rollType == CombatRollType.bombardment && player.hasTech("proxima")) {
                        if (hitRolls > 0) {
                            resultRolls2 = reroll.roll(player, hitRolls);
                            // Very important to remove the rerolled dice from the original dice pool
                            resultRolls.removeIf(Die::isSuccess);
                            int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                            totalHits += hitRolls2 - hitRolls;
                            reroll.record(
                                    payloadBuilder,
                                    resultBuilder,
                                    "Rerolling " + StringHelper.pluralize(hitRolls, "hit")
                                            + " due to Ta Zern, the Jol-Nar Commander:\n",
                                    extraRollsForUnit,
                                    resultRolls2,
                                    hitRolls2,
                                    RollSegmentType.JOL_NAR_COMMANDER_REROLL_HITS,
                                    DieRollSource.REROLL_HIT);
                        }
                    } else if (numMisses > 0) {
                        resultRolls2 = reroll.rerollMisses(player, resultRolls, numMisses);
                        chanceOfAllHits *= reroll.hitChance(numMisses);
                        chanceOfAllMiss *= reroll.missChance(numMisses);
                        maximumHits += numRolls * mult;
                        int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                        totalHits += hitRolls2;
                        reroll.record(
                                payloadBuilder,
                                resultBuilder,
                                "Rerolling " + misses(numMisses) + " due to Ta Zern, the Jol-Nar Commander:\n",
                                0,
                                resultRolls2,
                                hitRolls2,
                                RollSegmentType.JOL_NAR_COMMANDER_REROLL_MISSES,
                                DieRollSource.REROLL_MISS);
                    }
                }
                if (IronLeadersHandler.shouldAutoRerollCommanderMechMisses(game, player, unitModel, rollType)
                        && numMisses > 0) {
                    resultRolls2 = reroll.rerollMisses(player, resultRolls, numMisses);
                    chanceOfAllHits *= reroll.hitChance(numMisses);
                    chanceOfAllMiss *= reroll.missChance(numMisses);
                    maximumHits += numRolls * mult;
                    int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                    totalHits += hitRolls2;
                    reroll.record(
                            payloadBuilder,
                            resultBuilder,
                            "Rerolling " + misses(numMisses) + " due to Captain Vakros, the Iron Tide Commander:\n",
                            0,
                            resultRolls2,
                            hitRolls2,
                            RollSegmentType.IRON_COMMANDER_REROLL_MISSES,
                            DieRollSource.REROLL_MISS);
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
                    int num1s = countOnes(resultRolls);
                    if (num1s > 0) {
                        resultRolls2 = reroll.roll(player, num1s);
                        int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                        totalHits += hitRolls2;
                        reroll.record(
                                payloadBuilder,
                                resultBuilder,
                                "Rerolling " + StringHelper.pluralize(num1s, "roll")
                                        + " of 1 due to the Kaltrim Commander:\n ",
                                0,
                                resultRolls2,
                                hitRolls2,
                                RollSegmentType.KALTRIM_COMMANDER_REROLL_ONES,
                                DieRollSource.REROLL_ONE);
                    }
                }

                if (game.getStoredValue("munitionsReserves").equalsIgnoreCase(player.getFaction())
                        && rollType == CombatRollType.combatround
                        && numMisses > 0
                        && !isThalnosReroll) { // do not munitions after thalnos
                    resultRolls2 = reroll.rerollMisses(player, resultRolls, numMisses);
                    chanceOfAllHits *= reroll.hitChance(numMisses);
                    chanceOfAllMiss *= reroll.missChance(numMisses);
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
                    reroll.record(
                            payloadBuilder,
                            resultBuilder,
                            "**Munitions Reserve** rerolling " + misses(numMisses) + ": ",
                            0,
                            resultRolls2,
                            hitRolls2,
                            RollSegmentType.MUNITIONS_RESERVES_REROLL,
                            DieRollSource.MUNITIONS_RESERVES);
                    // these dice are eligible for further rerolls
                    resultRolls.addAll(resultRolls2);
                    resultRolls2.clear();
                }

                if (game.playerHasLeaderUnlockedOrAlliance(player, "kaltrimcommander")) {
                    int num1s = countOnes(resultRolls);
                    // Very important to remove the rerolled dice from the original dice pool
                    resultRolls.removeIf(d -> d.getResult() == 1);

                    if (num1s > 0) {
                        resultRolls2 = reroll.roll(player, num1s);
                        int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                        totalHits += hitRolls2;
                        reroll.record(
                                null,
                                resultBuilder,
                                "Rerolling " + StringHelper.pluralize(num1s, "roll")
                                        + " of 1 due to the Kaltrim Commander:\n ",
                                0,
                                resultRolls2,
                                hitRolls2,
                                null,
                                null);
                    }
                }

                boolean hasMiss = resultRolls.stream().anyMatch(Predicate.not(Die::isSuccess));
                if (game.playerHasLeaderUnlockedOrAlliance(player, "toldarcommander")
                        && rollType == CombatRollType.combatround
                        && !isThalnosReroll
                        && hasMiss
                        && (toldarRerollUnit == null
                                || toHit < toldarRerollToHit
                                || (toHit == toldarRerollToHit && numRollsPerUnit > toldarRerollDiceCount))) {
                    toldarRerollUnit = unitModel;
                    toldarRerollToHit = toHit;
                    toldarRerollModifier = modifierToHit;
                    toldarRerollUnitCount = numOfUnit;
                    toldarRerollDiceCount = numRollsPerUnit;
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
        if (toldarRerollUnit != null) {
            List<DiceHelper.Die> reroll = DiceHelper.rollDice(toldarRerollToHit - toldarRerollModifier, 1);
            int rerollHits = DiceHelper.countSuccesses(reroll);
            totalHits += rerollHits;
            player.setExpectedHitsTimes10(
                    player.getExpectedHitsTimes10() + (11 - toldarRerollToHit + toldarRerollModifier));
            String rerollText = CombatMessageHelper.displayUnitRoll(
                    toldarRerollUnit,
                    toldarRerollToHit,
                    toldarRerollModifier,
                    toldarRerollUnitCount,
                    toldarRerollDiceCount,
                    0,
                    reroll,
                    rerollHits);
            payloadBuilder.addUnitRoll(
                    toldarRerollUnit,
                    toldarRerollToHit,
                    toldarRerollModifier,
                    toldarRerollUnitCount,
                    toldarRerollDiceCount,
                    0,
                    RollSegmentType.TOLDAR_COMMANDER_REROLL_MISS,
                    reroll,
                    rerollHits,
                    DieRollSource.REROLL_MISS);
            resultBuilder
                    .append("Rerolling 1 miss from ")
                    .append(toldarRerollUnit.getName())
                    .append(" due to Baird Feraux, the Toldar Commander:\n")
                    .append(rerollText);
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
        if (totalHits > 0
                && rollType != CombatRollType.combatround
                && game.playerHasLeaderUnlockedOrAlliance(player, "xytheriscommander")) {
            totalHits++;
            result += "\n" + player.getFactionEmoji()
                    + " produced 1 additional hit from _Zythrix_ the Xytheris Commander.";
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

    private static String misses(int count) {
        return count + " miss" + (count == 1 ? "" : "es");
    }

    private static int countOnes(List<Die> dice) {
        return (int) dice.stream().filter(die -> die.getResult() == 1).count();
    }

    private record RerollTarget(UnitModel unit, int toHit, int modifier, int numOfUnit, int numRollsPerUnit) {

        List<Die> roll(Player player, int count) {
            List<Die> dice = DiceHelper.rollDice(toHit - modifier, count);
            player.setExpectedHitsTimes10(player.getExpectedHitsTimes10() + (count * (11 - toHit + modifier)));
            return dice;
        }

        List<Die> rerollMisses(Player player, List<Die> resultRolls, int count) {
            List<Die> dice = roll(player, count);
            // Very important to remove the rerolled dice from the original dice pool
            resultRolls.removeIf(Predicate.not(Die::isSuccess));
            return dice;
        }

        double hitChance(int count) {
            return Math.pow((11 - toHit + modifier) / 10.0, count);
        }

        double missChance(int count) {
            return Math.pow((toHit - modifier - 1) / 10.0, count);
        }

        void record(
                RollPayloadBuilder payloadBuilder,
                StringBuilder resultBuilder,
                String header,
                int extraRolls,
                List<Die> dice,
                int hits,
                RollSegmentType segmentType,
                DieRollSource source) {
            String unitRoll = CombatMessageHelper.displayUnitRoll(
                    unit, toHit, modifier, numOfUnit, numRollsPerUnit, extraRolls, dice, hits);
            if (payloadBuilder != null) {
                payloadBuilder.addUnitRoll(
                        unit, toHit, modifier, numOfUnit, numRollsPerUnit, extraRolls, segmentType, dice, hits, source);
            }
            resultBuilder.append(header).append(unitRoll);
        }
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
                        modifier.getMaxDice(),
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
            addUnitRoll(
                    unitModel,
                    toHit,
                    modifier,
                    unitQuantity,
                    numRollsPerUnit,
                    extraRolls,
                    segmentType,
                    resultRolls,
                    hits,
                    source,
                    List.of());
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
                DieRollSource source,
                List<Integer> modifiersByDie) {
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
                    modifiersByDie,
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

        private static List<CombatRollPayload.DieRoll> toDieRolls(
                List<DiceHelper.Die> resultRolls, DieRollSource source) {
            if (resultRolls.isEmpty()) return List.of();
            return resultRolls.stream()
                    .map(die ->
                            new CombatRollPayload.DieRoll(die.getResult(), die.getThreshold(), die.isSuccess(), source))
                    .toList();
        }

        private static String getDisplayedUnitName(UnitModel unitModel) {
            if (unitModel.getUpgradesFromUnitId().isPresent()
                    || unitModel.getFaction().isPresent()) {
                return unitModel.getName();
            }
            return "";
        }

        private static String resolveScopeDisplay(CombatModifierModel modifier, Map<UnitModel, Integer> units) {
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
        return flattenUnitMap(getUnitsInCombatByHolder(tile, unitHolder, player, event, roleType, game));
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
        UnitHolder spaceHolder = tile.getSpaceUnitHolder();

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
        XytherisAbilityHandler.getBestHiveEchoUnit(tile, player, CombatRollType.AFB)
                .ifPresent(unit -> output.putIfAbsent(unit, 1));
        if (player.hasUnit("iron_flagship")) {
            IronUnitsHandler.getIronFlagshipAfbUnits(player, tile)
                    .forEach((model, count) -> output.put(new ImmutablePair<>(model, spaceHolder), count));
        }
        if (player.hasUnit("vyserix_flagship")) {
            VyserixUnitHandler.getVyserixFlagshipAfbUnits(player, tile)
                    .forEach((model, count) -> output.put(new ImmutablePair<>(model, spaceHolder), count));
        }
        checkBadUnits(player, event, unitsByAsyncId, flattenUnitMap(output));

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
        unitHolder
                .getUnitAsyncIdsOnHolder(colorID)
                .forEach((id, count) -> unitsByAsyncId.merge(id, count, Integer::sum));
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
            unitsByAsyncId.merge(unitEntry.getKey(), unitEntry.getValue(), Integer::sum);
        }
    }

    private static UnitModel starfallSpaceCannonUnit(Player player, int hitsOn) {
        return fakeSpaceCannonUnit(player, "starfallpds", "Starfall Gunnery space cannon", 1, hitsOn);
    }

    public static Map<UnitModel, Integer> getProximaBombardUnit(Player player) {
        UnitModel proximaFakeUnit =
                fakeUnit(player, "ProximaBombard", Mapper.getTech("proxima").getName(), "dn");
        proximaFakeUnit.setBombardDieCount(3);
        proximaFakeUnit.setBombardHitsOn(player.hasTech("tf-proxima") ? 7 : 8);
        return Map.of(proximaFakeUnit, 1);
    }

    public static Map<Pair<UnitModel, UnitHolder>, Integer> getUnitsInBombardment(
            Tile tile, Player player, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        UnitHolder spaceHolder = tile.getSpaceUnitHolder();
        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            getUnitsOnHolderByAsyncId(colorID, unitsByAsyncId, unitHolder);
        }
        Map<UnitModel, Integer> unitsInCombat = getUnitsInCombat(player, unitsByAsyncId);

        Map<Pair<UnitModel, UnitHolder>, Integer> output = new HashMap<>(unitsInCombat.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().getBombardDieCount(player) > 0)
                .collect(Collectors.toMap(
                        entry -> new ImmutablePair<>(entry.getKey(), spaceHolder), Map.Entry::getValue)));
        XytherisAbilityHandler.getBestHiveEchoUnit(tile, player, CombatRollType.bombardment)
                .ifPresent(unit -> output.putIfAbsent(unit, 1));
        checkBadUnits(player, event, unitsByAsyncId, flattenUnitMap(output));
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
        getUnitsOnHolderByAsyncId(colorID, unitsByAsyncId, planet);
        Map<UnitModel, Integer> unitsOnPlanet = getUnitsInCombat(player, unitsByAsyncId);

        for (Map.Entry<UnitKey, Integer> unitEntry : planet.getUnits().entrySet()) {
            Player structureOwner =
                    game.getPlayerByColorID(unitEntry.getKey().colorID()).orElse(null);
            boolean graveholdControlled =
                    SarcosaBreakthroughHandler.canControlNeutralStructure(game, player, planet, unitEntry.getKey());
            UnitModel structure = graveholdControlled
                    ? SarcosaBreakthroughHandler.getControlledNeutralStructureModel(
                            game, player, planet, unitEntry.getKey())
                    : structureOwner == null ? null : structureOwner.getUnitFromUnitKey(unitEntry.getKey());
            if ((ThurvialiTechHandler.canUseCoexistingStructure(game, player, planet, unitEntry.getKey())
                            || graveholdControlled)
                    && structure != null) {
                unitsOnPlanet.merge(structure, unitEntry.getValue(), Integer::sum);
            }
        }

        // Check for space cannon die on planet
        String ccID = Mapper.getControlID(player.getColor());
        if (player.controlsMecatol(true) && game.mecatols().contains(planet.getName()) && player.hasIIHQ()) {
            PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
            planet.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
            planet.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
        }
        if (planet.getControlList().contains(ccID) && planet.getSpaceCannonDieCount() > 0) {
            unitsOnPlanet.put(
                    planetSpaceCannonUnit(
                            player, planet, planet.getSpaceCannonDieCount(), planet.getSpaceCannonHitsOn()),
                    1);
        }
        if (player.hasUnlockedBreakthrough("aeternabt")) {
            UnitModel twilightCannon = AeternaBreakthroughHandler.getTwilightDefenseCannon(player, planet, true);
            if (twilightCannon != null) {
                unitsOnPlanet.put(twilightCannon, 1);
            }
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
        UnitHolder spaceHolder = tile.getSpaceUnitHolder();

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
            for (Map.Entry<UnitKey, Integer> unitEntry : unitHolder.getUnits().entrySet()) {
                Player structureOwner =
                        game.getPlayerByColorID(unitEntry.getKey().colorID()).orElse(null);
                boolean graveholdControlled = SarcosaBreakthroughHandler.canControlNeutralStructure(
                        game, player, unitHolder, unitEntry.getKey());
                UnitModel structure = graveholdControlled
                        ? SarcosaBreakthroughHandler.getControlledNeutralStructureModel(
                                game, player, unitHolder, unitEntry.getKey())
                        : structureOwner == null ? null : structureOwner.getUnitFromUnitKey(unitEntry.getKey());
                if ((ThurvialiTechHandler.canUseCoexistingStructure(game, player, unitHolder, unitEntry.getKey())
                                || graveholdControlled)
                        && structure != null) {
                    unitsOnTile.merge(new ImmutablePair<>(structure, unitHolder), unitEntry.getValue(), Integer::sum);
                }
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
            if (TeHelperUnits.affectedByQuietus(game, player, adjTile)
                    || (adjTile.isScar(game) && !player.hasTech("dsxinystar"))
                    || ButtonHelper.isTileSmothered(game, adjTile, player)) {
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
                for (Map.Entry<UnitKey, Integer> unitEntry :
                        unitHolder.getUnits().entrySet()) {
                    Player structureOwner = game.getPlayerByColorID(
                                    unitEntry.getKey().colorID())
                            .orElse(null);
                    boolean graveholdControlled = SarcosaBreakthroughHandler.canControlNeutralStructure(
                            game, player, unitHolder, unitEntry.getKey());
                    UnitModel structure = graveholdControlled
                            ? SarcosaBreakthroughHandler.getControlledNeutralStructureModel(
                                    game, player, unitHolder, unitEntry.getKey())
                            : structureOwner == null ? null : structureOwner.getUnitFromUnitKey(unitEntry.getKey());
                    if ((ThurvialiTechHandler.canUseCoexistingStructure(game, player, unitHolder, unitEntry.getKey())
                                    || graveholdControlled)
                            && structure != null) {
                        unitsOnAdjacentTiles.merge(
                                new ImmutablePair<>(structure, unitHolder), unitEntry.getValue(), Integer::sum);
                    }
                }
                if (unitHolder instanceof Planet planet) {
                    if (player.hasUnlockedBreakthrough("aeternabt")) {
                        UnitModel twilightCannon =
                                AeternaBreakthroughHandler.getTwilightDefenseCannon(player, planet, true);
                        if (twilightCannon != null) {
                            unitsOnAdjacentTiles.put(new ImmutablePair<>(twilightCannon, unitHolder), 1);
                        }
                    }
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
                String ccID = Mapper.getControlID(player.getColor());
                if (planet.getControlList().contains(ccID) && planet.getSpaceCannonDieCount() > 0) {
                    unitsOnTile.put(
                            new ImmutablePair<>(
                                    planetSpaceCannonUnit(
                                            player,
                                            planet,
                                            planet.getSpaceCannonDieCount(),
                                            planet.getSpaceCannonHitsOn()),
                                    unitHolder),
                            1);
                }
                if (player.hasUnlockedBreakthrough("aeternabt")) {
                    UnitModel twilightCannon =
                            AeternaBreakthroughHandler.getTwilightDefenseCannon(player, planet, true);
                    if (twilightCannon != null) {
                        unitsOnTile.put(new ImmutablePair<>(twilightCannon, unitHolder), 1);
                    }
                }
                boolean spaceStation =
                        (player.hasUnlockedBreakthrough("gledgebt") || player.hasTech("tf-mantlecracking"))
                                && planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG);
                if ((planet.isSpaceStation(game) || spaceStation)
                        && player.getPlanets().contains(planet.getName())) {
                    if (player.hasUnlockedBreakthrough("gledgebt")) {
                        unitsOnTile.put(
                                new ImmutablePair<>(planetSpaceCannonUnit(player, planet, 1, 5), unitHolder), 1);
                    }
                    if (player.hasTech("tf-deepinstallations")) {
                        unitsOnTile.put(
                                new ImmutablePair<>(planetSpaceCannonUnit(player, planet, 2, 5), unitHolder), 1);
                    }
                }
            }
        }
        if (player.hasAbility("starfall_gunnery")) {
            if (player == game.getActivePlayer()) {
                int count = Math.min(3, ButtonHelper.checkNumberNonFighterShipsWithoutSpaceCannon(player, tile));
                if (count > 0) {
                    unitsOnTile.put(new ImmutablePair<>(starfallSpaceCannonUnit(player, 8), spaceHolder), count);
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
                    unitsOnTile.put(new ImmutablePair<>(starfallSpaceCannonUnit(player, 9), spaceHolder), count);
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
        XytherisAbilityHandler.getBestHiveEchoUnit(tile, player, CombatRollType.SpaceCannonOffence)
                .ifPresent(unit -> output.putIfAbsent(unit, 1));
        UnitModel sigilCannon = ArcanumTechHandler.getSigilOfTransmutationSpaceCannon(game, player, tile);
        if (sigilCannon != null) {
            output.put(new ImmutablePair<>(sigilCannon, spaceHolder), 1);
        }

        checkBadUnits(player, event, unitsByAsyncId, flattenUnitMap(output));

        for (UnitModel riggedExplosivesCannon :
                RiggedExplosivesLLButtonHandler.getRiggedExplosivesCannons(game, player, tile)) {
            unitsOnTile.put(new ImmutablePair<>(riggedExplosivesCannon, spaceHolder), 1);
        }

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
        countByIdentity.putAll(playerUnits);
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
            if (keys.stream().anyMatch(key -> MassHypnosisLLButtonHandler.isHypnotizedRollModel(key.getLeft()))) {
                divergingModels.add(modelEntry.getKey());
                for (Pair<UnitModel, UnitHolder> key : keys) {
                    merged.put(key, countByIdentity.get(key));
                }
                continue;
            }
            if (keys.size() == 1) {
                Pair<UnitModel, UnitHolder> k = keys.getFirst();
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
                merged.put(keys.getFirst(), totalCount);
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
