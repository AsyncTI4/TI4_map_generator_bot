package ti4.contest.replay.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import ti4.contest.replay.core.CombatContestReplayStatus;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "combat_contest",
        indexes = {
            @Index(name = "idx_replay_contest_replay_status_due", columnList = "replay_status, next_replay_at"),
            @Index(name = "idx_replay_contest_posted_at", columnList = "posted_at")
        })
/**
 * Tracks the public replay contest lifecycle once a candidate has been promoted.
 */
public class CombatReplayContestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false, unique = true)
    private Long candidateId;

    @Column(name = "posted_at", nullable = false)
    private LocalDateTime postedAt;

    @Column(name = "public_channel_id", nullable = false)
    private Long publicChannelId;

    @Column(name = "public_message_id", nullable = false)
    private Long publicMessageId;

    @Column(name = "public_thread_id")
    private Long publicThreadId;

    @Column(name = "replay_status", nullable = false)
    private CombatContestReplayStatus replayStatus;

    @Column(name = "replay_start_at", nullable = false)
    private LocalDateTime replayStartAt;

    @Column(name = "next_replay_at", nullable = false)
    private LocalDateTime nextReplayAt;

    @Column(name = "next_event_sequence", nullable = false)
    private Integer nextEventSequence;

    @Column(name = "replay_completed_at")
    private LocalDateTime replayCompletedAt;

    @Column(name = "pre_replay_context_posted_at")
    private LocalDateTime preReplayContextPostedAt;

    @Column(name = "leaderboard_posted_at")
    private LocalDateTime leaderboardPostedAt;

    @Column(name = "replay_error", columnDefinition = "TEXT")
    private String replayError;
}
