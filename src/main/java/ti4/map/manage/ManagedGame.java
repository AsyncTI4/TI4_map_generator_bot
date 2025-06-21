package ti4.map.manage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import ti4.map.Game;
import ti4.map.Player;

import static java.util.stream.Collectors.toUnmodifiableSet;

@Getter
public class ManagedGame { // BE CAREFUL ADDING FIELDS TO THIS CLASS, AS IT CAN EASILY BALLOON THE DATA ON THE HEAP BY MEGABYTES PER FIELD

    private final String name;
    private final boolean hasEnded;
    private final boolean hasWinner;
    private final boolean vpGoalReached;
    private final boolean fowMode;
    private final boolean factionReactMode;
    private final boolean injectRules;
    private final String creationDate;
    private final long lastModifiedDate;
    private final String activePlayerId;
    private final long lastActivePlayerChange;
    private final long endedDate;
    private final int round;
    private final Guild guild;
    private final TextChannel mainGameChannel;
    private final TextChannel actionsChannel;
    private final TextChannel tableTalkChannel;
    private final ThreadChannel launchPostThread;
    private final Set<ManagedPlayer> players;
    private final Map<ManagedPlayer, Boolean> playerToIsReal;
    private final boolean stale;

    public ManagedGame(Game game) {
        name = game.getName();
        hasEnded = game.isHasEnded();
        hasWinner = game.hasWinner();
        vpGoalReached = game.getPlayers().values().stream().anyMatch(player -> player.getTotalVictoryPoints() >= game.getVp());
        fowMode = game.isFowMode();
        factionReactMode = game.isBotFactionReacts();
        injectRules = game.isInjectRulesLinks();
        creationDate = game.getCreationDate();
        lastModifiedDate = game.getLastModifiedDate();
        activePlayerId = sanitizeToNull(game.getActivePlayerID());
        lastActivePlayerChange = game.getLastActivePlayerChange() == null ? 0 : game.getLastActivePlayerChange().getTime();
        endedDate = game.getEndedDate();
        round = game.getRound();
        guild = game.getGuild();
        mainGameChannel = game.getMainGameChannel();
        actionsChannel = game.getActionsChannel();
        tableTalkChannel = game.getTableTalkChannel();
        launchPostThread = game.getLaunchPostThread();

        players = game.getPlayers().values().stream().map(p -> GameManager.addOrMergePlayer(this, p)).collect(toUnmodifiableSet());
        playerToIsReal = game.getPlayers().values().stream().collect(Collectors.toUnmodifiableMap(p -> getPlayer(p.getUserID()), Player::isRealPlayer));

        final long sixtyDays = 1000L * 60 * 60 * 24 * 60;
        stale = (System.currentTimeMillis() - game.getLastModifiedDate()) > sixtyDays;
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

    public boolean hasPlayer(String playerId) {
        return players.stream().anyMatch(p -> p.getId().equals(playerId));
    }

    public boolean isActive() {
        return !hasEnded && !hasWinner && !vpGoalReached && !stale;
    }

    public List<String> getPlayerIds() {
        return players.stream().map(ManagedPlayer::getId).toList();
    }

    public List<ManagedPlayer> getRealPlayers() {
        return playerToIsReal.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
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
