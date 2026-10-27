package ti4.service.leader.agent.modules;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class NaazAgent extends AgentModule<Player> {

    static final String ID = "naazagent";

    public NaazAgent() {
        super(ID, "Garv and Gunn, the Naaz-Rokha");
    }

    public static Button button(Player naaz) {
        return Buttons.green(
                ownedButtonId(naaz, ID, naaz.getFaction()),
                useLabel(naaz, ID, "Naaz-Rokha Agents"),
                FactionEmojis.Naaz);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.playerOrUser(game, user, payload);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player explorer = use.payload();
        return AgentOutcome.of(Message.withButtons(
                explorer,
                explorer.getRepresentationUnfogged() + ", please choose which planet you wish to explore.",
                ButtonHelper.getButtonsToExploreAllPlanets(explorer, use.game())));
    }
}
