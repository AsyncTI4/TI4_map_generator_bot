package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.discord.interactions.commands.planet.PlanetExhaustAbility;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.PlanetModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.SelfOrTargetedAgent;

public final class FreeSystemsAgent extends SelfOrTargetedAgent {

    static final String ID = "freesystemsagent";
    private static final String ABILITY_STEP = "freeSystemsAgentStep2_";

    public FreeSystemsAgent() {
        super(ID, "Cordo Haved, the Free Systems");
    }

    public static Optional<Button> button(Game game, Player freeSystems) {
        if (freeSystems.getReadiedPlanets().isEmpty()
                || legendaryAbilities(game).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Buttons.green(
                ownedButtonId(freeSystems, ID, freeSystems.getFaction()),
                useLabel(freeSystems, ID, "Free Systems Agent"),
                FactionEmojis.freesystems));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (legendaryAbilities(game).isEmpty()) {
            return Optional.of("No player controls a legendary planet");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        List<Button> abilities = legendaryAbilities(use.game()).stream()
                .map(planet -> Buttons.green(
                        ownedStepId(target, ABILITY_STEP + planet, use.viaYssaril()),
                        Helper.getPlanetRepresentation(planet, use.game())))
                .toList();
        return AgentOutcome.of(Message.addressedTo(
                target, ", please choose the legendary planet ability that you wish to use.", abilities));
    }

    @ButtonHandler(ABILITY_STEP)
    static void useAbility(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        AgentStepIds.Parsed step = AgentStepIds.parse(buttonID);
        String planet = StringUtils.substringAfter(step.id(), ABILITY_STEP);
        PlanetExhaustAbility.doAction(event, player, planet, game, false);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " chose to replicate the "
                        + Helper.getPlanetRepresentation(planet, game) + " legendary ability with "
                        + AgentLifecycle.agentNameShownTo(
                                game, player, player.hasLeader(ID) ? player : null, ID, step.viaYssaril())
                        + ".");
        ButtonHelper.deleteMessage(event);
    }

    static List<String> legendaryAbilities(Game game) {
        return game.getRealPlayers().stream()
                .flatMap(player -> player.getPlanets().stream())
                .filter(planet -> !planet.contains("custodia") && !planet.contains("ghoti"))
                .filter(FreeSystemsAgent::hasLegendaryAbility)
                .toList();
    }

    private static boolean hasLegendaryAbility(String planet) {
        PlanetModel model = Mapper.getPlanet(planet);
        return model != null && StringUtils.isNotEmpty(model.getLegendaryAbilityText());
    }
}
