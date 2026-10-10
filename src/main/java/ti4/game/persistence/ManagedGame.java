package ti4.game.persistence;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
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
    private final Map<ManagedPlayer, Boolean> playerToIsReal;

    public ManagedGame(Game game) {
        this(ManagedGameState.of(game));
    }

    public ManagedGame(ManagedGameState state) {
        name = state.name();
        hasEnded = state.hasEnded();
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
        creationDateTime = state.creationDateTime();
        lastModifiedDate = state.lastModifiedDate();
        activePlayerId = state.activePlayerId();
        lastActivePlayerChange = state.lastActivePlayerChange();
        endedDate = state.endedDate();
        round = state.round();
        guild = findById(state.guildId(), id -> JdaService.jda.getGuildById(id));
        mainGameChannel = findById(state.mainGameChannelId(), id -> JdaService.jda.getTextChannelById(id));
        tableTalkChannel = findById(state.tableTalkChannelId(), id -> JdaService.jda.getTextChannelById(id));
        launchPostThread = findById(state.launchPostThreadId(), ManagedGame::findPrimaryGuildThread);

        players = state.participants().stream()
                .map(participant -> GameManager.addOrMergePlayer(this, participant))
                .collect(toUnmodifiableSet());
        playerToIsReal = state.participants().stream()
                .collect(Collectors.toUnmodifiableMap(
                        participant -> getPlayer(participant.userId()), ManagedGameState.Participant::realPlayer));
    }

    private static <T> T findById(String id, Function<String, T> lookup) {
        if (JdaService.jda == null || !StringUtils.isNumeric(id)) return null;
        return lookup.apply(id);
    }

    private static ThreadChannel findPrimaryGuildThread(String id) {
        if (JdaService.guildPrimary == null) return null;
        return JdaService.guildPrimary.getThreadChannelById(id);
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
        return playerToIsReal.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList();
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
}
