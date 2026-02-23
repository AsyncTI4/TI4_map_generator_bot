package ti4.spring.service.roundstats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@IdClass(GameRoundPlayerStatsId.class)
@Table(name = "game_round_player_stats")
public class GameRoundPlayerStats {

    @Id
    @Column(name = "game_id", nullable = false)
    private String gameId;

    @Id
    @Column(name = "user_discord_id", nullable = false)
    private String userDiscordId;

    @Id
    @Column(name = "round", nullable = false)
    private int round;

    @Column(name = "max_army_cost")
    private Double maxArmyCost;

    @Column(name = "last_seen_army_cost")
    private Double lastSeenArmyCost;

    @Column(name = "sc_picks")
    private String scPicks;

    @Column(name = "combats_initiated")
    private Integer combatsInitiated;

    @Column(name = "tacticals_with_combat")
    private Integer tacticalsWithCombat;

    @Column(name = "planets_taken")
    private Integer planetsTaken;

    @Column(name = "planets_stolen")
    private Integer planetsStolen;

    @Column(name = "techs_gained")
    private String techsGained;

    @Column(name = "dice_rolled")
    private Integer diceRolled;

    @Column(name = "turn_times")
    private String turnTimes;
}
