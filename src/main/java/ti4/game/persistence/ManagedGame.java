package ti4.game.persistence;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.discord.utility.DiscordChannelUtility;
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
    private final boolean injectRules;
    private final String creationDate;
    private final long lastModifiedDate;
    private final String activePlayerId;
    private final long lastActivePlayerChange;
    private final long endedDate;
    private final int round;
    private final Guild guild;
    private final String mainGameChannelId;
    private final String tableTalkChannelId;
    private final String launchPostThreadId;
    private final Set<ManagedPlayer> players;
    private final Map<ManagedPlayer, Boolean> playerToIsReal;

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
        injectRules = game.isInjectRulesLinks();
        creationDate = game.getCreationDate();
        lastModifiedDate = game.getLastModifiedDate();
        activePlayerId = sanitizeToNull(game.getActivePlayerID());
        lastActivePlayerChange = game.getLastActivePlayerChange() == null
                ? 0
                : game.getLastActivePlayerChange().getTime();
        endedDate = game.getEndedDate();
        round = game.getRound();
        guild = game.getGuild();
        Channel channel = game.getMainGameChannel();
        mainGameChannelId = channel != null ? channel.getId() : null;
        channel = game.getTableTalkChannel();
        tableTalkChannelId = channel != null ? channel.getId() : null;
        channel = game.getLaunchPostThread();
        launchPostThreadId = channel != null ? channel.getId() : null;

        players = game.getPlayers().values().stream()
                .map(p -> GameManager.addOrMergePlayer(this, p))
                .collect(toUnmodifiableSet());
        playerToIsReal = game.getPlayers().values().stream()
                .collect(Collectors.toUnmodifiableMap(
                        p -> getPlayer(p.getUserID()),
                        p -> ((p.isRealPlayer() && !p.isNpc()) || (p.isEliminated() && game.isHasEnded()))));
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

    @Nullable
    public TextChannel getMainGameChannel() {
        return getCurrentTextChannel(mainGameChannelId);
    }

    @Nullable
    public TextChannel getTableTalkChannel() {
        return getCurrentTextChannel(tableTalkChannelId);
    }

    @Nullable
    public ThreadChannel getLaunchPostThread() {
        if (launchPostThreadId == null) return null;
        return DiscordChannelUtility.retrieveThreadChannelById(guild, launchPostThreadId)
                .complete();
    }

    @Nullable
    private TextChannel getCurrentTextChannel(String channelId) {
        if (channelId == null) return null;
        return JdaService.jda.getTextChannelById(channelId);
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
