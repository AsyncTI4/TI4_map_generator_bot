package ti4.game.persistence;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;

@Getter
public class ManagedGame {

    private static final long SIXTY_DAYS_MILLISECONDS = 1000L * 60 * 60 * 24 * 60;

    // BE CAREFUL ADDING FIELDS TO THIS CLASS, AS IT CAN EASILY BALLOON THE DATA ON THE HEAP BY MEGABYTES PER FIELD
    private final String name;
    private final boolean hasEnded;
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
        name = game.getName();
        hasEnded = game.isHasEnded();
        hasWinner = game.hasWinner();
        vpGoalReached =
                game.getPlayers().values().stream().anyMatch(player -> player.getTotalVictoryPoints() >= game.getVp());
        fowMode = game.isFowMode();
        factionReactMode = game.isBotFactionReacts();
        twilightsFallMode = game.isTwilightsFallMode();
        colorReactMode = game.isBotColorReacts();
        stratReactMode = game.isBotStratReacts();
        fastScFollowMode = game.isFastSCFollowMode();
        fogQol01 = FoWHelper.isFogQol01(game);
        injectRules = game.isInjectRulesLinks();
        autoPingEnabled = game.getAutoPingStatus();
        temporaryPingDisable = game.isTemporaryPingDisable();
        creationDateTime = game.getCreationDateTime();
        lastModifiedDate = game.getLastModifiedDate();
        activePlayerId = sanitizeToNull(game.getActivePlayerID());
        lastActivePlayerChange = game.getLastActivePlayerChange() == null
                ? 0
                : game.getLastActivePlayerChange().getTime();
        endedDate = game.getEndedDate();
        round = game.getRound();
        guild = game.getGuild();
        mainGameChannel = game.getMainGameChannel();
        tableTalkChannel = game.getTableTalkChannel();
        launchPostThread = game.getLaunchPostThread();

        players = game.getPlayers().values().stream()
                .map(p -> GameManager.addOrMergePlayer(this, p))
                .collect(toUnmodifiableSet());
        playerToStatus = game.getPlayers().values().stream()
                .collect(Collectors.toUnmodifiableMap(
                        p -> getPlayer(p.getUserID()), p -> Status.of(p, game.isHasEnded())));
        teamMateToPrimaryId = collectTeamMates(game);
    }

    private static Map<String, String> collectTeamMates(Game game) {
        if (!game.isCommunityMode()) {
            return Collections.emptyMap();
        }
        return game.getPlayers().values().stream()
                .flatMap(player -> player.getTeamMateIDs().stream()
                        .filter(id -> !id.equals(player.getUserID()))
                        .map(id -> Map.entry(id, player.getUserID())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, (first, _) -> first));
    }

    public String getSeatUserId(String id) {
        if (hasPlayer(id)) return id;
        return teamMateToPrimaryId.getOrDefault(id, id);
    }

    public boolean hasPlayerOrTeamMate(String id) {
        return hasPlayer(id) || teamMateToPrimaryId.containsKey(id);
    }

    private static String sanitizeToNull(String str) {
        if (StringUtils.isBlank(str) || "null".equalsIgnoreCase(str)) {
            return null;
        }
        return str;
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

    public boolean matches(Game game) {
        return name.equals(game.getName()) && lastModifiedDate == game.getLastModifiedDate();
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

        static Status of(Player player, boolean gameHasEnded) {
            if ((player.isRealPlayer() && !player.isNpc()) || (player.isEliminated() && gameHasEnded)) {
                return REAL;
            }
            if (player.isRealPlayer() || player.isEliminated()) {
                return REAL_OR_ELIMINATED;
            }
            return OTHER;
        }

        boolean isRealOrEliminated() {
            return this != OTHER;
        }
    }
}
