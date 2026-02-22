package ti4.spring.service.gamestats;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "game_stats_dashboard_player")
class GameStatsDashboardPlayerData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_name", nullable = false)
    private GameStatsDashboardPayloadData gameStats;

    @Column(name = "discord_user_id")
    private String discordUserId;

    @Column(name = "discord_username")
    private String discordUsername;

    @Column(name = "color_actual")
    private String colorActual;

    @Column(name = "command_token_fleet")
    private Integer commandTokenFleet;

    @Column(name = "command_token_strategy")
    private Integer commandTokenStrategy;

    @Column(name = "command_token_tactics")
    private Integer commandTokenTactics;

    @Column(name = "commodities")
    private Integer commodities;

    @Column(name = "max_commodities")
    private Integer maxCommodities;

    @Column(name = "custodian_points")
    private Integer custodianPoints;

    @Column(name = "faction_name")
    private String factionName;

    @Column(name = "hand_summary_secret_objectives")
    private Integer handSummarySecretObjectives;

    @Column(name = "hand_summary_actions")
    private Integer handSummaryActions;

    @Column(name = "hand_summary_promissory")
    private Integer handSummaryPromissory;

    @Column(name = "leader_hero")
    private String leaderHero;

    @Column(name = "leader_commander")
    private String leaderCommander;

    @Column(name = "influence_available")
    private Integer influenceAvailable;

    @Column(name = "influence_total")
    private Integer influenceTotal;

    @Column(name = "resources_available")
    private Integer resourcesAvailable;

    @Column(name = "resources_total")
    private Integer resourcesTotal;

    @Column(name = "legendary_count")
    private Integer legendaryCount;

    @Column(name = "traits_cultural")
    private Integer traitsCultural;

    @Column(name = "traits_hazardous")
    private Integer traitsHazardous;

    @Column(name = "traits_industrial")
    private Integer traitsIndustrial;

    @Column(name = "tech_specialty_blue")
    private Integer techSpecialtyBlue;

    @Column(name = "tech_specialty_green")
    private Integer techSpecialtyGreen;

    @Column(name = "tech_specialty_red")
    private Integer techSpecialtyRed;

    @Column(name = "tech_specialty_yellow")
    private Integer techSpecialtyYellow;

    @Column(name = "score")
    private Integer score;

    @Column(name = "trade_goods")
    private Integer tradeGoods;

    @Column(name = "turn_order")
    private Integer turnOrder;

    @Column(name = "total_number_of_turns")
    private Integer totalNumberOfTurns;

    @Column(name = "total_turn_time")
    private Long totalTurnTime;

    @Column(name = "expected_hits")
    private Double expectedHits;

    @Column(name = "actual_hits")
    private Integer actualHits;

    @Column(name = "is_eliminated")
    private Boolean eliminated;

    @ElementCollection
    @CollectionTable(name = "game_stats_player_alliance", joinColumns = @JoinColumn(name = "player_stats_id"))
    @Column(name = "alliance_color", nullable = false)
    private List<String> alliances = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "game_stats_player_law", joinColumns = @JoinColumn(name = "player_stats_id"))
    @Column(name = "law_name", nullable = false)
    private List<String> laws = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "game_stats_player_objective", joinColumns = @JoinColumn(name = "player_stats_id"))
    @Column(name = "objective_name", nullable = false)
    private List<String> objectives = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "game_stats_player_relic", joinColumns = @JoinColumn(name = "player_stats_id"))
    @Column(name = "relic_name", nullable = false)
    private List<String> relicCards = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "game_stats_player_strategy_card", joinColumns = @JoinColumn(name = "player_stats_id"))
    @Column(name = "strategy_card_name", nullable = false)
    private List<String> strategyCards = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "game_stats_player_technology", joinColumns = @JoinColumn(name = "player_stats_id"))
    @Column(name = "technology_name", nullable = false)
    private List<String> technologies = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "game_stats_player_teammate", joinColumns = @JoinColumn(name = "player_stats_id"))
    @Column(name = "teammate_user_id", nullable = false)
    private List<String> teammateIds = new ArrayList<>();
}
