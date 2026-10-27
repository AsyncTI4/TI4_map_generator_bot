package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.PlayerPickerAgent;

public final class PharadnAgent extends PlayerPickerAgent {

    static final String ID = "pharadnagent";
    private static final String PICK_PLAYER_PREFIX = "pharadnAgentSelect";

    public PharadnAgent() {
        super(ID, "Nokash, the Pharadn", PICK_PLAYER_PREFIX);
    }

    public static Button button(Player pharadn) {
        return Buttons.gray(ownedButtonId(pharadn, ID), useLabel(pharadn, ID, "Pharadn Agent"), FactionEmojis.pharadn);
    }

    @ButtonHandler(PICK_PLAYER_PREFIX + "_")
    public static void resolvePickedPlayer(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        resolvePickedPlayer(ID, buttonID, event, game, player);
    }

    @Override
    protected AgentOutcome resolveFor(AgentUse<Choice> use, Player target) {
        List<Button> planets = target.getPlanetsAllianceMode().stream()
                .map(planet -> Buttons.gray(
                        target.factionButtonChecker() + "pharadnAgentKill_" + planet,
                        Helper.getPlanetRepresentation(planet, use.game())))
                .toList();
        return AgentOutcome.offer(
                Message.addressedTo(
                        target, ", please choose the planet that you wish to destroy all your infantry on.", planets),
                Message.addressedTo(target, " controls no planets to destroy infantry on."));
    }
}
