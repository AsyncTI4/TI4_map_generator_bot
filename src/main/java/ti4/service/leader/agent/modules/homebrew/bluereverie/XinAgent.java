package ti4.service.leader.agent.modules.homebrew.bluereverie;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperHeroes;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.PlayerPickerAgent;

public final class XinAgent extends PlayerPickerAgent {

    static final String ID = "xinagent";
    private static final String LEGACY_SELECT_PLAYER = "selectXinAgentPlayer_";

    public XinAgent() {
        super(ID, "Liu, the Xin");
    }

    public static Button button(Player holder) {
        return useButton(holder, ID, "Xin Agent", FactionEmojis.xin);
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler("useXinAgent")
    static void legacyUse(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, "");
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_SELECT_PLAYER)
    static void legacySelectPlayer(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_SELECT_PLAYER.length()));
    }

    @Override
    protected AgentOutcome resolveFor(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", " + use.agentNameFor(target) + " allows you to follow an exhausted strategy card."
                        + " You must still spend 1 command token from your strategy pool to do so.",
                ButtonHelperHeroes.getSecondaryButtons(use.game())));
    }
}
