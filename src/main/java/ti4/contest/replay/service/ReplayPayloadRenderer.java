package ti4.contest.replay.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatReplayDecoys;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.renderers.CombatReplayTileRenderer;
import ti4.contest.replay.core.renderers.CombatRollPayloadRenderer;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.ActionCardModel;
import ti4.model.LeaderModel;
import ti4.model.TechnologyModel;

/**
 * Converts persisted replay payloads into Discord messages or tile-render requests during replay playback.
 */
@Service
@RequiredArgsConstructor
public class ReplayPayloadRenderer {

    private final ReplayDispatchSerializer payloadSerializer;

    public RenderedReplayEvent render(Game game, CombatCandidateEntity candidate, CombatCandidateEventEntity event) {
        ReplayDispatchPayload payload = payloadSerializer.read(event);
        if (payload == null) return message(event.getSummaryText(), List.of());

        RenderContext context = new RenderContext(game, candidate, event);
        return renderPayload(context, payload);
    }

    public Game restoreReplayGame(
            String snapshotJson, Game game, CombatCandidateEntity candidate, String tilePosition) {
        String initialContextJson = candidate == null ? snapshotJson : candidate.getInitialRenderSnapshotJson();
        Game snapshotGame = CombatReplayTileRenderer.render(initialContextJson, snapshotJson, game);
        if (snapshotGame == null || candidate == null) return snapshotGame;
        CombatReplayDecoys.applyToTile(snapshotGame, tilePosition, readReplayAbilities(candidate));
        removeReplayCommandCounters(snapshotGame, tilePosition);
        snapshotGame.setName(CombatReplayTileRenderer.buildReplaySnapshotName(
                candidate.getAttackerFaction(), candidate.getDefenderFaction()));
        return snapshotGame;
    }

    public CombatReplayDecoys.Abilities readReplayAbilities(CombatCandidateEntity candidate) {
        return candidate == null
                ? new CombatReplayDecoys.Abilities(null)
                : CombatReplayDecoys.read(candidate.getReplayAbilitiesJson());
    }

    private RenderedReplayEvent renderPayload(RenderContext context, ReplayDispatchPayload payload) {
        if (payload instanceof ReplayDispatchPayload.HitAssignDispatch hitAssign) {
            return renderHitAssign(context, hitAssign);
        }
        if (payload instanceof ReplayDispatchPayload.TileRenderMessageDispatch tileRenderMessage) {
            return renderTileRenderMessage(context, tileRenderMessage);
        }
        if (payload instanceof ReplayDispatchPayload.GenericMessageDispatch genericMessage) {
            return renderGenericMessage(context, genericMessage);
        }
        if (payload instanceof ReplayDispatchPayload.CombatRollDispatch combatRoll) {
            return renderCombatRoll(context, combatRoll);
        }
        if (payload instanceof ReplayDispatchPayload.LeaderPlayedDispatch leaderPlayed) {
            return renderLeaderPlayed(context, leaderPlayed);
        }
        if (payload instanceof ReplayDispatchPayload.ActionCardPlayedDispatch actionCardPlayed) {
            return renderActionCardPlayed(context, actionCardPlayed);
        }
        if (payload instanceof ReplayDispatchPayload.TechPlayedDispatch techPlayed) {
            return renderTechPlayed(context, techPlayed);
        }
        if (payload instanceof ReplayDispatchPayload.TechExhaustedDispatch techExhausted) {
            return renderTechExhausted(context, techExhausted);
        }
        if (payload instanceof ReplayDispatchPayload.RetreatDeclaredDispatch retreatDeclared) {
            return renderRetreatDeclared(context, retreatDeclared);
        }
        if (payload instanceof ReplayDispatchPayload.RetreatResolvedDispatch retreatResolved) {
            return renderRetreatResolved(context, retreatResolved);
        }
        return message(context.event().getSummaryText(), List.of());
    }

    private RenderedReplayEvent renderHitAssign(
            RenderContext context, ReplayDispatchPayload.HitAssignDispatch payload) {
        return tileRender(
                context.event().getSummaryText(), List.of(), payload.tilePosition(), payload.combatStateSnapshotJson());
    }

    private RenderedReplayEvent renderTileRenderMessage(
            RenderContext context, ReplayDispatchPayload.TileRenderMessageDispatch payload) {
        ReplayDispatchPayload.DiscordMessage replayMessage = payload.message();
        String content = replayMessage == null
                ? context.event().getSummaryText()
                : firstNonBlank(replayMessage.content(), context.event().getSummaryText());
        List<MessageEmbed> embeds =
                replayMessage == null ? List.of() : ReplayDispatchSerializer.toMessageEmbeds(replayMessage.embeds());
        return tileRender(content, embeds, payload.tilePosition(), payload.combatStateSnapshotJson());
    }

    private RenderedReplayEvent renderGenericMessage(
            RenderContext context, ReplayDispatchPayload.GenericMessageDispatch payload) {
        return message(payload.message(), context.event().getSummaryText());
    }

    private RenderedReplayEvent renderCombatRoll(
            RenderContext context, ReplayDispatchPayload.CombatRollDispatch rollDispatch) {
        CombatRollPayload payloadWithAbilities =
                CombatReplayDecoys.applyToRoll(rollDispatch.payload(), readReplayAbilities(context.candidate()));
        String rendered = CombatRollPayloadRenderer.render(payloadWithAbilities);
        String renderedWithHeader = StringUtils.isBlank(rendered) ? null : "## Roll Update\n" + rendered;
        String content = firstNonBlank(
                renderedWithHeader,
                firstNonBlank(rollDispatch.message().content(), context.event().getSummaryText()));
        return message(content, List.of());
    }

    private RenderedReplayEvent renderLeaderPlayed(
            RenderContext context, ReplayDispatchPayload.LeaderPlayedDispatch payload) {
        LeaderModel leader = Mapper.getLeader(payload.leaderId());
        if (leader == null) return message(context.event().getSummaryText(), List.of());

        Game game = context.game();
        String verb = "hero".equalsIgnoreCase(leader.getType()) ? "played" : "used";
        MessageEmbed embed = leader.getRepresentationEmbed(
                false,
                true,
                false,
                game != null && Constants.VERBOSITY_VERBOSE.equals(game.getOutputVerbosity()),
                game != null && game.isTwilightsFallMode());
        return message(
                replayMessage(
                        "## " + leaderHeader(leader) + "\n" + actor(context, true) + " " + verb + " _"
                                + leader.getName() + "_.",
                        embed),
                context.event().getSummaryText());
    }

    private RenderedReplayEvent renderActionCardPlayed(
            RenderContext context, ReplayDispatchPayload.ActionCardPlayedDispatch payload) {
        ActionCardModel actionCard = Mapper.getActionCard(payload.actionCardId());
        if (actionCard == null) return message(context.event().getSummaryText(), List.of());

        return message(
                replayMessage(
                        "## Action Card\n" + actor(context, true) + " played _" + actionCard.getName() + "_.",
                        actionCard.getRepresentationEmbed(false, true, context.game())),
                context.event().getSummaryText());
    }

    private RenderedReplayEvent renderTechPlayed(
            RenderContext context, ReplayDispatchPayload.TechPlayedDispatch payload) {
        return renderTech(context, payload.techId(), "used");
    }

    private RenderedReplayEvent renderTechExhausted(
            RenderContext context, ReplayDispatchPayload.TechExhaustedDispatch payload) {
        return renderTech(context, payload.techId(), "exhausted");
    }

    private RenderedReplayEvent renderRetreatDeclared(
            RenderContext context, ReplayDispatchPayload.RetreatDeclaredDispatch payload) {
        return message("## Retreat\n" + actor(context, false) + " announced a retreat.", List.of());
    }

    private RenderedReplayEvent renderRetreatResolved(
            RenderContext context, ReplayDispatchPayload.RetreatResolvedDispatch payload) {
        String destination = firstNonBlank(payload.destination(), "an unknown system");
        return message("## Retreat\n" + actor(context, false) + " retreated to " + destination + ".", List.of());
    }

    private RenderedReplayEvent renderTech(RenderContext context, String techId, String verb) {
        TechnologyModel tech = Mapper.getTech(techId);
        if (tech == null) return message(context.event().getSummaryText(), List.of());

        return message(
                replayMessage(
                        "## Combat Ability\n" + actor(context, false) + " " + verb + " _" + tech.getName() + "_.",
                        tech.getRepresentationEmbed()),
                context.event().getSummaryText());
    }

    private String leaderHeader(LeaderModel leader) {
        return switch (StringUtils.lowerCase(leader.getType())) {
            case "agent" -> "Agent";
            case "hero" -> "Hero";
            case "commander" -> "Commander";
            case "envoy" -> "Envoy";
            default -> "Leader";
        };
    }

    private String actor(RenderContext context, boolean ping) {
        Game game = context.game();
        String actorFaction = context.event().getActorFaction();
        Player player = game == null ? null : game.getPlayerFromColorOrFaction(actorFaction);
        if (player == null) return actorFaction;
        return ping ? player.getRepresentation() : player.getRepresentationNoPing();
    }

    private ReplayDispatchPayload.DiscordMessage replayMessage(String content, MessageEmbed embed) {
        return new ReplayDispatchPayload.DiscordMessage(
                content, ReplayDispatchSerializer.fromMessageEmbeds(embed == null ? List.of() : List.of(embed)));
    }

    private MessageResult message(ReplayDispatchPayload.DiscordMessage message, String fallbackContent) {
        return new MessageResult(
                firstNonBlank(message.content(), fallbackContent),
                ReplayDispatchSerializer.toMessageEmbeds(message.embeds()));
    }

    private MessageResult message(String content, List<MessageEmbed> embeds) {
        return new MessageResult(content, embeds);
    }
    private TileRenderResult tileRender(
            String content, List<MessageEmbed> embeds, String tilePosition, String snapshotJson) {
        return new TileRenderResult(content, embeds, tilePosition, snapshotJson);
    }

    private void removeReplayCommandCounters(Game snapshotGame, String tilePosition) {
        snapshotGame.getTileByPosition(tilePosition).removeAllCC();
    }

    private String firstNonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    public sealed interface RenderedReplayEvent permits MessageResult, TileRenderResult {}

    public record MessageResult(String content, List<MessageEmbed> embeds) implements RenderedReplayEvent {}

    public record TileRenderResult(String content, List<MessageEmbed> embeds, String tilePosition, String snapshotJson)
            implements RenderedReplayEvent {}

    private record RenderContext(Game game, CombatCandidateEntity candidate, CombatCandidateEventEntity event) {}
}
