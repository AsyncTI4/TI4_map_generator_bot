package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;

public final class RevenantStratumAgent extends AgentModule<RevenantStratumAgent.Exploration> {

    static final String ID = "revenantstonebornagent";
    private static final String USE_ON_OTHER = "useRevenantStratumAgentOther";
    private static final String SELECT_TARGET = "selectRevenantStratumTarget_";
    private static final String SELECT_PLANET = "selectRevenantStratumPlanet_";
    private static final String EXPLORE = "exploreRevenantStratum_";
    private static final String SEPARATOR = "|";
    private static final Set<String> EXPLORATION_TRAITS =
            Set.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL);

    public record Exploration(Player explorer, Planet planet, Tile system, String trait) {}

    public RevenantStratumAgent() {
        super(ID, "Necrolith, the Revenant of Stratum");
    }

    public static Button cardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_ON_OTHER, "Use Revenant Stratum Agent", FactionEmojis.revenant);
    }

    public static void addExploreButtons(List<Button> buttons, Game game, Player player, Planet planet) {
        if (buttons == null
                || game == null
                || player == null
                || planet == null
                || !player.hasUnexhaustedLeader(ID)
                || !player.getPlanetsAllianceMode().contains(planet.getName())) {
            return;
        }
        for (String trait : EXPLORATION_TRAITS) {
            if (!planet.getPlanetTypes().contains(trait)) {
                buttons.add(Buttons.gray(
                        ownedButtonId(player, ID, planet.getName() + SEPARATOR + trait),
                        "Explore as " + StringUtils.capitalize(trait),
                        FactionEmojis.revenant));
            }
        }
    }

    @ButtonHandler(USE_ON_OTHER)
    static void offerTargets(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(ID)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .filter(target -> !target.getPlanetsAllianceMode().isEmpty())
                .map(target -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_TARGET + target.getFaction(),
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

    @ButtonHandler(SELECT_TARGET)
    static void offerPlanets(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_TARGET.length()));
        if (target == null
                || target == agentOwner
                || !agentOwner.hasUnexhaustedLeader(ID)
                || target.getPlanetsAllianceMode().isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = target.getPlanetsAllianceMode().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(Objects::nonNull)
                .map(planet -> Buttons.gray(
                        target.factionButtonChecker()
                                + SELECT_PLANET
                                + agentOwner.getFaction()
                                + SEPARATOR
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

    @ButtonHandler(SELECT_PLANET)
    static void offerTraits(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] values = buttonID.substring(SELECT_PLANET.length()).split("\\|", 2);
        Player agentOwner = values.length == 2 ? game.getPlayerFromColorOrFaction(values[0]) : null;
        Planet planet = values.length == 2 ? game.getUnitHolderFromPlanet(values[1]) : null;
        if (agentOwner == null
                || planet == null
                || !agentOwner.hasUnexhaustedLeader(ID)
                || !target.getPlanetsAllianceMode().contains(planet.getName())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentation() + ", choose an exploration trait for " + planet.getRepresentation(game)
                        + ".",
                traitButtons(agentOwner, target, planet));
        ButtonHelper.deleteMessage(event);
    }

    static List<Button> traitButtons(Player agentOwner, Player target, Planet planet) {
        return EXPLORATION_TRAITS.stream()
                .map(trait -> Buttons.gray(
                        target.factionButtonChecker()
                                + EXPLORE
                                + agentOwner.getFaction()
                                + SEPARATOR
                                + planet.getName()
                                + SEPARATOR
                                + trait,
                        "Explore as " + StringUtils.capitalize(trait),
                        ExploreEmojis.getTraitEmoji(trait)))
                .toList();
    }

    @ButtonHandler(EXPLORE)
    static void explore(ButtonInteractionEvent event, Game game, Player presser, String buttonID) {
        String rest = buttonID.substring(EXPLORE.length());
        if (StringUtils.countMatches(rest, SEPARATOR) < 2) {
            // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
            AgentLifecycle.pressLegacyButton(event, game, presser, ID, rest);
            return;
        }
        Player agentOwner = game.getPlayerFromColorOrFaction(StringUtils.substringBefore(rest, SEPARATOR));
        if (agentOwner == null) {
            return;
        }
        String exploration = presser.getFaction() + SEPARATOR + StringUtils.substringAfter(rest, SEPARATOR);
        if (AgentLifecycle.use(game, agentOwner, ID, exploration, event)) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @Override
    public Optional<Exploration> decode(Game game, Player user, String payload) {
        String[] parts = StringUtils.split(payload, SEPARATOR);
        if (parts == null || parts.length < 2 || parts.length > 3) {
            return Optional.empty();
        }
        Player explorer = parts.length == 3 ? game.getPlayerFromColorOrFaction(parts[0]) : user;
        String planetName = parts[parts.length - 2];
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile system = game.getTileFromPlanet(planetName);
        if (explorer == null || planet == null || system == null) {
            return Optional.empty();
        }
        return Optional.of(new Exploration(explorer, planet, system, parts[parts.length - 1]));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Exploration exploration) {
        if (!EXPLORATION_TRAITS.contains(exploration.trait())) {
            return Optional.of("That is not an exploration trait");
        }
        if (!exploration
                .explorer()
                .getPlanetsAllianceMode()
                .contains(exploration.planet().getName())) {
            return Optional.of("That planet is no longer controlled by the exploring player");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Exploration> use) {
        Exploration exploration = use.payload();
        if (exploration.explorer() != use.user()) {
            ActionCardHelper.drawActionCards(use.user(), 1);
        }
        ExploreService.explorePlanet(
                use.event(),
                exploration.system(),
                exploration.planet().getName(),
                exploration.trait(),
                exploration.explorer(),
                false,
                use.game(),
                1,
                true);
        return AgentOutcome.none().deletingPressedMessage();
    }
}
