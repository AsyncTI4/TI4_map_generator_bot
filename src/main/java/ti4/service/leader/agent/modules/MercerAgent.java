package ti4.service.leader.agent.modules;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;

public final class MercerAgent extends AgentModule<String> {

    static final String ID = "nomadagentmercer";
    private static final String MOVE_STEP = "mercerMove_";

    public MercerAgent() {
        super(ID, "Field Marshal Mercer, a Nomad");
    }

    public static Button button(Player mercer, String position, String planet, String planetRepresentation) {
        return Buttons.green(
                ownedButtonId(mercer, ID, position, planet),
                useLabel(mercer, ID, "Mercer on " + planetRepresentation),
                FactionEmojis.Nomad);
    }

    @Override
    public Optional<String> decode(Game game, Player user, String payload) {
        String planet = StringUtils.substringAfter(payload, "_");
        return game.getTileFromPlanet(planet) == null ? Optional.empty() : Optional.of(planet);
    }

    @Override
    public AgentOutcome resolve(AgentUse<String> use) {
        Player user = use.user();
        String planet = use.payload();
        List<Button> moves = ButtonHelper.getButtonsForMovingGroundForcesToAPlanet(use.game(), planet, user);
        if (moves.stream().noneMatch(button -> button.getCustomId().startsWith(MOVE_STEP))) {
            return AgentOutcome.of(use.noteToUser(user, " has no ground forces to move with " + use.agentName() + "."));
        }
        return AgentOutcome.of(Message.addressedTo(
                user,
                ", use buttons to move ground forces to " + Helper.getPlanetRepresentation(planet, use.game())
                        + " with " + use.agentName() + ".",
                moves));
    }
}
