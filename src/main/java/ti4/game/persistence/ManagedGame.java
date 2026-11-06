package ti4.game.persistence;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.game.Game;

@Getter
public class ManagedGame {

    private static final long SIXTY_DAYS_MILLISECONDS = 1000L * 60 * 60 * 24 * 60;

    // BE CAREFUL ADDING FIELDS TO THIS CLASS, AS IT CAN EASILY BALLOON THE DATA ON THE HEAP BY MEGABYTES PER FIELD
    private final String name;
    private final String revisionKey;
    private final boolean hasEnded;
    private final boolean competitiveExportEligible;
    private final boolean hasWinner;
    private final boolean vpGoalReached;
    private final boolean fowMode;
    private final boolean factionReactMode;
    private final boolean twilightsFallMode;
    private final boolean colorReactMode;
    private final boolean stratReactMode;
    private final boolean fastScFollowMode;
    private final boolean fogQol01;
    private final boolean injectRules;
    private final boolean autoPingEnabled;
    private final boolean temporaryPingDisable;
    private final long creationDateTime;
    private final long lastModifiedDate;
    private final String activePlayerId;
    private final long lastActivePlayerChange;
    private final long endedDate;
    private final int round;
    private final Guild guild;
    private final TextChannel mainGameChannel;
    private final TextChannel tableTalkChannel;
    private final ThreadChannel launchPostThread;
    private final Set<ManagedPlayer> players;

    @Getter(AccessLevel.NONE)
    private final Map<ManagedPlayer, Status> playerToStatus;

    private final Map<String, String> teamMateToPrimaryId;

    public ManagedGame(Game game) {
        this(ManagedGameState.of(game));
    }

    public ManagedGame(ManagedGameState state) {
        name = state.name();
        revisionKey = state.revisionKey();
        hasEnded = state.hasEnded();
        competitiveExportEligible = state.competitiveExportEligible();
        hasWinner = state.hasWinner();
        vpGoalReached = state.vpGoalReached();
        fowMode = state.fowMode();
        factionReactMode = state.factionReactMode();
        twilightsFallMode = state.twilightsFallMode();
        colorReactMode = state.colorReactMode();
        stratReactMode = state.stratReactMode();
        fastScFollowMode = state.fastScFollowMode();
        fogQol01 = state.fogQol01();
        injectRules = state.injectRules();
        autoPingEnabled = state.autoPingEnabled();
        temporaryPingDisable = state.temporaryPingDisable();
        creationDateTime = state.creationDateTime();
        lastModifiedDate = state.lastModifiedDate();
        activePlayerId = state.activePlayerId();
        lastActivePlayerChange = state.lastActivePlayerChange();
        endedDate = state.endedDate();
        round = state.round();
        mainGameChannel = findById(state.mainGameChannelId(), id -> JdaService.jda.getTextChannelById(id));
        tableTalkChannel = findById(state.tableTalkChannelId(), id -> JdaService.jda.getTextChannelById(id));
        guild = findGuild(state.guildId(), mainGameChannel, tableTalkChannel);
        launchPostThread = findById(state.launchPostThreadId(), ManagedGame::findPrimaryGuildThread);

        players = state.participants().stream()
                .map(participant -> GameManager.addOrMergePlayer(this, participant))
                .collect(toUnmodifiableSet());
        playerToStatus = state.participants().stream()
                .collect(Collectors.toUnmodifiableMap(
                        participant -> getPlayer(participant.userId()),
                        participant -> Status.of(participant, hasEnded)));
        teamMateToPrimaryId = state.teamMateToPrimaryId();
    }

    private static <T> T findById(String id, Function<String, T> lookup) {
        if (JdaService.jda == null || !StringUtils.isNumeric(id)) return null;
        return lookup.apply(id);
    }

    private static Guild findGuild(String guildId, TextChannel mainGameChannel, TextChannel tableTalkChannel) {
        if (JdaService.jda != null && StringUtils.isNumeric(guildId)) {
            return JdaService.jda.getGuildById(guildId);
        }
        if (mainGameChannel != null) return mainGameChannel.getGuild();
        if (tableTalkChannel != null) return tableTalkChannel.getGuild();
        return null;
    }

    private static ThreadChannel findPrimaryGuildThread(String id) {
        if (JdaService.guildPrimary == null) return null;
        return JdaService.guildPrimary.getThreadChannelById(id);
    }

    public String getSeatUserId(String id) {
        if (hasPlayer(id)) return id;
        return teamMateToPrimaryId.getOrDefault(id, id);
    }

    public boolean hasPlayerOrTeamMate(String id) {
        return hasPlayer(id) || teamMateToPrimaryId.containsKey(id);
    }

    @Nullable
    public ManagedPlayer getPlayer(String id) {
        return players.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
    }

    public boolean hasPlayer(String id) {
        return players.stream().anyMatch(p -> p.getId().equals(id));
    }

    public boolean isActive() {
        return !hasEnded
                && !hasWinner
                && !vpGoalReached
                && (System.currentTimeMillis() - lastModifiedDate) < SIXTY_DAYS_MILLISECONDS;
    }

    public List<String> getPlayerIds() {
        return players.stream().map(ManagedPlayer::getId).toList();
    }

    public List<ManagedPlayer> getRealPlayers() {
        return playerToStatus.entrySet().stream()
                .filter(entry -> entry.getValue() == Status.REAL)
                .map(Map.Entry::getKey)
                .toList();
    }

    public long countRealAndEliminatedPlayers() {
        return playerToStatus.values().stream()
                .filter(Status::isRealOrEliminated)
                .count();
    }

    public boolean isRealOrEliminatedPlayer(String userId) {
        ManagedPlayer player = getPlayer(userId);
        return player != null && playerToStatus.get(player).isRealOrEliminated();
    }

    boolean matches(Game game) {
        return name.equals(game.getName())
                && lastModifiedDate == game.getLastModifiedDate()
                && Objects.equals(revisionKey, game.getRevisionKey());
    }

    public Game getGame() {
        return GameManager.get(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ManagedGame that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    private enum Status {
        REAL,
        REAL_OR_ELIMINATED,
        OTHER;

        static Status of(ManagedGameState.Participant participant, boolean hasEnded) {
            if (!participant.playing()) {
                return OTHER;
            }
            if (participant.eliminated() ? hasEnded : !participant.npc()) {
                return REAL;
            }
            return REAL_OR_ELIMINATED;
        }

        boolean isRealOrEliminated() {
            return this != OTHER;
        }
    }
}
