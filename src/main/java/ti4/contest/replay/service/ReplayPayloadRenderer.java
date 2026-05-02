package ti4.contest.replay.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatCandidateEventType;
import ti4.contest.replay.core.CombatReplayDecoys;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.renderers.CombatReplayTileRenderer;
import ti4.contest.replay.core.renderers.CombatRollPayloadRenderer;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
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
    private final CombatCandidateEventRepository candidateEventRepository;

    public RenderedReplayEvent render(Game game, CombatCandidateEntity candidate, CombatCandidateEventEntity event) {
        ReplayDispatchPayload payload = payloadSerializer.read(event);
        if (payload == null) return message(event.getSummaryText(), List.of());

        RenderContext context = new RenderContext(game, candidate, event);
        return renderPayload(context, payload);
    }

    public Game restoreReplayGame(
            String snapshotJson, Game game, CombatCandidateEntity candidate, String tilePosition) {
        return restoreReplayGame(snapshotJson, game, candidate, tilePosition, true);
    }

    public Game restoreReplayGame(
            String snapshotJson,
            Game game,
            CombatCandidateEntity candidate,
            String tilePosition,
            boolean applyReplayDecoys) {
        String initialContextJson = candidate == null ? snapshotJson : candidate.getInitialRenderSnapshotJson();
        Game snapshotGame = CombatReplayTileRenderer.render(initialContextJson, snapshotJson);
        if (snapshotGame == null || candidate == null) return snapshotGame;
        if (applyReplayDecoys) {
            CombatReplayDecoys.applyToTile(snapshotGame, tilePosition, readReplayAbilities(candidate));
        }
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
        String content = firstNonBlank(
                renderHitAssignSummary(context, payload), context.event().getSummaryText());
        return tileRender(content, List.of(), payload.tilePosition(), payload.combatStateSnapshotJson());
    }

    private RenderedReplayEvent renderTileRenderMessage(
            RenderContext context, ReplayDispatchPayload.TileRenderMessageDispatch payload) {
        ReplayDispatchPayload.DiscordMessage replayMessage = payload.message();
        String content = replayMessage == null
                ? context.event().getSummaryText()
                : firstNonBlank(replayMessage.content(), context.event().getSummaryText());
        List<MessageEmbed> embeds =
                replayMessage == null ? List.of() : ReplayDispatchSerializer.toMessageEmbeds(replayMessage.embeds());
        return tileRender(
                content,
                embeds,
                payload.tilePosition(),
                payload.combatStateSnapshotJson(),
                context.event().getEventType() != CombatCandidateEventType.RESOLVED);
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
        return tileRender(content, embeds, tilePosition, snapshotJson, true);
    }

    private TileRenderResult tileRender(
            String content,
            List<MessageEmbed> embeds,
            String tilePosition,
            String snapshotJson,
            boolean applyReplayDecoys) {
        return new TileRenderResult(content, embeds, tilePosition, snapshotJson, applyReplayDecoys);
    }

    private void removeReplayCommandCounters(Game snapshotGame, String tilePosition) {
        snapshotGame.getTileByPosition(tilePosition).removeAllCC();
    }

    private String firstNonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private String renderHitAssignSummary(RenderContext context, ReplayDispatchPayload.HitAssignDispatch payload) {
        String previousSnapshotJson = previousTileSnapshotJson(context, payload.tilePosition());
        if (StringUtils.isBlank(previousSnapshotJson)) return null;

        Game previous =
                restoreReplayGame(previousSnapshotJson, context.game(), context.candidate(), payload.tilePosition());
        Game current = restoreReplayGame(
                payload.combatStateSnapshotJson(), context.game(), context.candidate(), payload.tilePosition());
        if (previous == null || current == null) return null;

        List<String> changes = hitAssignmentChanges(previous, current, payload.tilePosition());
        if (changes.isEmpty()) return null;
        return context.event().getSummaryText() + "\n" + String.join("\n", changes);
    }

    private String previousTileSnapshotJson(RenderContext context, String tilePosition) {
        CombatCandidateEntity candidate = context.candidate();
        if (candidate == null || candidate.getId() == null) return null;

        String previousSnapshotJson = candidate.getInitialRenderSnapshotJson();
        for (CombatCandidateEventEntity event :
                candidateEventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidate.getId())) {
            if (event.getSequenceNumber() == null
                    || context.event().getSequenceNumber() == null
                    || event.getSequenceNumber() >= context.event().getSequenceNumber()) {
                break;
            }
            ReplayDispatchPayload eventPayload = payloadSerializer.read(event);
            if (eventPayload
                            instanceof
                            ReplayDispatchPayload.HitAssignDispatch(String position, String combatStateSnapshotJson)
                    && tilePosition.equals(position)) {
                previousSnapshotJson = combatStateSnapshotJson;
            } else if (eventPayload instanceof ReplayDispatchPayload.TileRenderMessageDispatch tileRender
                    && tilePosition.equals(tileRender.tilePosition())) {
                previousSnapshotJson = tileRender.combatStateSnapshotJson();
            }
        }
        return previousSnapshotJson;
    }

    private List<String> hitAssignmentChanges(Game previous, Game current, String tilePosition) {
        Map<UnitKey, Counts> before = unitCounts(previous, tilePosition);
        Map<UnitKey, Counts> after = unitCounts(current, tilePosition);
        List<String> changes = new ArrayList<>();

        for (Map.Entry<UnitKey, Counts> entry : before.entrySet()) {
            UnitKey key = entry.getKey();
            Counts previousCounts = entry.getValue();
            Counts currentCounts = after.getOrDefault(key, Counts.empty());
            int sustained = previousCounts.sustainedBy(currentCounts);
            int destroyed = previousCounts.total() - currentCounts.total();
            if (sustained > 0) {
                changes.add("- " + unitOwner(current, key.getColorID()) + " sustained "
                        + unitPhrase(key.getUnitType(), sustained) + ".");
            }
            if (destroyed > 0) {
                changes.add("- " + unitOwner(current, key.getColorID()) + " destroyed "
                        + unitPhrase(key.getUnitType(), destroyed) + ".");
            }
        }
        return changes;
    }

    private Map<UnitKey, Counts> unitCounts(Game game, String tilePosition) {
        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) return Map.of();

        Map<UnitKey, Counts> counts = new HashMap<>();
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : holder.getUnitKeys()) {
                counts.merge(unitKey, Counts.from(holder, unitKey), Counts::plus);
            }
        }
        return counts;
    }

    private String unitOwner(Game game, String colorId) {
        Player player = game.getPlayerFromColorOrFaction(colorId);
        if (player == null) return colorId;
        return player.getFactionEmoji();
    }

    private String unitPhrase(UnitType unitType, int count) {
        String name = unitType.humanReadableName().toLowerCase();
        if (count == 1 || unitType == UnitType.Infantry || unitType == UnitType.Pds) {
            return count + " " + name;
        }
        return count + " " + name + "s";
    }

    private record Counts(int none, int damaged, int galvanized, int damagedGalvanized) {

        private static Counts empty() {
            return new Counts(0, 0, 0, 0);
        }

        private static Counts from(UnitHolder holder, UnitKey key) {
            return new Counts(
                    holder.getUnitCountForState(key, UnitState.none),
                    holder.getUnitCountForState(key, UnitState.dmg),
                    holder.getUnitCountForState(key, UnitState.glv),
                    holder.getUnitCountForState(key, UnitState.dmg_glv));
        }

        private Counts plus(Counts other) {
            return new Counts(
                    none + other.none,
                    damaged + other.damaged,
                    galvanized + other.galvanized,
                    damagedGalvanized + other.damagedGalvanized);
        }

        private int total() {
            return none + damaged + galvanized + damagedGalvanized;
        }

        private int sustainedBy(Counts current) {
            int standardSustains = Math.min(Math.max(0, none - current.none), Math.max(0, current.damaged - damaged));
            int galvanizedSustains = Math.min(
                    Math.max(0, galvanized - current.galvanized),
                    Math.max(0, current.damagedGalvanized - damagedGalvanized));
            return standardSustains + galvanizedSustains;
        }
    }

    public sealed interface RenderedReplayEvent permits MessageResult, TileRenderResult {}

    public record MessageResult(String content, List<MessageEmbed> embeds) implements RenderedReplayEvent {}

    public record TileRenderResult(
            String content,
            List<MessageEmbed> embeds,
            String tilePosition,
            String snapshotJson,
            boolean applyReplayDecoys)
            implements RenderedReplayEvent {}

    private record RenderContext(Game game, CombatCandidateEntity candidate, CombatCandidateEventEntity event) {}
}
