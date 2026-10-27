package ti4.service.leader.agent.modules.homebrew;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.helpers.ButtonHelperAbilities;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.UntargetedAgent;

public final class MykoMentoriAgent extends UntargetedAgent {

    static final String ID = "mykomentoriagent";
    private static final String OMEN_DIE_FROM_AGENT = "yes";

    public MykoMentoriAgent() {
        super(ID, "Lactarius Indigo, the Myko-Mentori");
    }

    public static Button button(Player myko) {
        return Buttons.gray(
                ownedButtonId(myko, ID), useLabel(myko, ID, "Myko-Mentori Agent"), FactionEmojis.mykomentori);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player myko = use.user();
        return AgentOutcome.of(Message.addressedTo(
                myko,
                ", you may play an Omen die with the following buttons. Duplicate dice are not shown.",
                ButtonHelperAbilities.omenDiceButtons(use.game(), myko, OMEN_DIE_FROM_AGENT)));
    }
}
