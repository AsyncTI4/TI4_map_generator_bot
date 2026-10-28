package ti4.service.leader.agent;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AgendaRiderHelper;
import ti4.helpers.ButtonHelper;
import ti4.model.LeaderModel;
import ti4.model.Mapper;
import ti4.service.emoji.TI4Emoji;
import ti4.service.leader.agent.AgentOutcome.Message;

public abstract class AgentModule<P> {

    private static final String CONTENT_FOR_EMPTY_MESSAGE = "Combat";

    private final String agentId;
    private final String displayName;

    protected AgentModule(String agentId, String displayName) {
        this.agentId = agentId;
        this.displayName = displayName;
    }

    public final String agentId() {
        return agentId;
    }

    public Set<String> aliasIds() {
        return Set.of();
    }

    public final String displayName() {
        return displayName;
    }

    public String displayName(String leaderId) {
        return displayName;
    }

    public abstract Optional<P> decode(Game game, Player user, String payload);

    public Optional<PlayerPickerAgent> picker() {
        return Optional.empty();
    }

    public Optional<String> unusableReason(Game game, Player user, P payload) {
        return Optional.empty();
    }

    public String reference(String cardName, boolean viaYssaril) {
        if (viaYssaril) {
            return "Clever Clever " + cardName + "/Yssaril agent";
        }
        return cardName + " agent";
    }

    public String exhaustAnnouncementAfterName(AgentUse<P> use) {
        return " has exhausted " + use.agentName() + ".";
    }

    public abstract AgentOutcome resolve(AgentUse<P> use);

    protected static Optional<Tile> activeSystem(Game game) {
        return Optional.ofNullable(game.getTileByPosition(game.getActiveSystem()));
    }

    protected static String ownedButtonId(Player owner, String agentId, String... payloadSegments) {
        return owner.factionButtonChecker() + AgentButtonIds.format(agentId, payloadSegments);
    }

    protected static void offerUnitOwnerPicker(
            ButtonInteractionEvent event, Game game, Player holder, String agentId, String position) {
        AgentOutcome outcome = AgentOutcome.of(Message.addressedTo(
                holder,
                ", please choose the faction to give " + AgentLifecycle.agentNameForHolder(game, holder, agentId)
                        + " to.",
                unitOwnerButtons(game, holder, agentId, position)));
        if (event.getMessage().getContentRaw().isEmpty()) {
            outcome = outcome.withPressedMessageEdit(CONTENT_FOR_EMPTY_MESSAGE);
        }
        AgentLifecycle.deliver(outcome, event, holder);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    protected static List<Button> unitOwnerButtons(Game game, Player holder, String agentId, String position) {
        return AgendaRiderHelper.getPlayerOutcomeButtons(game, null, AgentButtonIds.format(agentId, position), null)
                .stream()
                .map(button -> button.withCustomId(holder.factionButtonChecker() + button.getCustomId()))
                .toList();
    }

    protected static String ownedStepId(Player owner, String stepId, boolean viaYssaril) {
        return owner.factionButtonChecker() + AgentStepIds.flagCopy(stepId, viaYssaril);
    }

    protected static Button useButton(Player owner, String agentId, String agentLabel, TI4Emoji emoji) {
        return Buttons.gray(ownedButtonId(owner, agentId), useLabel(owner, agentId, agentLabel), emoji);
    }

    protected static String useLabel(Player owner, String agentId, String agentLabel) {
        return useLabel(owner, agentId, agentLabel, "");
    }

    protected static String useLabel(Player owner, String agentId, String agentLabel, String suffix) {
        String label = gameCardName(owner.getGame(), agentId).orElse(agentLabel);
        return (AgentLifecycle.holdsOnlyAYssarilCopy(owner, agentId) ? "Use Clever Clever " : "Use ") + label + suffix;
    }

    private static Optional<String> gameCardName(Game game, String agentId) {
        LeaderModel leader = Mapper.getLeader(agentId);
        if (leader == null) {
            return Optional.empty();
        }
        String name = leader.getName(game);
        return name.equals(leader.getName()) ? Optional.empty() : Optional.of(name);
    }
}
