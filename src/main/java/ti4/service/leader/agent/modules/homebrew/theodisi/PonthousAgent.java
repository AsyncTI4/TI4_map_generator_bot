package ti4.service.leader.agent.modules.homebrew.theodisi;

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
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.unit.AddUnitService;

public final class PonthousAgent extends AgentModule<PonthousAgent.Reinforcement> {

    static final String ID = "ponthousagent";
    private static final String CHOOSE_TARGET = "ponthousagent_";
    private static final String LEGACY_SELECT_TARGET = "ponthousAgentTarget_";
    private static final String SEPARATOR = "|";

    public record Reinforcement(Tile system, Player target) {}

    public PonthousAgent() {
        super(ID, "Caelyn Lorris, the Ponthous");
    }

    public static Button button(Player player, Tile tile) {
        return Buttons.gray(
                player.factionButtonChecker() + CHOOSE_TARGET + tile.getPosition(),
                useLabel(player, ID, "Ponthous Agent"),
                FactionEmojis.ponthous);
    }

    @ButtonHandler(CHOOSE_TARGET)
    static void chooseTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.substring(CHOOSE_TARGET.length());
        if (!player.hasUnexhaustedLeader(ID) || game.getTileByPosition(position) == null) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the player on whom to use Caelyn Lorris, the Ponthous agent.",
                targetButtons(game, player, position));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    static List<Button> targetButtons(Game game, Player holder, String position) {
        return game.getRealPlayers().stream()
                .map(target -> Buttons.gray(
                        ownedButtonId(holder, ID, position + SEPARATOR + target.getFaction()),
                        target.getColorDisplayName(),
                        target.fogSafeEmoji()))
                .toList();
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_SELECT_TARGET)
    static void legacySelectTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(LEGACY_SELECT_TARGET.length()).split("_", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        AgentLifecycle.pressLegacyButton(event, game, player, ID, parts[0] + SEPARATOR + parts[1]);
    }

    @Override
    public Optional<Reinforcement> decode(Game game, Player user, String payload) {
        Tile system = game.getTileByPosition(StringUtils.substringBefore(payload, SEPARATOR));
        Player target = game.getPlayerFromColorOrFaction(StringUtils.substringAfter(payload, SEPARATOR));
        if (system == null || target == null) {
            return Optional.empty();
        }
        return Optional.of(new Reinforcement(system, target));
    }

    @Override
    public AgentOutcome resolve(AgentUse<Reinforcement> use) {
        Game game = use.game();
        Player target = use.payload().target();
        Tile system = use.payload().system();
        AddUnitService.addUnits(use.event(), system, game, target.getColor(), "2 fighter, 2 infantry");
        String message = use.user().getRepresentation() + " exhausted " + use.agentNameFor(target) + " to place 2 "
                + "fighters and 2 infantry for " + target.getRepresentation() + " in the space area of "
                + system.getRepresentationForButtons(game, target) + ".";
        return AgentOutcome.notifyInFog(game, target, message, use.user()).deletingPressedMessage();
    }
}
