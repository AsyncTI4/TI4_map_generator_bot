package ti4.service.leader.agent.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentTargets.PlanetInSystem;
import ti4.service.leader.agent.AgentUse;

public final class MercerAgent extends AgentModule<String> {

    static final String ID = "nomadagentmercer";

    public MercerAgent() {
        super(ID, "Field Marshal Mercer, a Nomad");
    }

    public static List<Button> buttons(Game game, Player mercer) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null) {
            return List.of();
        }
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : activeSystem.getPlanetUnitHolders()) {
            String planetName = planet.getName();
            if (mercer.getPlanetsAllianceMode().contains(planetName)) {
                buttons.add(Buttons.green(
                        ownedButtonId(mercer, ID, activeSystem.getPosition(), planetName),
                        useLabel(
                                mercer,
                                ID,
                                cardLabel(mercer, ID, "Mercer") + " on "
                                        + Helper.getPlanetRepresentation(planetName, game)),
                        FactionEmojis.Nomad));
            }
        }
        return buttons;
    }

    @Override
    public Optional<String> decode(Game game, Player user, String payload) {
        return AgentTargets.planetInSystem(game, payload).map(PlanetInSystem::planet);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, String planet) {
        if (ButtonHelper.groundForceMoveButtons(game, planet, user).isEmpty()) {
            return Optional.of(
                    "You have no ground forces that can move to " + Helper.getPlanetRepresentation(planet, game));
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<String> use) {
        Player user = use.user();
        String planet = use.payload();
        return AgentOutcome.of(Message.addressedTo(
                user,
                ", you may move up to 2 ground forces to " + Helper.getPlanetRepresentation(planet, use.game())
                        + " with " + use.agentName() + ".",
                ButtonHelper.getButtonsForMovingGroundForcesToAPlanet(use.game(), planet, user)));
    }
}
