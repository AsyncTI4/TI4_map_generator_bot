package ti4.spring.service.tournament;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tournament_pool")
public class Tournament {

    public static final int DEFAULT_GROUP_SIZE = 6;
    public static final int DEFAULT_GAMES_PER_CATEGORY = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "series_name", nullable = false)
    private String seriesName;

    @Column(name = "round_label", nullable = false)
    private String roundLabel;

    @Column(name = "previous_round_id")
    private Long previousRoundId;

    @Column(name = "fog_mode", nullable = false)
    private boolean fogMode;

    @Column(name = "host_guild_id")
    private String hostGuildId;

    @Column(name = "panel_channel_id")
    private String panelChannelId;

    @Column(name = "panel_message_id")
    private String panelMessageId;

    @Column(name = "participant_role_id")
    private String participantRoleId;

    @Column(name = "target_size", nullable = false)
    private int targetSize;

    @Column(name = "group_size", nullable = false)
    private int groupSize = DEFAULT_GROUP_SIZE;

    @Column(name = "games_per_category", nullable = false)
    private int gamesPerCategory = DEFAULT_GAMES_PER_CATEGORY;

    @Column(name = "match_active_hours", nullable = false)
    private boolean matchActiveHours = true;

    @Column(name = "group_seed")
    private Long groupSeed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TournamentStatus status = TournamentStatus.OPEN;

    @Column(name = "game_setup_json", columnDefinition = "TEXT")
    private String gameSetupJson;

    @Column(name = "game_masters_by_group_json", columnDefinition = "TEXT")
    private String gameMastersByGroupJson;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    public String displayName() {
        return seriesName + " · " + roundLabel;
    }
}
