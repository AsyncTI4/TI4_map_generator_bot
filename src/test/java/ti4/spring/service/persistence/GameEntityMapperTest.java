package ti4.spring.service.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ManagedGameState;
import ti4.testUtils.BaseTi4Test;

class GameEntityMapperTest extends BaseTi4Test {

    private static final List<String> COLORS = List.of("red", "blue", "green", "yellow", "purple", "black");

    @Test
    void snapshotCapturesRevisionKeyAndMappingVersion() {
        Game game = new Game();
        game.setName("mapper-revision");
        game.setRevisionKey("saved-revision");

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);
        game.setRevisionKey("later-revision");

        assertThat(snapshot.game().getRevisionKey()).isEqualTo("saved-revision");
        assertThat(snapshot.game().getMappingVersion()).isEqualTo(GameEntityMapper.MAPPING_VERSION);
    }

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

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);

        PlayerEntity playerEntity = playerFor(snapshot, "original-id");
        assertThat(playerEntity.isReplaced()).isTrue();
        assertThat(playerEntity.getStatsUser().getId()).isEqualTo("original-id");
        assertThat(playerEntity.getStatsUser().getName()).isEqualTo("Original");
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

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);

        UserEntity user = playerFor(snapshot, "original-id").getStatsUser();
        // The placeholder lets GameEntityPersistenceService keep any real name already stored for this user.
        assertThat(GameEntityMapper.hasUnknownName(user)).isTrue();
    }

    @Test
    void gameWithFewerThanThreePlayersIsStoredLikeAnyOtherButStatisticsIgnored() {
        Game game = new Game();
        game.setName("mapper-two-player-game");
        addRealPlayer(game, "first-id", "sol");
        addRealPlayer(game, "second-id", "hacan");
        game.setStoredValue("TitlesForfirst-id", "Kingmaker");

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);

        assertThat(snapshot.game().isStatisticsIgnored()).isTrue();
        assertThat(snapshot.game().getPlayers())
                .extracting(player -> player.getStatsUser().getId())
                .containsExactlyInAnyOrder("first-id", "second-id");
        assertThat(snapshot.titles()).extracting(TitleEntity::getTitle).containsExactly("Kingmaker");
        assertThat(snapshot.users()).extracting(UserEntity::getId).containsExactly("first-id", "second-id");
    }

    @Test
    void seatWithoutAFactionIsStoredAsAPlayerRowThatIsNotPlaying() {
        Game game = new Game();
        game.setName("mapper-unseated-participant");
        addRealPlayer(game, "first-id", "sol");
        addRealPlayer(game, "second-id", "hacan");
        addRealPlayer(game, "third-id", "xxcha");
        game.addPlayer("spectator-id", "Spectator");

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);

        assertThat(snapshot.game().isStatisticsIgnored()).isFalse();
        assertThat(snapshot.game().getPlayerCount()).isEqualTo(3);
        assertThat(snapshot.game().getPlayers())
                .filteredOn(PlayerEntity::isPlaying)
                .extracting(player -> player.getStatsUser().getId())
                .containsExactlyInAnyOrder("first-id", "second-id", "third-id");
        PlayerEntity spectator = playerFor(snapshot, "spectator-id");
        assertThat(spectator.isPlaying()).isFalse();
        assertThat(spectator.getPlayingUserId()).isEqualTo("spectator-id");
        assertThat(spectator.getPlayingUserName()).isEqualTo("Spectator");
    }

    @Test
    void everyKindOfSeatIsStoredWithWhatWarmupNeedsToTellThemApart() {
        Game game = new Game();
        game.setName("mapper-seat-kinds");
        addRealPlayer(game, "first-id", "sol");
        Player replacement = game.addPlayer("replacement-id", "Replacement");
        replacement.setFaction("hacan");
        replacement.setColor("blue");
        replacement.setStatsTrackedUserID("original-id");
        replacement.setStatsTrackedUserName("Original");
        Player npc = game.addPlayer("npc-id", "Npc");
        npc.setFaction("xxcha");
        npc.setColor("green");
        npc.setNpc(true);
        Player eliminated = game.addPlayer("eliminated-id", "Eliminated");
        eliminated.setFaction("letnev");
        eliminated.setColor("yellow");
        eliminated.setEliminated(true);
        eliminated.setDummy(true);
        Player dummy = game.addPlayer("dummy-id", "Dummy");
        dummy.setFaction("mentak");
        dummy.setColor("purple");
        dummy.setDummy(true);
        // A seat whose stats tracking points elsewhere but that is not playing counts for nobody, so it keeps
        // its own user and cannot collide with the user whose stats it once tracked.
        Player setup = game.addPlayer("setup-id", "Setup");
        setup.setStatsTrackedUserID("first-id");

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);

        assertThat(snapshot.game().getPlayers())
                .extracting(
                        player -> player.getStatsUser().getId(),
                        PlayerEntity::getPlayingUserId,
                        PlayerEntity::isPlaying,
                        PlayerEntity::isEliminated,
                        PlayerEntity::isNpc,
                        PlayerEntity::isReplaced)
                .containsExactlyInAnyOrder(
                        tuple("first-id", "first-id", true, false, false, false),
                        tuple("original-id", "replacement-id", true, false, false, true),
                        tuple("npc-id", "npc-id", true, false, true, false),
                        tuple("eliminated-id", "eliminated-id", true, true, false, false),
                        tuple("dummy-id", "dummy-id", false, false, false, false),
                        tuple("setup-id", "setup-id", false, false, false, false));
        assertThat(GameEntityMapper.toManagedGameState(snapshot.game(), participantsOf(snapshot)))
                .isEqualTo(ManagedGameState.of(game));
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

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);
        ManagedGameState persisted = GameEntityMapper.toManagedGameState(
                snapshot.game(), participantsOf(snapshot).reversed());

        // Warmup rebuilds ManagedGames from this state, so it must match what the game file would produce.
        assertThat(persisted).isEqualTo(ManagedGameState.of(game));
        assertThat(persisted.participants())
                .extracting(ManagedGameState.Participant::userId)
                .containsExactly("first-id", "second-id", "spectator-id", "third-id");
        assertThat(persisted.guildId()).isEqualTo("111");
        assertThat(persisted.endedDate()).isEqualTo(5678);
    }

    @Test
    void gameThatNeverEndedRoundTripsWithAnEndedDateOfZero() {
        Game game = new Game();
        game.setName("mapper-not-ended");
        addRealPlayer(game, "first-id", "sol");

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);

        assertThat(snapshot.game().getEndedEpochMilliseconds()).isNull();
        assertThat(GameEntityMapper.toManagedGameState(snapshot.game(), participantsOf(snapshot)))
                .isEqualTo(ManagedGameState.of(game));
    }

    private static void addRealPlayer(Game game, String userId, String faction) {
        Player player = game.addPlayer(userId, "User " + userId);
        player.setFaction(faction);
        player.setColor(COLORS.get(game.getPlayers().size() - 1));
    }

    private static List<ManagedGameState.Participant> participantsOf(GameEntitySnapshot snapshot) {
        return snapshot.game().getPlayers().stream()
                .map(player -> new ParticipantRow(
                                player.getGame().getGameName(),
                                player.getPlayingUserId(),
                                player.getPlayingUserName(),
                                player.isPlaying(),
                                player.isEliminated(),
                                player.isNpc())
                        .toParticipant())
                .toList();
    }

    private static PlayerEntity playerFor(GameEntitySnapshot snapshot, String statsUserId) {
        return snapshot.game().getPlayers().stream()
                .filter(player -> player.getStatsUser().getId().equals(statsUserId))
                .findFirst()
                .orElseThrow();
    }
}
