package ti4.service.leader.agent.modules.homebrew;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.UntargetedAgent;

public final class LiZhoAgent extends UntargetedAgent {

    static final String ID = "lizhoagent";

    public LiZhoAgent() {
        super(ID, "Vasra Ivo, the Li-Zho");
    }

    public static Button button(Player liZho) {
        return Buttons.green(ownedButtonId(liZho, ID), useLabel(liZho, ID, "Li-Zho Agent"), FactionEmojis.lizho);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player liZho = use.user();
        return AgentOutcome.offer(
                Message.addressedTo(
                        liZho,
                        ", please choose the system containing your ships where you wish to place your fighters.",
                        Helper.getTileWithShipsPlaceUnitButtons(liZho, use.game(), "2ff", "placeOneNDone_skipbuild")),
                Message.addressedTo(liZho, " has no system containing their ships."));
    }
}
