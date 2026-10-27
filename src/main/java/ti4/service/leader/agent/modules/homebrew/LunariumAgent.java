package ti4.service.leader.agent.modules.homebrew;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;

public final class LunariumAgent extends AgentModule<Player> {

    static final String ID = "lunariumagent";

    public LunariumAgent() {
        super(ID, "Gu'la Ma, the Lunarium");
    }

    public static Button button(Player lunarium) {
        return Buttons.red(
                ownedButtonId(lunarium, ID), useLabel(lunarium, ID, "Lunarium Agent"), FactionEmojis.lunarium);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return Optional.of(user);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player user = use.user();
        user.addSpentThing(ID);
        return AgentOutcome.none().withPressedMessageEdit(Helper.buildSpentThingsMessage(user, use.game(), "res"));
    }
}
