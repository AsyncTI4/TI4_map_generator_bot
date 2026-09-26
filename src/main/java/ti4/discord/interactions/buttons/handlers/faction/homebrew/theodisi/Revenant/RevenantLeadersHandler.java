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
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
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
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.leader.PlayHeroService;
import ti4.service.leader.PurgeHeroService;
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;

@UtilityClass
public class RevenantLeadersHandler {
    // Revenant of Stratum
    private static final String REVSTRATUMAGENT = "revenantstonebornagent";
    private static final String USE_REVSTRATUM_OTHER = "useRevenantStratumAgentOther";
    private static final String SELECT_REVSTRATUM_TARGET = "selectRevenantStratumTarget_";
    private static final String SELECT_REVSTRATUM_PLANET = "selectRevenantStratumPlanet_";
    private static final String EXPLORE_REVSTRATUM = "exploreRevenantStratum_";
    // Revenant of Ardentia
    private static final String REVARDENTIAAGENT = "revenantardentiaagent";
    private static final String USE_REVARDENTIA_AGENT = "useRevenantArdentiaAgent_";
    private static final String MOVE_REVARDENTIA_TOKEN = "moveRevenantArdentiaToken_";
    // Revenant of Ruin
    private static final String REVTHRONESHERO = "revenantthroneshero";
    private static final String USE_REVTHRONES_HERO = "useRevenantThronesHero_";
    private static final String RESOLVE_REVTHRONES_HERO = "resolveRevenantThronesHero_";
    // Revenant of Arcanum
    private static final String REVARCAGENT = "revenantarcanumagent";
    private static final String USE_REVARCAGENT = "useRevenantArcanumAgent_";
    private static final String SELECT_REVARCAGENT_TECH = "selectRevenantArcanumReturnTech_";
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
    // Revenant of Scrapyard
    private static final String REVSCRAPYARD = "revenantscrapyardagent";
    private static final String SELECT_REVSCRAPYARD_TARGET = "selectRevenantTarget";
    private static final String RESOLVE_REVSCRAPYARD_TARGET = "resolveRevenantTarget_";
    private static final String PRODUCE_WITH_REVSCRAPYARD = "produceUsingRevScrapyardAgent";
    private static final String PAGE_REVSCRAPYARD_SYSTEMS = "pageRevenantScrapyardSystems_";
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
    private static final String REVXYTHERIS = "revenantxytherisagent";
    private static final String REVXYTHERISCOMMANDER = "revenantxytheriscommander";
    private static final String USE_REVXYTHERIS = "useRevenantXytherisAgent_";
    private static final String SELECT_REVXYTHERIS_TARGET = "selectRevenantXytherisTarget";
    private static final String REVXYTHERIS_WINDOW = "revenantXytherisAgentWindow";
    private static final String REVXYTHERIS_TARGET = "revenantXytherisAgentTarget_";
    // Revenant of Kryxos
    private static final String REVKRYXOS = "revenantkryxoshero";
    private static final String USE_REVKRYXOS = "useRevenantKryxosHero_";
    private static final String SELECT_REVKRYXOS_TECH = "selectRevenantKryxosTech_";
    private static final String DECLINE_REVKRYXOS = "declineRevenantKryxosHero";
    private static final String REVKRYXOS_CONTEXT = "revenantKryxosHeroContext_";
    private static final String REVKRYXOS_FIRST_TECH = "revenantKryxosHeroFirstTech_";
    // Revenant of Veylor
    private static final String REVVEYLORCOMMANDER = "revenantveylorcommander";
    // Revenant of Verydith
    private static final String REVVERYDITHAGENT = "revenantverydithagent";
    private static final String USE_REVVERYDITH_AGENT = "useRevenantVerydithAgent_";
    private static final String DECLINE_REVVERYDITH_AGENT = "declineRevenantVerydithAgent_";
    private static final String SELECT_REVVERYDITH_TARGET = "selectRevenantVerydithTarget";
    private static final String REVVERYDITH_PENDING = "revenantVerydithPending_";
    // Revenant of Thurviali
    private static final String SELECT_REVTHURVIALI_TARGET = "selectRevenantThurvialiTarget_";
    private static final String PLACE_REVTHURVIALI_INFANTRY = "placeRevenantThurvialiInfantry_";
    private static final String FINISH_REVTHURVIALI_HERO = "finishRevenantThurvialiHero";
    private static final String REVTHURVIALI_REMAINING = "revenantThurvialiHeroRemaining_";

    public static Button getRevStratumCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_REVSTRATUM_OTHER,
                "Use Revenant Stratum Agent",
                FactionEmojis.revenant);
    }

    public static Button getRevVerydithCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + SELECT_REVVERYDITH_TARGET,
                "Use Revenant Verydith Agent",
                FactionEmojis.revenant);
    }

    public static boolean offerRevVerydithAgentPrompt(
            GenericInteractionCreateEvent event, Player player, Tile tile, boolean ping, boolean useTactic) {
        if (player == null
                || tile == null
                || !useTactic
                || !player.hasUnexhaustedLeader(REVVERYDITHAGENT)
                || !player.getGame()
                        .getStoredValue(REVVERYDITH_PENDING + player.getFaction())
                        .isEmpty()) {
            return false;
        }
        String payload = tile.getPosition() + "|" + (ping ? "1" : "0");
        player.getGame().setStoredValue(REVVERYDITH_PENDING + player.getFaction(), payload);
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + USE_REVVERYDITH_AGENT + payload,
                        "Use Revenant Verydith Agent",
                        FactionEmojis.revenant),
                Buttons.red(player.factionButtonChecker() + DECLINE_REVVERYDITH_AGENT + payload, "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event == null ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", you are about to spend a tactical command token to place one in "
                        + tile.getRepresentationForButtons(player.getGame(), player)
                        + ". You may exhaust **Koral Vel**, the Revenant of Verydith agent, to place it from reinforcements instead.",
                buttons);
        return true;
    }

    @ButtonHandler(USE_REVVERYDITH_AGENT)
    public static void useRevVerydithAgent(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(USE_REVVERYDITH_AGENT.length());
        if (payload.startsWith("other|")) {
            Player target = game.getPlayerFromColorOrFaction(payload.substring("other|".length()));
            Leader agent = player.getLeader(REVVERYDITHAGENT).orElse(null);
            if (target == null || agent == null || !player.hasUnexhaustedLeader(REVVERYDITHAGENT)) {
                ButtonHelper.deleteMessage(event);
                return;
            }
            ExhaustLeaderService.exhaustLeader(game, player, agent);
            target.setTacticalCC(target.getTacticalCC() + 1);
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " exhausted **Koral Vel**, the Revenant of Verydith agent, allowing "
                            + target.getRepresentationNoPing()
                            + " to spend a command token from reinforcements instead. "
                            + target.getRepresentationNoPing()
                            + " gained 1 tactical command token.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = payload.contains("|") ? game.getTileByPosition(payload.substring(0, payload.indexOf('|'))) : null;
        boolean ping = payload.endsWith("|1");
        Leader agent = player.getLeader(REVVERYDITHAGENT).orElse(null);
        if (tile == null
                || agent == null
                || !player.hasUnexhaustedLeader(REVVERYDITHAGENT)
                || !payload.equals(game.getStoredValue(REVVERYDITH_PENDING + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.removeStoredValue(REVVERYDITH_PENDING + player.getFaction());
        ExhaustLeaderService.exhaustLeader(game, player, agent);
        CommandCounterHelper.addCC(event, player, tile, ping, false, true);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DECLINE_REVVERYDITH_AGENT)
    public static void declineRevVerydithAgent(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(DECLINE_REVVERYDITH_AGENT.length());
        Tile tile = payload.contains("|") ? game.getTileByPosition(payload.substring(0, payload.indexOf('|'))) : null;
        boolean ping = payload.endsWith("|1");
        if (tile == null || !payload.equals(game.getStoredValue(REVVERYDITH_PENDING + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.removeStoredValue(REVVERYDITH_PENDING + player.getFaction());
        CommandCounterHelper.addCC(event, player, tile, ping, true, true);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolvePendingRevVerydithAgent(Game game, Player player, ButtonInteractionEvent event) {
        String payload = game.getStoredValue(REVVERYDITH_PENDING + player.getFaction());
        if (payload.isEmpty()) {
            return;
        }
        game.removeStoredValue(REVVERYDITH_PENDING + player.getFaction());
        int separator = payload.indexOf('|');
        Tile tile = separator < 0 ? null : game.getTileByPosition(payload.substring(0, separator));
        if (tile != null) {
            CommandCounterHelper.addCC(event, player, tile, payload.endsWith("|1"), true, true);
        }
    }

    @ButtonHandler(SELECT_REVVERYDITH_TARGET)
    public static void selectRevVerydithTarget(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(REVVERYDITHAGENT)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Koral Vel cannot be used right now.");
            return;
        }
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .map(target -> Buttons.green(
                        player.factionButtonChecker() + USE_REVVERYDITH_AGENT + "other|" + target.getFaction(),
                        target.getFactionNameOrColor(),
                        target.fogSafeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no other players to choose.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + ", choose a player to use **Koral Vel**, the Revenant of Verydith agent on.",
                buttons);
    }

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

    public static void addRevStratumExploreButtons(List<Button> buttons, Game game, Player player, Planet planet) {
        if (buttons == null
                || game == null
                || player == null
                || planet == null
                || !player.hasUnexhaustedLeader(REVSTRATUMAGENT)
                || !player.getPlanetsAllianceMode().contains(planet.getName())) {
            return;
        }
        for (String trait : EXPLORATION_TRAITS) {
            if (!planet.getPlanetTypes().contains(trait)) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + EXPLORE_REVSTRATUM + planet.getName() + "|" + trait,
                        "Explore as " + StringUtils.capitalize(trait),
                        FactionEmojis.revenant));
            }
        }
    }

    @ButtonHandler(USE_REVSTRATUM_OTHER)
    public static void offerRevStratumTargetButtons(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasUnexhaustedLeader(REVSTRATUMAGENT)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .filter(target -> !target.getPlanetsAllianceMode().isEmpty())
                .map(target -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_REVSTRATUM_TARGET + target.getFaction(),
                        "Use on " + target.getColor(),
                        target.fogSafeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "No other player controls a planet.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose a player to explore a planet with **Necrolith**.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_REVSTRATUM_TARGET)
    public static void offerRevStratumPlanetButtons(
            ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        Player target = game == null
                ? null
                : game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_REVSTRATUM_TARGET.length()));
        if (target == null
                || agentOwner == null
                || target == agentOwner
                || !agentOwner.hasUnexhaustedLeader(REVSTRATUMAGENT)
                || target.getPlanetsAllianceMode().isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = target.getPlanetsAllianceMode().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(Objects::nonNull)
                .map(planet -> Buttons.gray(
                        target.factionButtonChecker() + SELECT_REVSTRATUM_PLANET + agentOwner.getFaction() + "|"
                                + planet.getName(),
                        "Explore " + planet.getRepresentation(game)))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentation() + ", choose a planet to explore with **Necrolith**.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_REVSTRATUM_PLANET)
    public static void offerRevStratumTraitButtons(
            ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] values = buttonID.substring(SELECT_REVSTRATUM_PLANET.length()).split("\\|", 2);
        Player agentOwner = values.length == 2 && game != null ? game.getPlayerFromColorOrFaction(values[0]) : null;
        Planet planet = values.length == 2 && game != null ? game.getUnitHolderFromPlanet(values[1]) : null;
        if (agentOwner == null
                || target == null
                || planet == null
                || !agentOwner.hasUnexhaustedLeader(REVSTRATUMAGENT)
                || !target.getPlanetsAllianceMode().contains(planet.getName())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = EXPLORATION_TRAITS.stream()
                .map(trait -> Buttons.gray(
                        target.factionButtonChecker() + EXPLORE_REVSTRATUM + agentOwner.getFaction() + "|"
                                + planet.getName() + "|" + trait,
                        "Explore as " + StringUtils.capitalize(trait),
                        ExploreEmojis.getTraitEmoji(trait)))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentation() + ", choose an exploration trait for " + planet.getRepresentation(game)
                        + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(EXPLORE_REVSTRATUM)
    public static void exploreWithRevStratum(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(EXPLORE_REVSTRATUM.length()).split("\\|", 3);
        Player agentOwner = values.length == 3 && game != null ? game.getPlayerFromColorOrFaction(values[0]) : player;
        Planet planet = game == null || values.length < 2
                ? null
                : game.getUnitHolderFromPlanet(values.length == 3 ? values[1] : values[0]);
        String trait = values.length == 3 ? values[2] : (values.length == 2 ? values[1] : "");
        if (agentOwner == null
                || player == null
                || planet == null
                || !EXPLORATION_TRAITS.contains(trait)
                || !agentOwner.hasUnexhaustedLeader(REVSTRATUMAGENT)
                || !player.getPlanetsAllianceMode().contains(planet.getName())) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        Leader agent = agentOwner.getLeader(REVSTRATUMAGENT).orElse(null);
        Tile tile = game.getTileFromPlanet(planet.getName());
        if (agent == null || tile == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        if (agentOwner != player) {
            ActionCardHelper.drawActionCards(agentOwner, 1);
        }
        ExploreService.explorePlanet(event, tile, planet.getName(), trait, player, false, game, 1, true);
        ButtonHelper.deleteMessage(event);
    }

    public static void offerRevArdentiaAgentButtons(Game game, Player tokenOwner, Tile sourceTile) {
        if (game == null || tokenOwner == null || sourceTile == null) {
            return;
        }
        for (Player agentOwner : game.getRealPlayers()) {
            if (agentOwner == tokenOwner || !agentOwner.hasUnexhaustedLeader(REVARDENTIAAGENT)) {
                continue;
            }
            List<Button> buttons = List.of(
                    Buttons.green(
                            agentOwner.factionButtonChecker() + USE_REVARDENTIA_AGENT + tokenOwner.getFaction() + "|"
                                    + sourceTile.getPosition(),
                            "Move " + tokenOwner.getColor() + " Command Token",
                            FactionEmojis.revenant),
                    Buttons.red(agentOwner.factionButtonChecker() + "deleteButtons", "No Thanks"));
            MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                    agentOwner.getCardsInfoThread(),
                    agentOwner.getRepresentation() + ", " + tokenOwner.getRepresentationNoPing()
                            + " placed a command token in " + sourceTile.getRepresentationForButtons(game, agentOwner)
                            + ". You may exhaust **Kruth Torrious** to move it.",
                    buttons);
        }
    }

    @ButtonHandler(USE_REVARDENTIA_AGENT)
    public static void useRevArdentiaAgent(
            ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        String[] values = buttonID.substring(USE_REVARDENTIA_AGENT.length()).split("\\|", 2);
        Player tokenOwner = values.length == 2 && game != null ? game.getPlayerFromColorOrFaction(values[0]) : null;
        Tile sourceTile = values.length == 2 && game != null ? game.getTileByPosition(values[1]) : null;
        if (agentOwner == null
                || tokenOwner == null
                || sourceTile == null
                || tokenOwner == agentOwner
                || !agentOwner.hasUnexhaustedLeader(REVARDENTIAAGENT)
                || !sourceTile.hasCC(Mapper.getCCID(tokenOwner.getColor()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getRevArdentiaDestinationButtons(game, agentOwner, tokenOwner);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "There are no eligible systems for that command token.");
            return;
        }
        Leader agent = agentOwner.getLeader(REVARDENTIAAGENT).orElse(null);
        if (agent == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        ti4.service.RemoveCommandCounterService.fromTile(tokenOwner.getColor(), sourceTile, game);
        String prefix = agentOwner.factionButtonChecker() + MOVE_REVARDENTIA_TOKEN + tokenOwner.getFaction() + "|";
        List<Button> extraButtons =
                List.of(Buttons.red(agentOwner.factionButtonChecker() + "deleteButtons", "Decline"));
        List<Button> displayedButtons = new ArrayList<>(buttons);
        displayedButtons.addAll(extraButtons);
        if (displayedButtons.size() > 25) {
            displayedButtons = NewStuffHelper.buttonPagination(buttons, extraButtons, prefix, 25, 0, false);
        }
        MessageHelper.sendMessageToChannelWithButtons(
                agentOwner.getCorrectChannel(),
                agentOwner.getRepresentation() + ", choose where to move " + tokenOwner.getRepresentationNoPing()
                        + "'s command token with **Kruth Torrious**.",
                displayedButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(MOVE_REVARDENTIA_TOKEN)
    public static void moveRevArdentiaToken(
            ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        String[] values = buttonID.substring(MOVE_REVARDENTIA_TOKEN.length()).split("\\|", 2);
        Player tokenOwner = values.length == 2 && game != null ? game.getPlayerFromColorOrFaction(values[0]) : null;
        if (agentOwner == null || tokenOwner == null || !agentOwner.hasLeader(REVARDENTIAAGENT)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getRevArdentiaDestinationButtons(game, agentOwner, tokenOwner);
        String message = agentOwner.getRepresentation() + ", choose where to move "
                + tokenOwner.getRepresentationNoPing() + "'s command token with **Kruth Torrious**.";
        String prefix = agentOwner.factionButtonChecker() + MOVE_REVARDENTIA_TOKEN + tokenOwner.getFaction() + "|";
        List<Button> extraButtons =
                List.of(Buttons.red(agentOwner.factionButtonChecker() + "deleteButtons", "Decline"));
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, extraButtons, message, prefix, buttonID)) {
            return;
        }
        Tile destinationTile = values.length == 2 ? game.getTileByPosition(values[1]) : null;
        if (destinationTile == null
                || buttons.stream()
                        .noneMatch(button -> button.getCustomId().endsWith("|" + destinationTile.getPosition()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        destinationTile.addCC(Mapper.getCCID(tokenOwner.getColor()));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                agentOwner.getRepresentationNoPing() + " moved " + tokenOwner.getRepresentationNoPing()
                        + "'s command token to " + destinationTile.getRepresentationForButtons(game, agentOwner)
                        + " with **Kruth Torrious**.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getRevArdentiaDestinationButtons(Game game, Player agentOwner, Player tokenOwner) {
        if (game == null || agentOwner == null || tokenOwner == null) {
            return List.of();
        }
        String token = Mapper.getCCID(tokenOwner.getColor());
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.isHomeSystem(game))
                .filter(tile -> FoWHelper.playerHasUnitsInSystem(agentOwner, tile))
                .filter(tile -> tile.getPlanetUnitHolders().stream().noneMatch(Planet::isLegendary))
                .filter(tile -> !tile.hasCC(token))
                .map(tile -> Buttons.green(
                        agentOwner.factionButtonChecker() + MOVE_REVARDENTIA_TOKEN + tokenOwner.getFaction() + "|"
                                + tile.getPosition(),
                        "Move to " + tile.getRepresentationForButtons(game, agentOwner)))
                .toList();
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
                    opponent.factionButtonChecker() + "autoAssignGroundHits_" + holder.getName() + "_" + hits,
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
    // Revenant of Scrapyard
    public static Button getRevScrapyardAgentButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + PRODUCE_WITH_REVSCRAPYARD,
                "Use Revenant Scrapyard Agent",
                FactionEmojis.revenant);
    }

    public static Button getRevScrapyardCardsInfoButton(Game game, Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + SELECT_REVSCRAPYARD_TARGET,
                "Use Revenant Scrapyard Agent",
                FactionEmojis.revenant);
    }

    @ButtonHandler(SELECT_REVSCRAPYARD_TARGET)
    public static void offerRevScrapyardTargetButtons(
            ButtonInteractionEvent event, Game game, Player componentOwner, String buttonID) {
        if (!componentOwner.hasUnexhaustedLeader(REVSCRAPYARD)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Thrash \"Feral\" Burner, the Revenant of Scrapyard agent, is no longer available.");
            return;
        }

        List<Button> buttons = game.getRealPlayers().stream()
                .filter(target -> target != componentOwner) // Remove to include self
                .map(target -> Buttons.green(
                        componentOwner.factionButtonChecker() + RESOLVE_REVSCRAPYARD_TARGET + target.getFaction(),
                        target.getFactionNameOrColor(),
                        target.fogSafeEmoji()))
                .toList();

        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no eligible players.");
            return;
        }

        String message = componentOwner.getRepresentation() + ", choose the player who will produce 1 unit with "
                + "Thrash \"Feral\" Burner, the Revenant of Scrapyard agent.";
        String prefix = componentOwner.factionButtonChecker() + SELECT_REVSCRAPYARD_TARGET;
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

    @ButtonHandler(RESOLVE_REVSCRAPYARD_TARGET)
    public static void chooseRevenantTarget(
            ButtonInteractionEvent event, Game game, Player componentOwner, String buttonID) {

        String targetFaction = buttonID.substring(RESOLVE_REVSCRAPYARD_TARGET.length());
        Player target = game.getPlayerFromColorOrFaction(targetFaction);

        if (target == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find that player.");
            return;
        }

        if (useRevScrapyardAgent(event, game, componentOwner, target)) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler(PRODUCE_WITH_REVSCRAPYARD)
    public static void offerRevScrapyardChoices(ButtonInteractionEvent event, Game game, Player target) {
        if (useRevScrapyardAgent(event, game, target, target)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
    }

    private static boolean useRevScrapyardAgent(
            ButtonInteractionEvent event, Game game, Player agentOwner, Player target) {
        if (game == null || agentOwner == null || target == null || !agentOwner.hasUnexhaustedLeader(REVSCRAPYARD)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Thrash \"Feral\" Burner, the Revenant of Scrapyard agent, is no longer available.");
            return false;
        }
        List<Button> buttons = getProduceOneUnitInSystemsWithShipsButtons(game, target);

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentation() + " does not have any ships on the game board.");
            return false;
        }

        Leader agent = agentOwner.getLeader(REVSCRAPYARD).orElse(null);
        if (agent == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Could not find Thrash \"Feral\" Burner, the Revenant of Scrapyard agent.");
            return false;
        }
        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        sendRevScrapyardSystemButtons(null, game, target, "", buttons);
        return true;
    }

    @ButtonHandler(PAGE_REVSCRAPYARD_SYSTEMS)
    public static void pageRevScrapyardSystemButtons(
            ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String targetFaction =
                buttonID.substring(PAGE_REVSCRAPYARD_SYSTEMS.length()).split("_page", 2)[0];
        if (!target.getFaction().equals(targetFaction)) {
            return;
        }
        sendRevScrapyardSystemButtons(
                event, game, target, buttonID, getProduceOneUnitInSystemsWithShipsButtons(game, target));
    }

    private static void sendRevScrapyardSystemButtons(
            ButtonInteractionEvent event, Game game, Player target, String buttonID, List<Button> buttons) {
        String message = target.getRepresentation()
                + ", choose a system containing 1 or more of your ships in which to produce 1 unit due to Thrash \"Feral\" Burner, the Revenant of Scrapyard agent.";
        String prefix = target.factionButtonChecker() + PAGE_REVSCRAPYARD_SYSTEMS + target.getFaction() + "_";
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
                                + "_revenantScrapyard",
                        tile.getRepresentationForButtons(game, target)))
                .toList();
    }

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
        buttons.add(Buttons.red("deleteButtons", "Decline"));

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
        buttons.add(Buttons.red("deleteButtons", "Decline"));
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
        if (game.getStoredValue(REVXYTHERIS_WINDOW).startsWith(faction + "|")) {
            game.removeStoredValue(REVXYTHERIS_WINDOW);
        }
        game.removeStoredValue(REVXYTHERIS_TARGET + faction);
        game.removeStoredValue(REVKRYXOS_CONTEXT + faction);
        game.removeStoredValue(REVKRYXOS_FIRST_TECH + faction);
        game.removeStoredValue(REVPONTHOUS_USED + faction);
        game.removeStoredValue(REVMYRR_USED + faction);
        game.removeStoredValue(REVMYRR_PRODUCTION + faction);
        game.removeStoredValue(REVVERYDITH_PENDING + faction);
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

    private static boolean canUseRevXytherisAgent(Game game, Player agentOwner) {
        if (game == null || agentOwner == null || !agentOwner.hasUnexhaustedLeader(REVXYTHERIS)) {
            return false;
        }
        Player activePlayer = game.getActivePlayer();
        return activePlayer != null
                && (activePlayer.getFaction() + "|" + game.getActiveSystem())
                        .equals(game.getStoredValue(REVXYTHERIS_WINDOW));
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
        game.removeStoredValue(REVXYTHERIS_WINDOW);
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
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(REVXYTHERIS_TARGET))
                .toList()
                .forEach(game::removeStoredValue);
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
                        + ", you may exhaust Xythis the Whispering, the Revenant agent, to ready "
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
                    event, "Xythis the Whispering, the Revenant agent, is no longer available.");
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
                        + ", choose a player on whom to use Xythis the Whispering, the Revenant agent.",
                targets);
    }

    @ButtonHandler(USE_REVBASE_TARGET)
    public static void offerRevBaseTechButtons(
            ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        if (game == null || agentOwner == null || !agentOwner.hasUnexhaustedLeader(REVBASE)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Xythis the Whispering, the Revenant agent, is no longer available.");
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
                        + ", choose the technology that Xythis the Whispering, the Revenant agent, should ready.",
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
                    event, "Xythis the Whispering, the Revenant agent, is no longer available.");
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
                    event, "Could not find Xythis the Whispering, the Revenant agent.");
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        player.refreshTech(techId);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " readied " + techM.getNameRepresentation()
                        + " using Xythis the Whispering, the Revenant agent.");

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
    }

    // Green Revenant Leader Set
    // Revenant of Arcanum
    public static void offerRevArcanumAgentButtons(Game game, Player passedPlayer) {
        if (game == null || passedPlayer == null) {
            return;
        }
        for (Player agentOwner : game.getRealPlayers()) {
            if (!agentOwner.hasUnexhaustedLeader(REVARCAGENT)
                    || getRevArcanumReturnTechButtons(game, agentOwner, passedPlayer)
                            .isEmpty()) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                    agentOwner.getCardsInfoThread(),
                    agentOwner.getRepresentationNoPing() + ", " + passedPlayer.getRepresentationNoPing()
                            + " passed. You may exhaust **Lothos Yvollus** to let them return a technology and research one with exactly 1 fewer prerequisite.",
                    List.of(
                            Buttons.green(
                                    agentOwner.factionButtonChecker() + USE_REVARCAGENT + passedPlayer.getFaction(),
                                    "Use Revenant Arcanum Agent",
                                    FactionEmojis.revenant),
                            Buttons.red(agentOwner.factionButtonChecker() + "deleteButtons", "No Thanks")));
        }
    }

    @ButtonHandler(USE_REVARCAGENT)
    public static void useRevArcanumAgent(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        Player target =
                game == null ? null : game.getPlayerFromColorOrFaction(buttonID.substring(USE_REVARCAGENT.length()));
        Leader agent =
                agentOwner == null ? null : agentOwner.getLeader(REVARCAGENT).orElse(null);
        List<Button> buttons = getRevArcanumReturnTechButtons(game, agentOwner, target);
        if (agent == null || !agentOwner.hasUnexhaustedLeader(REVARCAGENT) || buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentation()
                        + ", choose a non-faction, non-unit technology to return to its deck with **Lothos Yvollus**.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_REVARCAGENT_TECH)
    public static void selectRevArcanumAgentTech(
            ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String techId = buttonID.substring(SELECT_REVARCAGENT_TECH.length());
        TechnologyModel returnedTech = Mapper.getTech(techId);
        if (target == null
                || returnedTech == null
                || !target.hasTech(techId)
                || returnedTech.isFactionTech()
                || returnedTech.isUnitUpgrade()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        target.removeTech(techId);
        List<TechnologyModel> researchableTechs = getRevArcanumResearchableTechs(game, target, returnedTech);
        if (researchableTechs.isEmpty()) {
            target.addTech(techId);
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No researchable technology has exactly 1 fewer prerequisite.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentation() + " returned " + returnedTech.getNameRepresentation()
                        + " to the technology deck. Research a technology with exactly 1 fewer prerequisite.",
                ListTechService.getTechButtons(new ArrayList<>(researchableTechs), target));
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getRevArcanumReturnTechButtons(Game game, Player agentOwner, Player target) {
        if (game == null || agentOwner == null || target == null) {
            return List.of();
        }
        return target.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(tech -> !tech.isFactionTech() && !tech.isUnitUpgrade())
                .filter(tech ->
                        !getRevArcanumResearchableTechs(game, target, tech).isEmpty())
                .map(tech -> Buttons.green(
                        target.factionButtonChecker() + SELECT_REVARCAGENT_TECH + tech.getAlias(),
                        "Return " + tech.getName(),
                        tech.getCondensedReqsEmojis(true)))
                .toList();
    }

    private static List<TechnologyModel> getRevArcanumResearchableTechs(
            Game game, Player target, TechnologyModel returnedTech) {
        if (game == null || target == null || returnedTech == null) {
            return List.of();
        }
        int prerequisites = returnedTech.getRequirements().orElse("").length() - 1;
        if (prerequisites < 0) {
            return List.of();
        }
        return Mapper.getTechs().values().stream()
                .filter(tech -> game.getTechnologyDeck().contains(tech.getAlias()))
                .filter(tech -> !target.hasTech(tech.getAlias()))
                .filter(tech -> !target.getPurgedTechs().contains(tech.getAlias()))
                .filter(tech -> tech.getFaction().isEmpty()
                        || tech.getFaction().get().isBlank()
                        || target.getNotResearchedFactionTechs().contains(tech.getAlias()))
                .filter(tech -> tech.getRequirements().orElse("").length() == prerequisites)
                .filter(tech -> ListTechService.isTechResearchable(tech, target))
                .toList();
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
            placeLichToken(event, game, player, target, commanders.get(0));
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
