package ti4.contest.replay.dispatch;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.contest.replay.core.CombatRollPayload;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ReplayDispatchPayload.DiscordMessageDispatch.class, name = "DISCORD_MESSAGE"),
    @JsonSubTypes.Type(value = ReplayDispatchPayload.GenericMessageDispatch.class, name = "GENERIC_MESSAGE"),
    @JsonSubTypes.Type(value = ReplayDispatchPayload.LeaderPlayedDispatch.class, name = "LEADER_PLAYED"),
    @JsonSubTypes.Type(value = ReplayDispatchPayload.ActionCardPlayedDispatch.class, name = "ACTION_CARD_PLAYED"),
    @JsonSubTypes.Type(value = ReplayDispatchPayload.TechPlayedDispatch.class, name = "TECH_PLAYED"),
    @JsonSubTypes.Type(value = ReplayDispatchPayload.TechExhaustedDispatch.class, name = "TECH_EXHAUSTED"),
    @JsonSubTypes.Type(value = ReplayDispatchPayload.RetreatDeclaredDispatch.class, name = "RETREAT_DECLARED"),
    @JsonSubTypes.Type(value = ReplayDispatchPayload.RetreatResolvedDispatch.class, name = "RETREAT_RESOLVED"),
    @JsonSubTypes.Type(value = ReplayDispatchPayload.HitAssignDispatch.class, name = "HIT_ASSIGN"),
    @JsonSubTypes.Type(value = ReplayDispatchPayload.TileRenderMessageDispatch.class, name = "TILE_RENDER_MESSAGE"),
    @JsonSubTypes.Type(value = ReplayDispatchPayload.CombatRollDispatch.class, name = "COMBAT_ROLL")
})
/**
 * Canonical persisted replay action model.
 *
 * <p>Most replay events store the exact Discord message payload to resend later, while hit assignment keeps the
 * custom image-rendering inputs.
 */
public interface ReplayDispatchPayload {

    static ReplayDispatchPayload genericMessage(String content) {
        return new GenericMessageDispatch(new DiscordMessage(content, List.of()));
    }

    static ReplayDispatchPayload genericMessage(String content, MessageEmbed embed) {
        return genericMessage(content, embed == null ? List.of() : List.of(embed));
    }

    static ReplayDispatchPayload genericMessage(String content, List<MessageEmbed> embeds) {
        return new GenericMessageDispatch(
                new DiscordMessage(content, ReplayDispatchSerializer.fromMessageEmbeds(embeds)));
    }

    static ReplayDispatchPayload leaderPlayed(String leaderId) {
        return new LeaderPlayedDispatch(leaderId);
    }

    static ReplayDispatchPayload actionCardPlayed(String actionCardId) {
        return new ActionCardPlayedDispatch(actionCardId);
    }

    static ReplayDispatchPayload techPlayed(String techId) {
        return new TechPlayedDispatch(techId);
    }

    static ReplayDispatchPayload techExhausted(String techId) {
        return new TechExhaustedDispatch(techId);
    }

    static ReplayDispatchPayload retreatDeclared() {
        return new RetreatDeclaredDispatch();
    }

    static ReplayDispatchPayload retreatResolved(String destination) {
        return new RetreatResolvedDispatch(destination);
    }

    static ReplayDispatchPayload hitAssign(String tilePosition, String combatStateSnapshotJson) {
        return new HitAssignDispatch(tilePosition, combatStateSnapshotJson);
    }

    static ReplayDispatchPayload tileRenderMessage(
            String tilePosition, String combatStateSnapshotJson, String content) {
        return new TileRenderMessageDispatch(
                tilePosition, combatStateSnapshotJson, new DiscordMessage(content, List.of()));
    }

    static ReplayDispatchPayload tileRenderMessage(
            String tilePosition, String combatStateSnapshotJson, String content, List<MessageEmbed> embeds) {
        return new TileRenderMessageDispatch(
                tilePosition,
                combatStateSnapshotJson,
                new DiscordMessage(content, ReplayDispatchSerializer.fromMessageEmbeds(embeds)));
    }

    static ReplayDispatchPayload combatRoll(String content, CombatRollPayload payload) {
        return new CombatRollDispatch(new DiscordMessage(content, List.of()), payload);
    }

    record DiscordMessageDispatch(DiscordMessage message) implements ReplayDispatchPayload {}

    record GenericMessageDispatch(DiscordMessage message) implements ReplayDispatchPayload {}

    record LeaderPlayedDispatch(String leaderId) implements ReplayDispatchPayload {}

    record ActionCardPlayedDispatch(String actionCardId) implements ReplayDispatchPayload {}

    record TechPlayedDispatch(String techId) implements ReplayDispatchPayload {}

    record TechExhaustedDispatch(String techId) implements ReplayDispatchPayload {}

    record RetreatDeclaredDispatch() implements ReplayDispatchPayload {}

    record RetreatResolvedDispatch(String destination) implements ReplayDispatchPayload {}

    record HitAssignDispatch(String tilePosition, String combatStateSnapshotJson) implements ReplayDispatchPayload {}

    record TileRenderMessageDispatch(String tilePosition, String combatStateSnapshotJson, DiscordMessage message)
            implements ReplayDispatchPayload {}

    record CombatRollDispatch(DiscordMessage message, CombatRollPayload payload) implements ReplayDispatchPayload {}

    record DiscordMessage(String content, List<DiscordEmbed> embeds) {

        public DiscordMessage {
            embeds = embeds == null ? List.of() : List.copyOf(embeds);
        }
    }

    record DiscordEmbed(
            String title,
            String description,
            String url,
            Integer color,
            String timestampIso,
            DiscordAuthor author,
            DiscordFooter footer,
            DiscordThumbnail thumbnail,
            DiscordImage image,
            List<DiscordField> fields) {

        public DiscordEmbed {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    record DiscordField(String name, String value, boolean inline) {}

    record DiscordAuthor(String name, String url, String iconUrl) {}

    record DiscordFooter(String text, String iconUrl) {}

    record DiscordThumbnail(String url) {}

    record DiscordImage(String url) {}
}
