package ti4.service.leader.agent.modules.homebrew;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class MortheusAgent extends TargetedAgent {

    static final String ID = "mortheusagent";

    public MortheusAgent() {
        super(ID, "Walik, the Mortheus");
    }

    public static Button button(Player mortheus) {
        return Buttons.red(
                ownedButtonId(mortheus, ID, mortheus.getFaction()),
                useLabel(mortheus, ID, "Mortheus Agent"),
                FactionEmojis.mortheus);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.offer(
                Message.addressedTo(
                        target,
                        ", please choose which system the ship you just produced is in.\nYou need to tell the bot"
                                + " which system the unit was produced in first, after which it will give tiles to"
                                + " move it to.",
                        ButtonHelper.getDomnaStepOneTiles(target, use.game())),
                use.noteToUser(target, " has no ships to move."));
    }
}
