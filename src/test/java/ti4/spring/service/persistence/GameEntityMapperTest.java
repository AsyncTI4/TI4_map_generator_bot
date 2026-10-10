package ti4.spring.service.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameFileStamp;
import ti4.game.persistence.ManagedGameState;
import ti4.testUtils.BaseTi4Test;

class GameEntityMapperTest extends BaseTi4Test {

    private static final GameFileStamp NO_FILE = new GameFileStamp(0, 0);
    private static final List<String> COLORS = List.of("red", "blue", "green", "yellow", "purple", "black");

    @Test
    void replacedPlayerUsesStatsTrackedUserNameFromTheGameFile() {
        Game game = new Game();
        game.setName("mapper-replaced-player");
        Player replacement = game.addPlayer("replacement-id", "Replacement");
        replacement.setFaction("sol");
        replacement.setColor("red");
        replacement.setStatsTrackedUserID("original-id");
        replacement.setStatsTrackedUserName("Original");
        addRealPlayer(game, "second-id", "hacan");
        addRealPlayer(game, "third-id", "xxcha");

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game, NO_FILE);

        PlayerEntity playerEntity = playerFor(snapshot, "original-id");
        assertThat(playerEntity.isReplaced()).isTrue();
        assertThat(playerEntity.getUser().getId()).isEqualTo("original-id");
        assertThat(playerEntity.getUser().getName()).isEqualTo("Original");
        // Only users a player or title row points at are written, otherwise the nightly reconciler would
        // delete the replacement's unreferenced user row and report it every night.
        assertThat(snapshot.users())
                .extracting(UserEntity::getId)
                .containsExactlyInAnyOrder("original-id", "second-id", "third-id");
    }

    @Test
    void replacedPlayerWithNoKnownNameGetsPlaceholder() {
        Game game = new Game();
        game.setName("mapper-unknown-player");
        Player replacement = game.addPlayer("replacement-id", "Replacement");
        replacement.setFaction("sol");
        replacement.setColor("red");
        replacement.setStatsTrackedUserID("original-id");
        replacement.setStatsTrackedUserName(null);
        addRealPlayer(game, "second-id", "hacan");
        addRealPlayer(game, "third-id", "xxcha");

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game, NO_FILE);

        UserEntity user = playerFor(snapshot, "original-id").getUser();
        // The placeholder lets GameEntityPersistenceService keep any real name already stored for this user.
        assertThat(GameEntityMapper.hasUnknownName(user)).isTrue();
    }

    @Test
    void gameWithFewerThanThreePlayersIsStatisticsIgnoredButKeepsItsParticipants() {
        Game game = new Game();
        game.setName("mapper-two-player-game");
        addRealPlayer(game, "first-id", "sol");
        addRealPlayer(game, "second-id", "hacan");
        game.setStoredValue("TitlesForfirst-id", "Kingmaker");

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game, new GameFileStamp(1234, 56));

        assertThat(snapshot.game().isStatisticsIgnored()).isTrue();
        // Statistics queries read player and title rows, so an ignored game must not write any.
        assertThat(snapshot.game().getPlayers()).isEmpty();
        assertThat(snapshot.titles()).isEmpty();
        assertThat(snapshot.users()).isEmpty();
        assertThat(snapshot.game().getParticipants())
                .extracting(GameParticipantEntity::getUserId)
                .containsExactly("first-id", "second-id");
        assertThat(snapshot.game().getGameFileModifiedEpochMilliseconds()).isEqualTo(1234);
        assertThat(snapshot.game().getGameFileSizeBytes()).isEqualTo(56);
    }

    @Test
    void seatsWithoutAFactionAreParticipantsButNotPlayers() {
        Game game = new Game();
        game.setName("mapper-unseated-participant");
        addRealPlayer(game, "first-id", "sol");
        addRealPlayer(game, "second-id", "hacan");
        addRealPlayer(game, "third-id", "xxcha");
        game.addPlayer("spectator-id", "Spectator");

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game, NO_FILE);

        assertThat(snapshot.game().isStatisticsIgnored()).isFalse();
        assertThat(snapshot.game().getPlayers())
                .extracting(player -> player.getUser().getId())
                .containsExactlyInAnyOrder("first-id", "second-id", "third-id");
        assertThat(snapshot.game().getParticipants())
                .filteredOn(participant -> "spectator-id".equals(participant.getUserId()))
                .singleElement()
                .satisfies(participant -> {
                    assertThat(participant.getUserName()).isEqualTo("Spectator");
                    assertThat(participant.isRealPlayer()).isFalse();
                });
    }

    @Test
    void managedGameStateSurvivesTheDatabaseRoundTrip() {
        Game game = new Game();
        game.setName("mapper-managed-round-trip");
        addRealPlayer(game, "first-id", "sol");
        addRealPlayer(game, "second-id", "hacan");
        addRealPlayer(game, "third-id", "xxcha");
        game.addPlayer("spectator-id", "Spectator");
        game.setFowMode(true);
        game.setBotFactionReacts(true);
        game.setBotStratReacts(true);
        game.setInjectRulesLinks(true);
        game.setFastSCFollowMode(true);
        game.setHasEnded(true);
        game.setEndedDate(5678);
        game.setRound(4);
        game.setActivePlayerID("second-id");
        game.setGuildID("111");
        game.setMainChannelID("222");
        game.setTableTalkChannelID("333");
        game.setLaunchPostThreadID("444");
        game.setLastModifiedDate(9999);

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game, new GameFileStamp(4321, 87));
        PersistedManagedGame persisted = GameEntityMapper.toPersistedManagedGame(
                snapshot.game(), participantsOf(snapshot).reversed());

        // Warmup rebuilds ManagedGames from this state, so it must match what the game file would produce.
        assertThat(persisted.state()).isEqualTo(ManagedGameState.of(game));
        assertThat(persisted.state().participants())
                .extracting(ManagedGameState.Participant::userId)
                .containsExactly("first-id", "second-id", "spectator-id", "third-id");
        assertThat(persisted.state().guildId()).isEqualTo("111");
        assertThat(persisted.state().endedDate()).isEqualTo(5678);
        assertThat(persisted.gameFileStamp()).isEqualTo(new GameFileStamp(4321, 87));
    }

    @Test
    void gameThatNeverEndedRoundTripsWithAnEndedDateOfZero() {
        Game game = new Game();
        game.setName("mapper-not-ended");
        addRealPlayer(game, "first-id", "sol");

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game, NO_FILE);

        assertThat(snapshot.game().getEndedEpochMilliseconds()).isNull();
        assertThat(GameEntityMapper.toPersistedManagedGame(snapshot.game(), participantsOf(snapshot))
                        .state())
                .isEqualTo(ManagedGameState.of(game));
    }

    private static void addRealPlayer(Game game, String userId, String faction) {
        Player player = game.addPlayer(userId, "User " + userId);
        player.setFaction(faction);
        player.setColor(COLORS.get(game.getPlayers().size() - 1));
    }

    private static List<ManagedGameState.Participant> participantsOf(GameEntitySnapshot snapshot) {
        return snapshot.game().getParticipants().stream()
                .map(participant -> new ManagedGameState.Participant(
                        participant.getUserId(), participant.getUserName(), participant.isRealPlayer()))
                .toList();
    }

    private static PlayerEntity playerFor(GameEntitySnapshot snapshot, String userId) {
        return snapshot.game().getPlayers().stream()
                .filter(player -> player.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow();
    }
}
