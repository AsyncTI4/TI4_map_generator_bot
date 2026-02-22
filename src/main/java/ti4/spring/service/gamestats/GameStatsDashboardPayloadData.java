package ti4.spring.service.gamestats;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "game_stats_dashboard_payload")
class GameStatsDashboardPayloadData {

    @Id
    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Column(name = "async_game_id")
    private String asyncGameId;

    @Column(name = "async_fun_game_name")
    private String asyncFunGameName;

    @Column(name = "hex_summary", columnDefinition = "TEXT")
    private String hexSummary;

    @Column(name = "is_pok")
    private boolean pok;

    @Column(name = "map_string", columnDefinition = "TEXT")
    private String mapString;

    @Column(name = "platform")
    private String platform;

    @Column(name = "round_number")
    private int round;

    @Column(name = "scoreboard")
    private int scoreboard;

    @Column(name = "creation_epoch_ms")
    private long creationEpochMilliseconds;

    @Column(name = "ended_epoch_ms")
    private Long endedEpochMilliseconds;

    @Column(name = "last_updated_epoch_ms")
    private Long lastUpdatedEpochMilliseconds;

    @Column(name = "speaker")
    private String speaker;

    @Column(name = "timestamp_seconds")
    private long timestamp;

    @Column(name = "ended_timestamp_seconds")
    private Long endedTimestamp;

    @Column(name = "turn_color")
    private String turn;

    @Column(name = "is_completed")
    private boolean completed;

    @Column(name = "is_fracture_in_play")
    private boolean fractureInPlay;

    @Column(name = "is_homebrew")
    private boolean homebrew;

    @Column(name = "is_discordant_stars_mode")
    private boolean discordantStarsMode;

    @Column(name = "is_absol_mode")
    private boolean absolMode;

    @Column(name = "is_franken_game")
    private boolean frankenGame;

    @Column(name = "is_alliance_mode")
    private boolean allianceMode;

    @Column(name = "is_tigl_game")
    private boolean tiglGame;

    @ElementCollection
    @CollectionTable(name = "game_stats_dashboard_law", joinColumns = @JoinColumn(name = "game_name"))
    @Column(name = "law", nullable = false)
    private List<String> laws = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "game_stats_dashboard_mode", joinColumns = @JoinColumn(name = "game_name"))
    @Column(name = "mode_name", nullable = false)
    private List<String> modes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "game_stats_dashboard_winner", joinColumns = @JoinColumn(name = "game_name"))
    @Column(name = "winner_user_id", nullable = false)
    private List<String> winners = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "game_stats_dashboard_unpicked_sc", joinColumns = @JoinColumn(name = "game_name"))
    @MapKeyColumn(name = "strategy_card_name")
    @Column(name = "trade_goods")
    private Map<String, Integer> unpickedStrategyCards = new HashMap<>();

    @OneToMany(mappedBy = "gameStats", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<GameStatsDashboardObjectiveData> objectives = new ArrayList<>();

    @OneToMany(mappedBy = "gameStats", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<GameStatsDashboardPlayerData> players = new ArrayList<>();

    void addObjective(String category, String objectiveName) {
        objectives.add(new GameStatsDashboardObjectiveData(this, category, objectiveName));
    }

    void addPlayer(GameStatsDashboardPlayerData playerData) {
        playerData.setGameStats(this);
        players.add(playerData);
    }
}
