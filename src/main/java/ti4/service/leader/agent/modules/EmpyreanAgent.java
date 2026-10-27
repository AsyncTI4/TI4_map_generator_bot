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

public final class EmpyreanAgent extends AgentModule<Player> {

    static final String ID = "empyreanagent";
    static final String ORIGINAL_TOKENS_KEY_PREFIX = "originalCCsFor";

    public EmpyreanAgent() {
        super(ID, "Acamar, the Empyrean");
    }

    public static Button button(Player empyrean) {
        return Buttons.gray(
                ownedButtonId(empyrean, ID), useLabel(empyrean, ID, "Empyrean Agent"), FactionEmojis.Empyrean);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.playerOrUser(game, user, payload);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        use.game().setStoredValue(ORIGINAL_TOKENS_KEY_PREFIX + target.getFaction(), target.getCCRepresentation());
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", your current command tokens are " + target.getCCRepresentation()
                        + ". Use buttons to gain command tokens.",
                ButtonHelper.getGainCCButtons(target)));
    }
}
