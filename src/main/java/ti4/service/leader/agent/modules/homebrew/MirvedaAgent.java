package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperCommanders;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class MirvedaAgent extends TargetedAgent {

    static final String ID = "mirvedaagent";

    public MirvedaAgent() {
        super(ID, "Logic Machina, the Mirveda");
    }

    public static Button button(Player mirveda) {
        return Buttons.gray(
                ownedButtonId(mirveda, ID, mirveda.getFaction()),
                useLabel(mirveda, ID, "Mirveda Agent"),
                FactionEmojis.mirveda);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (target.getStrategicCC() < 1) {
            return Optional.of(target.getFactionEmojiOrColor() + " has no command tokens in their strategy pool");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        target.setStrategicCC(target.getStrategicCC() - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(
                target, use.game(), use.event(), "used " + FactionEmojis.mirveda + " Logic Machina");
        return AgentOutcome.of(
                Message.addressedTo(
                        target,
                        ", 1 command token has been removed from your strategy pool due to use of "
                                + use.agentNameFor(target) + ". You may add it back if you didn't agree to the agent."),
                Message.addressedTo(
                        target,
                        ", please research a technology of a color which matches one of the prerequisites on the unit upgrade you just gained.",
                        List.of(Buttons.GET_A_TECH, Buttons.DELETE_BUTTONS)));
    }
}
