package ti4.contest.replay.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatReplayChannels;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.helpers.ThreadGetter;
import ti4.image.TileGenerator;
import ti4.message.MessageHelper;

@Service
@RequiredArgsConstructor
public class CombatReplayDiscordPostService {

    private final ti4.contest.replay.core.CombatContestSettings settings;
    private final ReplayPayloadRenderer replayPayloadRenderer;

    @SneakyThrows
    public Message postPromotionMessage(
            TextChannel contestChannel, String message, Game game, CombatCandidateEntity candidate) {
        return sendTileRenderMessage(
                contestChannel,
                message,
                List.of(),
                replayPayloadRenderer.restoreReplayGame(
                        candidate.getInitialRenderSnapshotJson(), game, candidate, candidate.getTilePosition()),
                candidate.getTilePosition());
    }

    public void postReplayEvent(
            MessageChannel channel, Game game, CombatCandidateEntity candidate, CombatCandidateEventEntity event) {
        ReplayPayloadRenderer.RenderedReplayEvent rendered = replayPayloadRenderer.render(game, candidate, event);
        if (rendered
                instanceof
                ReplayPayloadRenderer.TileRenderResult(
                        String content,
                        List<MessageEmbed> embeds,
                        String tilePosition,
                        String snapshotJson)) {
            sendTileRenderMessage(
                    channel,
                    content,
                    embeds,
                    replayPayloadRenderer.restoreReplayGame(snapshotJson, game, candidate, tilePosition),
                    tilePosition);
            return;
        }
        ReplayPayloadRenderer.MessageResult message = (ReplayPayloadRenderer.MessageResult) rendered;
        sendDiscordMessage(channel, message.content(), message.embeds());
    }

    @SneakyThrows
    public Message sendTileRenderMessage(
            MessageChannel channel, String message, List<MessageEmbed> embeds, Game snapshotGame, String tilePosition) {
        if (snapshotGame == null) {
            return sendDiscordMessage(channel, message, embeds);
        }
        try (FileUpload fileUpload = new TileGenerator(snapshotGame, null, null, 0, tilePosition).createFileUpload()) {
            List<String> messageParts = MessageHelper.splitLargeText(message, 2000);
            for (int i = 0; i < Math.max(0, messageParts.size() - 1); i++) {
                channel.sendMessage(messageParts.get(i)).complete();
            }
            MessageCreateBuilder builder = new MessageCreateBuilder().addFiles(fileUpload);
            if (!messageParts.isEmpty()) {
                builder.addContent(messageParts.get(messageParts.size() - 1));
            }
            if (!embeds.isEmpty()) builder.addEmbeds(embeds);
            return channel.sendMessage(builder.build()).complete();
        }
    }

    public static Message sendDiscordMessage(MessageChannel channel, String content, List<MessageEmbed> embeds) {
        if (embeds.isEmpty()) {
            return channel.sendMessage(content).complete();
        }
        if (content == null || content.isBlank()) {
            return channel.sendMessageEmbeds(embeds).complete();
        }
        return channel.sendMessage(content).addEmbeds(embeds).complete();
    }

    public ThreadChannel createReplayThread(Message posted, CombatCandidateEntity winner) {
        return posted.createThreadChannel(buildReplayThreadName(winner))
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR)
                .complete();
    }

    public static MessageChannel getContestThreadOrChannel(CombatReplayContestEntity contest) {
        if (JdaService.guildPrimary == null) return null;
        TextChannel contestChannel = JdaService.guildPrimary.getTextChannelById(contest.getPublicChannelId());
        if (contestChannel == null) return null;
        if (contest.getPublicThreadId() != null) {
            ThreadChannel thread = ThreadGetter.getThreadInChannelById(contestChannel, contest.getPublicThreadId());
            if (thread != null) return thread;
        }
        return contestChannel;
    }

    public TextChannel getContestChannel() {
        if (JdaService.guildPrimary == null) return null;
        List<TextChannel> channels =
                JdaService.guildPrimary.getTextChannelsByName(CombatReplayChannels.contestChannelName(settings), true);
        return channels.isEmpty() ? null : channels.getFirst();
    }

    private String buildReplayThreadName(CombatCandidateEntity candidate) {
        String attacker = normalizeThreadNamePart(candidate.getAttackerFaction());
        String defender = normalizeThreadNamePart(candidate.getDefenderFaction());
        String tilePosition = StringUtils.defaultIfBlank(candidate.getTilePosition(), "unknown");
        String candidateId =
                candidate.getId() == null ? "unknown" : candidate.getId().toString();
        return "combat-archive-c" + candidateId + "-t" + tilePosition + "-" + attacker + "-v-" + defender;
    }

    private static String normalizeThreadNamePart(String value) {
        String normalized = StringUtils.defaultIfBlank(value, "unknown")
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.isBlank()) return "unknown";
        return StringUtils.abbreviate(normalized, 18);
    }
}
