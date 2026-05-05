package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatCandidateEventType;
import ti4.contest.replay.core.CombatReplayDecoys;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;

class ReplayPayloadRendererTest {

    private final ReplayPayloadRenderer renderer =
            new ReplayPayloadRenderer(new ReplayDispatchSerializer(), mock(CombatCandidateEventRepository.class));

    @Test
    void readsPersistedReplayDecoysWhenDebugDecoyFlagIsOff() {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setReplayAbilitiesJson(CombatReplayDecoys.addDecoy(
                null,
                new CombatReplayDecoys.DecoyUnit("mentak", ":mentak:", "blu", UnitType.Cruiser, Constants.SPACE, 1)));

        CombatReplayDecoys.Abilities abilities = renderer.readReplayAbilities(candidate);

        assertTrue(abilities.hasDecoys());
    }

    @Test
    void resolvedTileRenderSkipsReplayDecoysForFinalImage() {
        CombatCandidateEventEntity event = event(
                CombatCandidateEventType.RESOLVED,
                ReplayDispatchPayload.tileRenderMessage("000", "{}", "## Contest Result\nWinner"));

        ReplayPayloadRenderer.RenderedReplayEvent rendered = renderer.render(null, new CombatCandidateEntity(), event);

        ReplayPayloadRenderer.TileRenderResult tileRender = (ReplayPayloadRenderer.TileRenderResult) rendered;
        assertFalse(tileRender.applyReplayDecoys());
    }

    @Test
    void nonResolvedTileRenderSkipsReplayDecoysAfterReplayStarts() {
        CombatCandidateEventEntity event = event(
                CombatCandidateEventType.HIT_ASSIGN,
                ReplayDispatchPayload.tileRenderMessage("000", "{}", "## Combat Update"));

        ReplayPayloadRenderer.RenderedReplayEvent rendered = renderer.render(null, new CombatCandidateEntity(), event);

        ReplayPayloadRenderer.TileRenderResult tileRender = (ReplayPayloadRenderer.TileRenderResult) rendered;
        assertFalse(tileRender.applyReplayDecoys());
    }

    private CombatCandidateEventEntity event(CombatCandidateEventType eventType, ReplayDispatchPayload payload) {
        CombatCandidateEventEntity event = new CombatCandidateEventEntity();
        event.setEventType(eventType);
        event.setSummaryText("summary");
        event.setPayloadJson(new ReplayDispatchSerializer().write(payload));
        return event;
    }
}
