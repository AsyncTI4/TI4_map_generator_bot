package ti4.service.leader.genome.homebrew.tknova;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.modules.ArtunoAgent;
import ti4.service.leader.genome.LeaningGenome;

public final class InvestmentGenome extends LeaningGenome<Integer> {

    static final String ID = "tknovainvestmentagent";

    public InvestmentGenome() {
        super(ID, "Investment Genome", new ArtunoAgent());
    }

    public static void offerTradeGoodsGained(Game game, Player gainer, int tradeGoods) {
        for (Player holder : game.getRealPlayers()) {
            if (!holder.hasUnexhaustedLeader(ID) || (game.isFowMode() && holder != gainer)) {
                continue;
            }
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green(
                    ownedButtonId(holder, ID, String.valueOf(tradeGoods)),
                    "Exhaust Investment Genome With " + StringHelper.pluralize(tradeGoods, "TG")));
            buttons.add(Buttons.red(holder.factionButtonChecker() + "deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    holder.getCorrectChannel(),
                    holder.getRepresentationUnfogged() + " you have the opportunity to exhaust "
                            + AgentLifecycle.agentNameForHolder(game, holder, ID) + ", and place "
                            + StringHelper.pluralize(tradeGoods, "trade good") + " on it, as "
                            + gainer.getFactionEmojiOrColor() + " gained them from the supply.",
                    buttons);
        }
    }

    @Override
    public AgentOutcome resolve(AgentUse<Integer> use) {
        return ArtunoAgent.placeTradeGoodsOnCard(use, ID);
    }
}
