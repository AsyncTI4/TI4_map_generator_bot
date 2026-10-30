package ti4.service.leader.agent.modules;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentPrompts;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.PlayerPickerAgent;

public final class NekroAgent extends PlayerPickerAgent {

    static final String ID = "nekroagent";
    private static final int TRADE_GOODS_GAINED = 2;

    public NekroAgent() {
        super(ID, "Nekro Malleon, the Nekro");
    }

    public static Button button(Player nekro) {
        return useButton(nekro, ID, "Nekro Agent", FactionEmojis.Nekro);
    }

    @Override
    protected AgentOutcome resolveFor(AgentUse<Player> use) {
        Game game = use.game();
        Player target = use.payload();
        String tradeGoods = target.gainTG(TRADE_GOODS_GAINED);
        ButtonHelperAbilities.pillageCheck(target, game);
        ButtonHelperAgents.resolveArtunoCheck(target, TRADE_GOODS_GAINED);
        return AgentOutcome.of(
                pickNote(use),
                Message.addressedTo(
                        target,
                        ", your trade goods increased by " + TRADE_GOODS_GAINED + " " + tradeGoods
                                + ". Use buttons in your `#cards-info` thread to discard 1 action card,"
                                + " or below to remove 1 command token."),
                Message.addressedTo(
                                target,
                                ", please choose the action card to discard.",
                                ActionCardHelper.getDiscardActionCardButtons(target, false))
                        .inCardsInfoThread(),
                AgentPrompts.loseCommandTokens(game, target));
    }
}
