package ti4.contest.replay.dispatch;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.SneakyThrows;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.stereotype.Component;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.json.JsonMapperManager;

@Component
/**
 * Serializes replay dispatch payloads to JSON and converts persisted embed data to and from JDA embeds.
 */
public class ReplayDispatchSerializer {

    @SneakyThrows
    public String write(ReplayDispatchPayload payload) {
        if (payload == null) return null;
        return JsonMapperManager.basic().writeValueAsString(payload);
    }

    public ReplayDispatchPayload read(CombatCandidateEventEntity event) {
        if (event == null
                || event.getPayloadJson() == null
                || event.getPayloadJson().isBlank()) return null;
        return read(event.getPayloadJson());
    }

    @SneakyThrows
    public ReplayDispatchPayload read(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return null;
        return JsonMapperManager.basic().readValue(payloadJson, ReplayDispatchPayload.class);
    }

    public static ReplayDispatchPayload.DiscordEmbed fromMessageEmbed(MessageEmbed embed) {
        if (embed == null) return null;

        MessageEmbed.AuthorInfo author = embed.getAuthor();
        MessageEmbed.Footer footer = embed.getFooter();
        MessageEmbed.Thumbnail thumbnail = embed.getThumbnail();
        MessageEmbed.ImageInfo image = embed.getImage();

        return new ReplayDispatchPayload.DiscordEmbed(
                embed.getTitle(),
                embed.getDescription(),
                embed.getUrl(),
                embed.getColor() == null ? null : embed.getColorRaw(),
                embed.getTimestamp() == null ? null : embed.getTimestamp().toString(),
                author == null
                        ? null
                        : new ReplayDispatchPayload.DiscordAuthor(
                                author.getName(), author.getUrl(), author.getIconUrl()),
                footer == null ? null : new ReplayDispatchPayload.DiscordFooter(footer.getText(), footer.getIconUrl()),
                thumbnail == null ? null : new ReplayDispatchPayload.DiscordThumbnail(thumbnail.getUrl()),
                image == null ? null : new ReplayDispatchPayload.DiscordImage(image.getUrl()),
                embed.getFields().stream()
                        .map(field -> new ReplayDispatchPayload.DiscordField(
                                field.getName(), field.getValue(), field.isInline()))
                        .toList());
    }

    public static List<ReplayDispatchPayload.DiscordEmbed> fromMessageEmbeds(List<MessageEmbed> embeds) {
        if (embeds == null || embeds.isEmpty()) return List.of();
        return embeds.stream().map(ReplayDispatchSerializer::fromMessageEmbed).toList();
    }

    public static MessageEmbed toMessageEmbed(ReplayDispatchPayload.DiscordEmbed spec) {
        if (spec == null) return null;

        EmbedBuilder builder = new EmbedBuilder();
        if (spec.title() != null || spec.url() != null) {
            builder.setTitle(spec.title(), spec.url());
        }
        if (spec.description() != null) {
            builder.setDescription(spec.description());
        }
        if (spec.color() != null) {
            builder.setColor(spec.color());
        }
        if (spec.timestampIso() != null) {
            builder.setTimestamp(OffsetDateTime.parse(spec.timestampIso()));
        }
        if (spec.author() != null) {
            builder.setAuthor(
                    spec.author().name(), spec.author().url(), spec.author().iconUrl());
        }
        if (spec.footer() != null) {
            builder.setFooter(spec.footer().text(), spec.footer().iconUrl());
        }
        if (spec.thumbnail() != null) {
            builder.setThumbnail(spec.thumbnail().url());
        }
        if (spec.image() != null) {
            builder.setImage(spec.image().url());
        }
        for (ReplayDispatchPayload.DiscordField field : spec.fields()) {
            builder.addField(field.name(), field.value(), field.inline());
        }
        return builder.build();
    }

    public static List<MessageEmbed> toMessageEmbeds(List<ReplayDispatchPayload.DiscordEmbed> embeds) {
        if (embeds == null || embeds.isEmpty()) return List.of();
        return embeds.stream().map(ReplayDispatchSerializer::toMessageEmbed).toList();
    }
}
