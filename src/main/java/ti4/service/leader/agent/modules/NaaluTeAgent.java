package ti4.service.leader.agent.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.RemoveCommandCounterService;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class NaaluTeAgent extends AgentModule<NaaluTeAgent.Removal> {

    static final String ID = "naaluagent-te";
    private static final String LEGACY_USE = "useNaaluAgent_";

    public record Removal(Tile system, Player tokenOwner) {}

    public NaaluTeAgent() {
        super(ID, "Z'eu, the Naalu");
    }

    public static void offerTokenRemoval(Game game, Player naalu, Tile system, Player tokenOwner) {
        if (game.isFowMode() && system.hasFog(naalu) && tokenOwner != naalu) {
            return;
        }
        if (!naalu.hasUnexhaustedLeader(ID)) {
            return;
        }
        String systemRepresentation = system.getRepresentationForButtons(game, naalu);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                ownedButtonId(naalu, ID, system.getPosition(), tokenOwner.getFaction()),
                "Remove A Token From " + systemRepresentation,
                LeaderEmojis.NaaluAgent));
        buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel("No Thanks"));

        String message = " You may use " + Mapper.getLeader(ID).getNameRepresentation() + " to remove a "
                + tokenOwner.getColor() + " command token from " + systemRepresentation + ".";
        String addressee = tokenOwner == naalu ? naalu.getRepresentation() : naalu.getRepresentationNoPing();
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                naalu.getCardsInfoThread(), addressee + message, buttons);
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_USE)
    static void legacyUse(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_USE.length()));
    }

    @Override
    public Optional<Removal> decode(Game game, Player user, String payload) {
        Tile system = game.getTileByPosition(StringUtils.substringBefore(payload, "_"));
        if (system == null) {
            return Optional.empty();
        }
        return AgentTargets.player(game, StringUtils.substringAfter(payload, "_"))
                .map(tokenOwner -> new Removal(system, tokenOwner));
    }

    @Override
    public String exhaustAnnouncementAfterName(AgentUse<Removal> use) {
        return " has exhausted " + use.agentName() + " to remove the just-placed command token from "
                + use.payload().system().getRepresentationForButtons() + ".";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Removal> use) {
        RemoveCommandCounterService.fromTile(
                use.event(), use.payload().tokenOwner(), use.payload().system());
        return AgentOutcome.none();
    }
}
