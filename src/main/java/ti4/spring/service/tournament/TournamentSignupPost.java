package ti4.spring.service.tournament;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tournament_signup_post")
public class TournamentSignupPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "tournament_id", nullable = false)
    private long tournamentId;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    TournamentSignupPost(long tournamentId, String guildId, String channelId, String messageId) {
        this.tournamentId = tournamentId;
        this.guildId = guildId;
        this.channelId = channelId;
        this.messageId = messageId;
    }
}
