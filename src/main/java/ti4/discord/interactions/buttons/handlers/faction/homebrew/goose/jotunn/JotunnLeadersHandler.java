package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.jotunn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.tuple.Pair;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class JotunnLeadersHandler {
    private static final String AGENT = "jotunnagent";
    private static final String COMMANDER = "jotunncommander";
    private static final String HERO = "jotunnhero";
    private static final String USE_AGENT_FOR_EXPLORE = "useJotunnAgentForExplore_";
    private static final String USE_AGENT_ON_OTHER = "useJotunnAgentOnOther";
    private static final String SELECT_AGENT_TARGET = "selectJotunnAgentTarget_";
    private static final String SELECT_AGENT_PLANET = "selectJotunnAgentPlanet_";
    private static final String PLACE_AGENT_STRUCTURE = "placeJotunnAgentStructure_";

    public static void addExploreButton(List<Button> buttons, Player player, Tile tile, String cardId) {
        if (!player.hasUnexhaustedLeader(AGENT)) {
            return;
        }
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + USE_AGENT_FOR_EXPLORE + cardId + "|" + tile.getPosition(),
                "Use Jotunn Agent",
                FactionEmojis.jotunn));
    }

    public static Button cardsInfoAgentButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_AGENT_ON_OTHER, "Use Jotunn Agent", FactionEmojis.jotunn);
    }

    @ButtonHandler(USE_AGENT_FOR_EXPLORE)
    public static void useAgentForExplore(ButtonInteractionEvent event, Game game, Player player, String buttonId) {
        String[] payload = buttonId.substring(USE_AGENT_FOR_EXPLORE.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[1]) : null;
        if (tile == null || !player.hasUnexhaustedLeader(AGENT)) {
            return;
        }
        if (!AgentLifecycle.use(game, player, AGENT, "explore", event)) {
            return;
        }
        game.discardExplore(payload[0]);
        sendPlanetChoices(game, player, tile, event);
    }

    @ButtonHandler(USE_AGENT_ON_OTHER)
    public static void useAgentOnOther(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(AGENT)) {
            return;
        }
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .map(target -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_AGENT_TARGET + target.getFaction(),
                        target.getFactionNameOrColor(),
                        target.fogSafeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose a player for Örnsteinn, the Jotunn agent.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_AGENT_TARGET)
    public static void selectAgentTarget(ButtonInteractionEvent event, Game game, Player player, String buttonId) {
        if (!player.hasUnexhaustedLeader(AGENT)) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(buttonId.substring(SELECT_AGENT_TARGET.length()));
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (target == null || activeSystem == null) {
            return;
        }
        if (!AgentLifecycle.use(game, player, AGENT, target.getFaction(), event)) {
            return;
        }
        sendPlanetChoices(game, target, activeSystem, event);
    }

    @ButtonHandler(SELECT_AGENT_PLANET)
    public static void selectAgentPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonId) {
        String[] payload = buttonId.substring(SELECT_AGENT_PLANET.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Planet planet = tile == null
                ? null
                : tile.getPlanetUnitHolders().stream()
                        .filter(candidate -> candidate.getName().equals(payload[1]))
                        .filter(candidate -> player.getPlanets().contains(candidate.getName()))
                        .findFirst()
                        .orElse(null);
        if (planet == null) {
            return;
        }
        List<Button> buttons = List.of(
                Buttons.gray(
                        player.factionButtonChecker() + PLACE_AGENT_STRUCTURE + tile.getPosition() + "|"
                                + planet.getName() + "|pds",
                        "Place 1 PDS",
                        FactionEmojis.jotunn),
                Buttons.gray(
                        player.factionButtonChecker() + PLACE_AGENT_STRUCTURE + tile.getPosition() + "|"
                                + planet.getName() + "|sd",
                        "Place 1 Space Dock",
                        FactionEmojis.jotunn));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a structure to place on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_AGENT_STRUCTURE)
    public static void placeAgentStructure(ButtonInteractionEvent event, Game game, Player player, String buttonId) {
        String[] payload = buttonId.substring(PLACE_AGENT_STRUCTURE.length()).split("\\|", 3);
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        Planet planet = tile == null
                ? null
                : tile.getPlanetUnitHolders().stream()
                        .filter(candidate -> candidate.getName().equals(payload[1]))
                        .filter(candidate -> player.getPlanets().contains(candidate.getName()))
                        .findFirst()
                        .orElse(null);
        if (planet == null || !("pds".equals(payload[2]) || "sd".equals(payload[2]))) {
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + payload[2] + " " + planet.getName());
        CommanderUnlockCheckService.checkPlayer(player, "jotunn");
        String structure = "pds".equals(payload[2]) ? "PDS" : "space dock";
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed 1 " + structure + " on "
                        + Helper.getPlanetRepresentation(planet.getName(), game)
                        + " using Örnsteinn, the Jotunn agent.");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveCommander(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Player target,
            Tile tile,
            int hits,
            Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        if (!game.playerHasLeaderUnlockedOrAlliance(player, COMMANDER) || target == null || !usesPds(player, units)) {
            return;
        }
        int excessHits = hits - ButtonHelper.checkNumberNonFighterShips(target, tile, false);
        if (excessHits <= 0) {
            return;
        }
        ButtonHelperStats.gainComms(event, game, player, excessHits, false);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "This was due to the _Jotunn_ commander.");
    }

    public static boolean hasDeepFrostCoverage(Game game, Player player) {
        return hasActiveHero(player)
                && game.getTileMap().values().stream()
                        .flatMap(tile -> tile.getUnitHolders().values().stream())
                        .flatMap(unitHolder -> unitHolder.getUnitKeysForPlayer(player).stream())
                        .map(player::getUnitFromUnitKey)
                        .anyMatch(unit -> unit != null && unit.getSpaceCannonDieCount(player) > 0);
    }

    public static void addDeepFrostCannons(Game game, Player player, Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        if (!hasActiveHero(player)) {
            return;
        }
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : unitHolder.getUnitKeysForPlayer(player)) {
                    UnitModel unit = player.getUnitFromUnitKey(unitKey);
                    if (unit != null && unit.getSpaceCannonDieCount(player) > 0) {
                        units.putIfAbsent(Pair.of(unit, unitHolder), unitHolder.getUnitCount(unitKey));
                    }
                }
            }
        }
    }

    private static void sendPlanetChoices(Game game, Player target, Tile tile, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (target.getPlanets().contains(planet.getName())) {
                buttons.add(Buttons.gray(
                        target.factionButtonChecker() + SELECT_AGENT_PLANET + tile.getPosition() + "|"
                                + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game),
                        FactionEmojis.jotunn));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentationNoPing() + " has no controlled planets in that system.");
        } else {
            MessageHelper.sendMessageToChannelWithButtons(
                    target.getCorrectChannel(),
                    target.getRepresentationNoPing() + ", choose a planet for Örnsteinn, the Jotunn agent.",
                    buttons);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static boolean usesPds(Player player, Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        return units.keySet().stream()
                .anyMatch(entry -> entry.getLeft().getUnitType() == UnitType.Pds
                        && entry.getRight().getUnitCount(UnitType.Pds, player) > 0);
    }

    private static boolean hasActiveHero(Player player) {
        return player.getLeader(HERO).map(Leader::isActive).orElse(false);
    }
}
