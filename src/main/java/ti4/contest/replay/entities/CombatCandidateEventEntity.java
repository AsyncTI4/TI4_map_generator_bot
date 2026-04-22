package ti4.contest.replay.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import ti4.contest.replay.core.CombatCandidateEventType;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "combat_candidate_event",
        indexes = {
            @Index(
                    name = "idx_combat_candidate_event_candidate_sequence",
                    columnList = "candidate_id, sequence_number",
                    unique = true),
            @Index(name = "idx_combat_candidate_event_candidate_occurred_at", columnList = "candidate_id, occurred_at")
        })
public class CombatCandidateEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private CombatCandidateEventType eventType;

    @Column(name = "round_number")
    private Integer roundNumber;

    @Column(name = "actor_faction")
    private String actorFaction;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;
}
