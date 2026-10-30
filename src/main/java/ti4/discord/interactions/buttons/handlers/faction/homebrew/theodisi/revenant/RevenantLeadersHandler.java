package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.revenant;

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
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn.StonebornLeadersHandler;
import ti4.discord.interactions.buttons.ids.AutoAssignGroundHitsButtonIds;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.RandomHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.DSHelperBreakthroughs;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.Mapper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.service.leader.PlayHeroService;
import ti4.service.leader.PurgeHeroService;
import ti4.service.leader.agent.modules.homebrew.theodisi.RevenantVerydithAgent;
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;

@UtilityClass
public class RevenantLeadersHandler {
    // Revenant of Ruin
    private static final String REVTHRONESHERO = "revenantthroneshero";
    private static final String USE_REVTHRONES_HERO = "useRevenantThronesHero_";
    private static final String RESOLVE_REVTHRONES_HERO = "resolveRevenantThronesHero_";
    // Revenant of Oblivion
    private static final String REVOBLCOMMANDER = "revenantoblivioncommander";
    private static final String USE_ARLIR_MIRRORED = "useArlirMirrored_";
    // Revenant of Kairn
    private static final Set<String> EXPLORATION_TRAITS =
            Set.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL);
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
    // Revenant Base Hero
    private static final String REVENANT_HERO = "revenanthero";
    private static final String SELECT_REVENANT_HERO = "selectRevenantHero_";
    private static final String REVENANT_HERO_CHOICES = "revenantHeroChoices_";
    private static final String REVENANT_UNAVAILABLE_HEROES = "revenantUnavailableHeroes";
    // Revenant of Ponthous
    private static final String REVPONTHOUS = "revenantponthouscommander";
    private static final String SELECT_REVPONTHOUS_INFANTRY = "selectRevenantPonthousInfantry_";
    private static final String PLACE_REVPONTHOUS_UNIT = "placeRevenantPonthousUnit_";
    private static final String REVPONTHOUS_USED = "revenantPonthousCommanderUsed_";
    // Revenant of Myrr
    private static final String USE_REVMYRR = "useRevenantMyrrHero";
    private static final String SELECT_REVMYRR_SYSTEM = "selectRevenantMyrrSystem_";
    private static final String REVMYRR_PRODUCTION = "revenantMyrrProduction_";
    private static final String REVMYRR_USED = "revenantMyrrHeroUsed_";
    // Revenant of Xytheris
    private static final String REVXYTHERISCOMMANDER = "revenantxytheriscommander";
    // Revenant of Kryxos
    private static final String REVKRYXOS = "revenantkryxoshero";
    private static final String USE_REVKRYXOS = "useRevenantKryxosHero_";
    private static final String SELECT_REVKRYXOS_TECH = "selectRevenantKryxosTech_";
    private static final String DECLINE_REVKRYXOS = "declineRevenantKryxosHero";
    private static final String REVKRYXOS_CONTEXT = "revenantKryxosHeroContext_";
    private static final String REVKRYXOS_FIRST_TECH = "revenantKryxosHeroFirstTech_";
    // Revenant of Veylor
    private static final String REVVEYLORCOMMANDER = "revenantveylorcommander";
    // Revenant of Thurviali
    private static final String SELECT_REVTHURVIALI_TARGET = "selectRevenantThurvialiTarget_";
    private static final String PLACE_REVTHURVIALI_INFANTRY = "placeRevenantThurvialiInfantry_";
    private static final String FINISH_REVTHURVIALI_HERO = "finishRevenantThurvialiHero";
    private static final String REVTHURVIALI_REMAINING = "revenantThurvialiHeroRemaining_";

    public static void startRevThurvialiHero(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Player> targets = game.getRealPlayers().stream()
                .filter(target -> !target.isNeutral())
                .filter(target -> target.getPlanets().stream()
                        .map(game::getUnitHolderFromPlanet)
                        .filter(Objects::nonNull)
                        .map(planet -> game.getTileFromPlanet(planet.getName()))
                        .anyMatch(tile -> tile != null && !tile.isHomeSystem(game)))
                .toList();
        if (targets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has no eligible planets for **Tainted Beacon**.");
            return;
        }
        game.setStoredValue(
                REVTHURVIALI_REMAINING + player.getFaction(),
                targets.stream().map(Player::getFaction).collect(java.util.stream.Collectors.joining("|")));
        List<Button> buttons = new ArrayList<>();
        for (Player target : targets) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_REVTHURVIALI_TARGET + target.getFaction(),
                    "Place Infantry on " + target.getFactionNameOrColor(),
                    target.fogSafeEmoji()));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + FINISH_REVTHURVIALI_HERO, "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", choose each player whose non-home planet will receive 1 infantry from **Tainted Beacon**.",
                buttons);
    }

    @ButtonHandler(SELECT_REVTHURVIALI_TARGET)
    public static void selectRevThurvialiTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_REVTHURVIALI_TARGET.length()));
        String remaining = game.getStoredValue(REVTHURVIALI_REMAINING + player.getFaction());
        if (target == null || !List.of(remaining.split("\\|", -1)).contains(target.getFaction())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String buttonPrefix = player.factionButtonChecker() + PLACE_REVTHURVIALI_INFANTRY + target.getFaction();
        PlanetTargetSpec targetSpec = PlanetTargetSpec.of(buttonPrefix)
                .requiringController()
                .where(planet -> {
                    Tile tile = game.getTileFromPlanet(planet.getName());
                    return tile != null && !tile.isHomeSystem(game);
                });
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : target.getPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(Objects::nonNull)
                .toList()) {
            Tile tile = game.getTileFromPlanet(planet.getName());
            if (planet == null || tile == null || tile.isHomeSystem(game)) {
                continue;
            }
            buttons.add(Buttons.green(
                    buttonPrefix + "_" + planet.getName(), "Place Infantry on " + planet.getRepresentation(game)));
        }
        buttons = PlanetTargetService.targetButtons(game, player, targetSpec, buttons);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That player no longer controls an eligible planet.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a non-home planet controlled by "
                        + target.getRepresentationNoPing() + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_REVTHURVIALI_INFANTRY)
    public static void placeRevThurvialiInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getRealPlayers().stream()
                .filter(candidate -> buttonID.startsWith(PLACE_REVTHURVIALI_INFANTRY + candidate.getFaction() + "_"))
                .findFirst()
                .orElse(null);
        String buttonPrefix = target == null
                ? null
                : player.factionButtonChecker() + PLACE_REVTHURVIALI_INFANTRY + target.getFaction();
        PlanetTargetSpec targetSpec = buttonPrefix == null
                ? null
                : PlanetTargetSpec.of(buttonPrefix).requiringController().where(candidate -> {
                    Tile candidateTile = game.getTileFromPlanet(candidate.getName());
                    return candidateTile != null && !candidateTile.isHomeSystem(game);
                });
        if (targetSpec != null && PlanetTargetService.handlePlanetPage(event, game, player, buttonID, targetSpec)) {
            return;
        }
        var resolvedTarget = targetSpec == null
                ? null
                : PlanetTargetService.resolve(
                        game,
                        player,
                        buttonID,
                        targetSpec,
                        candidate -> candidate.owner() == target
                                && target.getPlanets().contains(candidate.planetId()));
        Planet planet = resolvedTarget == null ? null : resolvedTarget.unitHolder();
        Tile tile = resolvedTarget == null ? null : resolvedTarget.tile();
        String remaining = game.getStoredValue(REVTHURVIALI_REMAINING + player.getFaction());
        if (target == null
                || planet == null
                || tile == null
                || tile.isHomeSystem(game)
                || !target.getPlanets().contains(planet.getName())
                || !List.of(remaining.split("\\|", -1)).contains(target.getFaction())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String coexistFlag = game.getStoredValue("coexistFlag");
        if (!planet.getUnitKeysForPlayer(target).isEmpty()) {
            game.setStoredValue("coexistFlag", "yes");
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "inf " + planet.getName());
        if (coexistFlag.isEmpty()) {
            game.removeStoredValue("coexistFlag");
        } else {
            game.setStoredValue("coexistFlag", coexistFlag);
        }
        List<String> remainingTargets = new ArrayList<>(List.of(remaining.split("\\|", -1)));
        remainingTargets.remove(target.getFaction());
        if (remainingTargets.isEmpty()) {
            game.removeStoredValue(REVTHURVIALI_REMAINING + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " finished resolving **Tainted Beacon**.");
        } else {
            game.setStoredValue(REVTHURVIALI_REMAINING + player.getFaction(), String.join("|", remainingTargets));
            List<Button> buttons = new ArrayList<>();
            for (String faction : remainingTargets) {
                Player remainingTarget = game.getPlayerFromColorOrFaction(faction);
                if (remainingTarget != null) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + SELECT_REVTHURVIALI_TARGET + remainingTarget.getFaction(),
                            "Place Infantry on " + remainingTarget.getFactionNameOrColor(),
                            remainingTarget.fogSafeEmoji()));
                }
            }
            buttons.add(Buttons.red(player.factionButtonChecker() + FINISH_REVTHURVIALI_HERO, "Done"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + ", choose another player for **Tainted Beacon**, or finish resolving it.",
                    buttons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(FINISH_REVTHURVIALI_HERO)
    public static void finishRevThurvialiHero(ButtonInteractionEvent event, Game game, Player player) {
        game.removeStoredValue(REVTHURVIALI_REMAINING + player.getFaction());
        ButtonHelper.deleteMessage(event);
    }

    public static void addRevThronesHeroButton(
            List<Button> buttons, Game game, Player player, Player opponent, Tile tile, String unitHolderName) {
        if (buttons == null
                || game == null
                || player == null
                || opponent == null
                || tile == null
                || unitHolderName == null
                || !player.hasLeader(REVTHRONESHERO)
                || !player.hasLeaderUnlocked(REVTHRONESHERO)
                || tile.getUnitHolders().get(unitHolderName) == null
                || tile.getUnitHolders()
                        .get(unitHolderName)
                        .getUnitKeysForPlayer(player)
                        .isEmpty()) {
            return;
        }
        buttons.add(Buttons.red(
                player.factionButtonChecker() + USE_REVTHRONES_HERO + tile.getPosition() + "|" + unitHolderName + "|"
                        + opponent.getFaction(),
                "Use Revenant Thrones Hero",
                FactionEmojis.revenant));
    }

    @ButtonHandler(USE_REVTHRONES_HERO)
    public static void offerRevThronesHeroUnitButtons(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_REVTHRONES_HERO.length()).split("\\|", 3);
        Tile tile = payload.length == 3 && game != null ? game.getTileByPosition(payload[0]) : null;
        UnitHolder holder =
                payload.length == 3 && tile != null ? tile.getUnitHolders().get(payload[1]) : null;
        Player opponent = payload.length == 3 && game != null ? game.getPlayerFromColorOrFaction(payload[2]) : null;
        if (player == null
                || tile == null
                || holder == null
                || opponent == null
                || opponent == player
                || !player.hasLeader(REVTHRONESHERO)
                || !player.hasLeaderUnlocked(REVTHRONESHERO)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
            UnitModel unit = player.getUnitFromUnitKey(unitKey);
            if (unit == null) {
                continue;
            }
            int hits = (int) Math.ceil(unit.getCost());
            for (UnitState state : holder.getNonZeroUnitStates(unitKey)) {
                if (holder.getUnitCountForState(unitKey, state) < 1) {
                    continue;
                }
                String stateText = state == UnitState.none ? "" : state.humanDescr() + " ";
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + RESOLVE_REVTHRONES_HERO + tile.getPosition() + "|"
                                + holder.getName() + "|" + opponent.getFaction() + "|" + unitKey.unitType() + "|"
                                + state,
                        "Destroy 1 " + stateText + unitKey.humanReadableName() + " (" + hits + " Hit"
                                + (hits == 1 ? "" : "s") + ")",
                        unitKey.unitEmoji()));
            }
        }
        if (buttons.isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", choose a unit involved in this combat to destroy with **Lost Throne of Pride**.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(RESOLVE_REVTHRONES_HERO)
    public static void resolveRevThronesHero(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(RESOLVE_REVTHRONES_HERO.length()).split("\\|", 5);
        Tile tile = payload.length == 5 && game != null ? game.getTileByPosition(payload[0]) : null;
        UnitHolder holder =
                payload.length == 5 && tile != null ? tile.getUnitHolders().get(payload[1]) : null;
        Player opponent = payload.length == 5 && game != null ? game.getPlayerFromColorOrFaction(payload[2]) : null;
        UnitKey unitKey = payload.length == 5 && holder != null
                ? holder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(payload[3]))
                        .findFirst()
                        .orElse(null)
                : null;
        UnitState state = payload.length == 5 ? ti4.helpers.Units.findUnitState(payload[4]) : null;
        UnitModel unit = unitKey == null || player == null ? null : player.getUnitFromUnitKey(unitKey);
        if (player == null
                || tile == null
                || holder == null
                || opponent == null
                || opponent == player
                || unitKey == null
                || state == null
                || unit == null
                || !player.hasLeader(REVTHRONESHERO)
                || !player.hasLeaderUnlocked(REVTHRONESHERO)
                || holder.getUnitCountForState(unitKey, state) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        int hits = (int) Math.ceil(unit.getCost());
        DestroyUnitService.destroyUnit(event, tile, game, new ParsedUnit(unitKey, 1, holder.getName()), true, state);
        PurgeHeroService.purgeHeroPreamble(event, player, game, REVTHRONESHERO, "Lost Throne of Pride - Fallen King");
        String message = player.getRepresentationNoPing() + " destroyed 1 " + unitKey.humanReadableName()
                + " with **Lost Throne of Pride** to produce " + hits + " hit" + (hits == 1 ? "" : "s") + ".\n"
                + opponent.getRepresentationNoPing() + ", assign the produced hits.";
        List<Button> hitButtons = new ArrayList<>();
        if (Constants.SPACE.equals(holder.getName())) {
            hitButtons.add(Buttons.green(
                    opponent.factionButtonChecker() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + hits,
                    "Auto-assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
            hitButtons.add(Buttons.red(
                    opponent.factionButtonChecker() + "getDamageButtons_" + tile.getPosition()
                            + "deleteThis_spacecombat",
                    "Manually Assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
        } else {
            hitButtons.add(Buttons.green(
                    opponent.factionButtonChecker() + AutoAssignGroundHitsButtonIds.format(holder.getName(), hits),
                    "Auto-assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
            hitButtons.add(Buttons.red(
                    opponent.factionButtonChecker() + "getDamageButtons_" + tile.getPosition()
                            + "deleteThis_groundcombat",
                    "Manually Assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, hitButtons);
        ButtonHelper.deleteMessage(event);
    }

    // Pantheon of Production
    // Revenant of Ponthous
    public static void offerRevPonthousCommander(Game game, Player player, Tile tile) {
        if (game == null
                || player == null
                || tile == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, REVPONTHOUS)
                || !game.getStoredValue(REVPONTHOUS_USED + player.getFaction()).isEmpty()) {
            return;
        }
        int fightersProduced = player.getCurrentProducedUnits().entrySet().stream()
                .filter(entry -> tile.getPosition().equals(getProducedUnitTilePosition(entry.getKey())))
                .filter(entry -> "ff".equals(getProducedUnitAlias(entry.getKey())))
                .mapToInt(java.util.Map.Entry::getValue)
                .sum();
        int infantryProduced = player.getCurrentProducedUnits().entrySet().stream()
                .filter(entry -> tile.getPosition().equals(getProducedUnitTilePosition(entry.getKey())))
                .filter(entry -> "gf".equals(getProducedUnitAlias(entry.getKey())))
                .mapToInt(java.util.Map.Entry::getValue)
                .sum();
        List<Button> buttons = new ArrayList<>();
        if (fightersProduced >= 2) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_REVPONTHOUS_UNIT + tile.getPosition() + "|ff|space",
                    "Place 1 Fighter"));
        }
        if (infantryProduced >= 2) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_REVPONTHOUS_INFANTRY + tile.getPosition(),
                    "Place 1 Infantry"));
        }
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.DECLINE_DELETE_BUTTONS);

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may place 1 additional fighter or infantry in the system due to Melloh Terras, the Revenant of Ponthous commander."
                        + "\n-# You must produce at least 2 infantry or 2 fighters, depending on what you're producing, to do this.",
                buttons);
    }

    @ButtonHandler(SELECT_REVPONTHOUS_INFANTRY)
    public static void offerRevPonthousCommanderInfantryLocations(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null
                || player == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, REVPONTHOUS)
                || !game.getStoredValue(REVPONTHOUS_USED + player.getFaction()).isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = game.getTileByPosition(buttonID.substring(SELECT_REVPONTHOUS_INFANTRY.length()));
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        String prefix = player.factionButtonChecker() + PLACE_REVPONTHOUS_UNIT + tile.getPosition() + "|gf|";
        if (ButtonHelper.canIBuildGFInSpace(player, tile, "normal")) {
            buttons.add(Buttons.green(prefix + "space", "Place 1 Infantry in Space"));
        }
        tile.getPlanetUnitHolders().stream()
                .filter(planet -> planet.getUnitCount(UnitType.Spacedock, player) > 0)
                .map(Planet::getName)
                .map(planetName -> Buttons.green(
                        prefix + planetName, "Place 1 Infantry on " + Helper.getPlanetRepresentation(planetName, game)))
                .forEach(buttons::add);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        buttons.add(Buttons.DECLINE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose where to place the infantry with Melloh Terras.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_REVPONTHOUS_UNIT)
    public static void placeRevPonthousCommanderUnit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null
                || player == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, REVPONTHOUS)
                || !game.getStoredValue(REVPONTHOUS_USED + player.getFaction()).isEmpty()) {
            return;
        }

        String[] payload = buttonID.substring(PLACE_REVPONTHOUS_UNIT.length()).split("\\|", 3);
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
                                || !"gf".equals(unit)
                                || tile.getUnitHolders().get(location).getUnitCount(UnitType.Spacedock, player) < 1))) {
            return;
        }

        game.setStoredValue(REVPONTHOUS_USED + player.getFaction(), "true");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + unit + " " + location);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed 1 " + ("ff".equals(unit) ? "fighter" : "infantry") + " in "
                        + tile.getRepresentationForButtons(game, player)
                        + " due to Melloh Terras, the Revenant of Ponthous commander.");
        ButtonHelper.deleteMessage(event);
    }

    // Revenant of Myrr
    public static Button getRevMyrrHeroButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_REVMYRR, "Use Revenant Myrr Hero", FactionEmojis.revenant);
    }

    @ButtonHandler(USE_REVMYRR)
    public static void offerRevMyrrHeroSystems(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!canUseRevMyrrHero(game, player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "DOMI.N.O, the Revenant of Myrr hero cannot be used right now.");
            return;
        }

        List<Button> buttons = getRevMyrrHeroSystemButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You do not have any units on the game board.");
            return;
        }

        game.setStoredValue(REVMYRR_USED + player.getFaction(), "pending");
        PurgeHeroService.purgeHeroPreamble(event, player, game, "revenantmyrrhero", "Revenant of Myrr");

        String message = player.getRepresentation()
                + ", choose a system containing 1 or more of your units in which to use PRODUCTION 4 due to DOMI.N.O, the Revenant of Myrr hero.";
        String prefix = player.factionButtonChecker() + SELECT_REVMYRR_SYSTEM;
        List<Button> paginatedButtons = NewStuffHelper.buttonPagination(buttons, prefix, 0);
        if (buttons.size() <= 24) {
            paginatedButtons = new ArrayList<>(buttons);
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, paginatedButtons);
    }

    @ButtonHandler(SELECT_REVMYRR_SYSTEM)
    public static void useRevMyrrHero(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!"pending".equals(game.getStoredValue(REVMYRR_USED + player.getFaction()))
                || !game.getStoredValue(REVMYRR_PRODUCTION + player.getFaction())
                        .isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "DOMI.N.O, the Revenant of Myrr hero cannot be used right now.");
            return;
        }

        List<Button> buttons = getRevMyrrHeroSystemButtons(game, player);
        String message = player.getRepresentation()
                + ", choose a system containing 1 or more of your units in which to use PRODUCTION 4 due to DOMI.N.O, the Revenant of Myrr hero.";
        String prefix = player.factionButtonChecker() + SELECT_REVMYRR_SYSTEM;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        String position = buttonID.substring(SELECT_REVMYRR_SYSTEM.length());
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !tile.containsPlayersUnits(player)) {
            return;
        }

        game.setStoredValue(REVMYRR_PRODUCTION + player.getFaction(), position);
        List<Button> productionButtons =
                Helper.getPlaceUnitButtons(event, player, game, tile, "revenantMyrrHero", "place");
        int totalProduction = Helper.getProductionValue(player, game, tile, false);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", choose the units you wish to produce with DOMI.N.O, the Revenant of Myrr hero."
                        + " Total PRODUCTION in this system: " + totalProduction + ".",
                productionButtons);
        ButtonHelper.deleteMessage(event);
    }

    public static int getRevMyrrProduction(Game game, Player player, Tile tile) {
        if (game == null || player == null || tile == null) {
            return 0;
        }
        return tile.getPosition().equals(game.getStoredValue(REVMYRR_PRODUCTION + player.getFaction())) ? 4 : 0;
    }

    public static void clearPurpleLeaderActionState(Game game) {
        if (game == null) {
            return;
        }
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(REVPONTHOUS_USED)
                        || key.startsWith(REVMYRR_USED)
                        || key.startsWith(REVMYRR_PRODUCTION))
                .toList()
                .forEach(game::removeStoredValue);
    }

    public static void clearPantheonState(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }
        String faction = player.getFaction();
        game.removeStoredValue(REVKRYXOS_CONTEXT + faction);
        game.removeStoredValue(REVKRYXOS_FIRST_TECH + faction);
        game.removeStoredValue(REVPONTHOUS_USED + faction);
        game.removeStoredValue(REVMYRR_USED + faction);
        game.removeStoredValue(REVMYRR_PRODUCTION + faction);
        RevenantVerydithAgent.clearPending(game, player);
        game.removeStoredValue(REVTHURVIALI_REMAINING + faction);
    }

    public static boolean canUseRevMyrrHero(Game game, Player player) {
        return game != null
                && player != null
                && player.hasLeaderUnlocked("revenantmyrrhero")
                && game.getStoredValue(REVMYRR_PRODUCTION + player.getFaction()).isEmpty();
    }

    private static List<Button> getRevMyrrHeroSystemButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.containsPlayersUnits(player))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + SELECT_REVMYRR_SYSTEM + tile.getPosition(),
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

    // Revenant of Xytheris
    public static void addRevXytherisCommanderModifier(
            List<NamedCombatModifierModel> modifiers, Game game, Player player, CombatRollType rollType) {
        if (modifiers == null
                || game == null
                || player == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, REVXYTHERISCOMMANDER)) {
            return;
        }
        Player activePlayer = game.getActivePlayer();
        boolean applies = (rollType == CombatRollType.combatround && activePlayer != player)
                || (rollType != CombatRollType.combatround && activePlayer == player);
        if (!applies) {
            return;
        }
        var modifier = Mapper.getCombatModifiers().get("plus1_1tacticalaction_all");
        if (modifier != null) {
            modifiers.add(
                    new NamedCombatModifierModel(modifier, "+1 from Zexan Mythis, the Revenant of Xytheris commander"));
        }
    }

    // Revenant of Kryxos
    public static void addRevKryxosHeroButton(
            List<Button> buttons, Game game, Player player, Player opponent, Tile tile, boolean isSpaceCombat) {
        if (buttons == null
                || game == null
                || player == null
                || opponent == null
                || tile == null
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
                .allMatch(secondTech -> secondTech.getAlias().equals(firstTech.getAlias())));
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
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
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
        TechnologyModel firstTech = Mapper.getTech(game.getStoredValue(REVKRYXOS_FIRST_TECH + player.getFaction()));
        TechnologyModel secondTech = Mapper.getTech(techId);
        game.removeStoredValue(REVKRYXOS_CONTEXT + player.getFaction());
        game.removeStoredValue(REVKRYXOS_FIRST_TECH + player.getFaction());
        if (firstTech != null && secondTech != null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " researched " + firstTech.getNameRepresentation() + " and "
                            + secondTech.getNameRepresentation() + " with Pryxos Xiv, the Revenant of Kryxos hero.");
        }
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
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(REVKRYXOS_CONTEXT) || key.startsWith(REVKRYXOS_FIRST_TECH))
                .toList()
                .forEach(game::removeStoredValue);
    }

    public static void clearRedLeaderTacticalState(Game game) {
        if (game == null) {
            return;
        }
        clearRedLeaderTacticalWindow(game);
    }

    public static void offerRevVeylorCommanderPlanets(Game game) {
        if (game == null) {
            return;
        }
        for (Player player : game.getRealPlayers()) {
            if (!game.playerHasLeaderUnlockedOrAlliance(player, REVVEYLORCOMMANDER)) {
                continue;
            }
            List<Button> buttons = new ArrayList<>(Helper.getPlanetRefreshButtons(player, game));
            if (!buttons.isEmpty()) {
                buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Done Readying Planets"));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentation()
                                + ", ready up to 2 planets you control due to Herrith the Schismatic, the Revenant of Veylor commander.",
                        buttons);
            }
        }
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
                                .filter(Objects::nonNull)
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

            Collections.shuffle(heroPool, RandomHelper.current());
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
                .filter(Objects::nonNull)
                .map(LeaderModel::getRepresentationEmbed)
                .toList();

        List<Button> buttons = drawnHeroes.stream()
                .map(Mapper::getLeader)
                .filter(Objects::nonNull)
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
                .filter(Objects::nonNull)
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
        StonebornLeadersHandler.resolveTempusAfterNonActionCardComponentPurge(game, player);
    }

    // Green Revenant Leader Set
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
    private static final String SELECT_LICH_TARGET = "selectLichTarget_";
    private static final String SELECT_LICH_COMMANDER = "selectLichCommander_";

    public static List<Button> offerLichTokenChoices(Player player, Game game) {
        List<Button> targets = new ArrayList<>();
        for (Player target : game.getRealPlayersExcludingThis(player)) {
            targets.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_LICH_TARGET + target.getColor(),
                    target.getFaction(),
                    FactionEmojis.getFactionIcon(target.getFaction())));
        }

        return targets;
    }

    @ButtonHandler(SELECT_LICH_TARGET)
    public static void resolveAllureOfDarkness(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasAbility("allure_of_darkness")) {
            return;
        }

        String targetColor = buttonID.replace(SELECT_LICH_TARGET, "");
        Player target = game.getPlayerFromColorOrFaction(targetColor);
        if (target == null) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not find player.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Leader> commanders = target.getLeaders().stream()
                .filter(leader -> Constants.COMMANDER.equals(leader.getType()))
                .toList();
        if (commanders.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "That player has no commander.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (commanders.size() == 1) {
            placeLichToken(event, game, player, target, commanders.getFirst());
            return;
        }

        List<Button> buttons = commanders.stream()
                .map(commander -> Buttons.green(
                        player.factionButtonChecker() + SELECT_LICH_COMMANDER + target.getColor() + "|"
                                + commander.getId(),
                        commander.getName(),
                        FactionEmojis.revenant))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose which of " + target.getRepresentationNoPing()
                        + "'s commanders receives the lich token.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_LICH_COMMANDER)
    public static void resolveLichCommanderChoice(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasAbility("allure_of_darkness")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String[] payload = buttonID.replace(SELECT_LICH_COMMANDER, "").split("\\|", 2);
        Player target = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Leader commander = target == null || payload.length != 2
                ? null
                : target.getLeaderByID(payload[1]).orElse(null);
        if (target == null || commander == null || !Constants.COMMANDER.equals(commander.getType())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        placeLichToken(event, game, player, target, commander);
    }

    private static void placeLichToken(
            ButtonInteractionEvent event, Game game, Player player, Player target, Leader commander) {
        for (Player targets : game.getRealPlayers()) {
            player.clearAllDebtTokens(targets.getColor(), "lich");
        }
        game.setDebtPoolIcon("lich", FactionEmojis.revenant.emojiString());
        game.setStoredValue(
                "revenantLichCommander_" + player.getFaction(), target.getFaction() + "|" + commander.getId());
        player.addDebtTokens(target.getColor(), 1, "lich");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " placed the lich token on " + target.getRepresentationNoPing() + "'s "
                        + commander.getName() + " commander.");
        ButtonHelper.deleteMessage(event);
    }
}
