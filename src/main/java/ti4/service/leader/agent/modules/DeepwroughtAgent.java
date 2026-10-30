package ti4.service.leader.agent.modules;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class DeepwroughtAgent extends TargetedAgent {

    static final String ID = "deepwroughtagent";
    private static final String COEXIST_PREFIX = "exchangeProgramPart3";

    public DeepwroughtAgent() {
        super(ID, "Doctor Carrina, the Deepwrought");
    }

    @Override
    public String exhaustAnnouncementAfterName(AgentUse<Player> use) {
        return " has exhausted " + use.agentName() + ", to allow "
                + use.payload().getRepresentationNoPing() + " to ignore 1 prerequisite.";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player researcher = use.payload();
        return AgentOutcome.offer(
                        Message.addressedTo(
                                use.user(),
                                ", after " + use.payload().getRepresentationNoPing()
                                        + " ignores a prerequisite, please choose the planet of theirs that you will coexist on.",
                                coexistButtons(use.game(), use.user(), researcher)),
                        Message.addressedTo(use.user(), ", there is no non-home planet of theirs to coexist on."))
                .and(Message.addressedTo(
                        researcher,
                        ", you may ignore 1 prerequisite on the technology you are researching, due to "
                                + use.agentNameFor(researcher) + "."));
    }

    private static List<Button> coexistButtons(Game game, Player deepwrought, Player researcher) {
        String prefix = deepwrought.factionButtonChecker() + COEXIST_PREFIX;
        if (game.isFowMode()) {
            return PlanetTargetService.targetButtons(
                    game,
                    deepwrought,
                    PlanetTargetSpec.of(prefix).where(planet -> !planet.isHomePlanet(game)),
                    new ArrayList<>());
        }
        return researcher.getPlanets().stream()
                .filter(planet -> isNonHomePlanetWithUnitsOf(game, researcher, planet))
                .map(planet -> Buttons.gray(prefix + "_" + planet, Helper.getPlanetRepresentation(planet, game)))
                .toList();
    }

    private static boolean isNonHomePlanetWithUnitsOf(Game game, Player researcher, String planet) {
        Planet holder = game.getUnitHolderFromPlanet(planet);
        return holder != null && !holder.isHomePlanet(game) && FoWHelper.playerHasUnitsOnPlanet(researcher, holder);
    }
}
