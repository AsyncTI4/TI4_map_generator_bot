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
import ti4.service.leader.agent.AgentUse;

public final class TnelisAgent extends AgentModule<Player> {

    static final String ID = "tnelisagent";
    static final String FACTION_KEY = "tnelisAgentFaction";

    public TnelisAgent() {
        super(ID, "Davish S'Norri, the Tnelis");
    }

    public static Button button(Player tnelis) {
        return Buttons.gray(
                ownedButtonId(tnelis, ID, tnelis.getFaction()),
                useLabel(tnelis, ID, "Tnelis Agent"),
                FactionEmojis.tnelis);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return Optional.of(user);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Game game = use.game();
        Player tnelis = use.user();
        game.setStoredValue(FACTION_KEY, tnelis.getFaction());
        ButtonHelper.resolveCombatRoll(
                tnelis, game, use.event(), "combatRoll_" + game.getActiveSystem() + "_space_bombardment");
        game.setStoredValue(FACTION_KEY, "");
        return AgentOutcome.none();
    }
}
