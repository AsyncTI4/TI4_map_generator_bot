package ti4.service.leader.agent.modules.homebrew;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.unit.AddUnitService;

public final class DihmohnAgent extends AgentModule<String> {

    static final String ID = "dihmohnagent";

    public DihmohnAgent() {
        super(ID, "Jgin Faru, the Dih-Mohn");
    }

    public static Button button(Player dihmohn, String planet, String planetRepresentation) {
        return Buttons.green(
                ownedButtonId(dihmohn, ID, planet),
                useLabel(dihmohn, ID, "Dih-Mohn Agent on " + planetRepresentation),
                FactionEmojis.dihmohn);
    }

    @Override
    public Optional<String> decode(Game game, Player user, String payload) {
        return game.getTileFromPlanet(payload) == null ? Optional.empty() : Optional.of(payload);
    }

    @Override
    public AgentOutcome resolve(AgentUse<String> use) {
        Player user = use.user();
        String planet = use.payload();
        AddUnitService.addUnits(
                use.event(), use.game().getTileFromPlanet(planet), use.game(), user.getColor(), "1 inf " + planet);
        return AgentOutcome.of(Message.plain(
                user,
                user.getFactionEmojiOrColor() + " landed 1 extra " + UnitEmojis.infantry + " on "
                        + Helper.getPlanetRepresentation(planet, use.game()) + " using " + use.agentName() + ".\n"
                        + "-# You must have committed another unit to this planet before you get this infantry."
                        + " Please do this if you have not already done so."));
    }
}
