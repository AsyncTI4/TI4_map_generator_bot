package ti4.service.leader.agent.modules.homebrew;

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
import ti4.service.leader.agent.AgentUse;

public final class KaloraAgent extends AgentModule<Player> {

    static final String ID = "kaloraagent";

    public KaloraAgent() {
        super(ID, "Valzor, the Kalora");
    }

    public static Button button(Player kalora) {
        return Buttons.gray(
                ownedButtonId(kalora, ID), useLabel(kalora, ID, "Valzor, the Kalora Agent"), FactionEmojis.kalora);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return Optional.of(user);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player user = use.user();
        return AgentOutcome.of(Message.withButtons(
                user,
                user.getRepresentationUnfogged() + ", please use the buttons to gain 1 command token.",
                ButtonHelper.getGainCCButtons(user)));
    }
}
