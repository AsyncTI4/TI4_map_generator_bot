package ti4.service.leader.agent.modules.homebrew;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.PlayerPickerAgent;

public final class KolleccAgent extends PlayerPickerAgent {

    static final String ID = "kolleccagent";
    private static final String EXPLORE_PREFIX = "kolleccAgentResStep2_";

    public KolleccAgent() {
        super(ID, "Captain Dust, the Kollecc");
    }

    public static Button button(Player kollecc) {
        return useButton(kollecc, ID, "Kollecc Agent", FactionEmojis.kollecc);
    }

    @Override
    protected AgentOutcome resolveFor(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.of(
                pickNote(use),
                Message.addressedTo(
                        target,
                        ", please choose the exploration deck to reveal 2 cards from.",
                        exploreButtons(use.game(), target)));
    }

    private static List<Button> exploreButtons(Game game, Player explorer) {
        String owner = explorer.factionButtonChecker();
        Set<String> traits = ButtonHelper.getTypesOfPlanetPlayerHas(game, explorer);
        List<Button> buttons = new ArrayList<>();
        if (traits.contains("industrial")) {
            buttons.add(Buttons.green(owner + EXPLORE_PREFIX + "industrial", "Explore Industrials X 2"));
        }
        if (traits.contains("cultural")) {
            buttons.add(Buttons.blue(owner + EXPLORE_PREFIX + "cultural", "Explore Culturals X 2"));
        }
        if (traits.contains("hazardous")) {
            buttons.add(Buttons.red(owner + EXPLORE_PREFIX + "hazardous", "Explore Hazardous X 2"));
        }
        return buttons;
    }
}
