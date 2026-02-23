package ti4.spring.service.statistics;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.map.FractureService;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "game")
class GameEntity {

    @Id
    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Column(name = "creation_epoch_milliseconds")
    private long creationEpochMilliseconds;

    @Column(name = "ended_epoch_milliseconds")
    private Long endedEpochMilliseconds;

    @Column(name = "round_number")
    private int round;

    @Column(name = "is_completed")
    private boolean completed;

    @Column(name = "is_prophecy_of_kings")
    private boolean prophecyOfKings;

    @Column(name = "is_thunders_edge")
    private boolean thundersEdge;

    @Column(name = "is_fracture_in_play")
    private boolean fractureInPlay;

    @Column(name = "is_homebrew")
    private boolean homebrew;

    @Column(name = "is_discordant_stars_mode")
    private boolean discordantStarsMode;

    @Column(name = "is_absol_mode")
    private boolean absolMode;

    @Column(name = "is_franken_mode")
    private boolean frankenMode;

    @Column(name = "is_alliance_mode")
    private boolean allianceMode;

    @Column(name = "is_twilight_imperium_global_league")
    private boolean twilightImperiumGlobalLeague;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PlayerEntity> players = new ArrayList<>();

    GameEntity(Game game) {
        setGameName(game.getName());
        setRound(game.getRound());
        setCreationEpochMilliseconds(game.getCreationDateTime());
        setEndedEpochMilliseconds(game.getEndedDate());
        setCompleted(game.getWinner().isPresent() && game.isHasEnded());
        setFractureInPlay(FractureService.isFractureInPlay(game));
        setHomebrew(game.isHomebrew());
        setDiscordantStarsMode(game.isDiscordantStarsMode());
        setAbsolMode(game.isAbsolMode());
        setFrankenMode(game.isFrankenGame());
        setAllianceMode(game.isAllianceMode());
        setTwilightImperiumGlobalLeague(game.isCompetitiveTIGLGame());
        setProphecyOfKings(game.isProphecyOfKings());
        setThundersEdge(game.isThundersEdge());

        for (Player player : game.getPlayers().values()) {
            var playerEntity = new PlayerEntity(game, player);
            playerEntity.setGame(this);
            players.add(playerEntity);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        GameEntity that = (GameEntity) other;
        return Objects.equals(gameName, that.gameName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameName);
    }
}
