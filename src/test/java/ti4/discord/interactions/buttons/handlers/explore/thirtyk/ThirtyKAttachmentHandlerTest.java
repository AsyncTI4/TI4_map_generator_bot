package ti4.discord.interactions.buttons.handlers.explore.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThirtyKAttachmentHandlerTest {

    // Cards ready when status homework starts, so status scoring still belongs to the action-phase cycle
    // (Profitable Factories must not pay twice for one ready cycle).
    @Test
    void attachmentsReadyAgainOnlyOnceStatusHomeworkStarts() {
        String actionPhase = ThirtyKAttachmentHandler.readyCycleMarker(3, "action");
        String agendaPhase = ThirtyKAttachmentHandler.readyCycleMarker(3, "agendaVoting");
        assertThat(ThirtyKAttachmentHandler.readyCycleMarker(3, "statusScoring"))
                .isEqualTo(actionPhase);
        assertThat(ThirtyKAttachmentHandler.readyCycleMarker(3, "statusHomework"))
                .isEqualTo(agendaPhase)
                .isNotEqualTo(actionPhase);
        assertThat(ThirtyKAttachmentHandler.readyCycleMarker(4, "strategy")).isNotEqualTo(actionPhase);
    }
}
