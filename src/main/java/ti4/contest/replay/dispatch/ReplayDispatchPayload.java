package ti4.contest.replay.dispatch;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ReplayDispatchPayload.DiscordMessageDispatch.class, name = "DISCORD_MESSAGE"),
    @JsonSubTypes.Type(value = ReplayDispatchPayload.HitAssignDispatch.class, name = "HIT_ASSIGN")
})
/**
 * Canonical persisted replay action model.
 *
 * <p>Most replay events store the exact Discord message payload to resend later, while hit assignment keeps the
 * custom image-rendering inputs.
 */
public interface ReplayDispatchPayload {

    static ReplayDispatchPayload discordMessage(String content) {
        return new DiscordMessageDispatch(new DiscordMessage(content, List.of()));
    }

    static ReplayDispatchPayload discordMessage(String content, MessageEmbed embed) {
        return discordMessage(content, embed == null ? List.of() : List.of(embed));
    }

    static ReplayDispatchPayload discordMessage(String content, List<MessageEmbed> embeds) {
        return new DiscordMessageDispatch(
                new DiscordMessage(content, ReplayDispatchSerializer.fromMessageEmbeds(embeds)));
    }

    static ReplayDispatchPayload hitAssign(String tilePosition, String combatStateSnapshotJson) {
        return new HitAssignDispatch(tilePosition, combatStateSnapshotJson);
    }

    record DiscordMessageDispatch(DiscordMessage message) implements ReplayDispatchPayload {}

    record HitAssignDispatch(String tilePosition, String combatStateSnapshotJson) implements ReplayDispatchPayload {}

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
