package ti4.map.manage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import lombok.Getter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import ti4.map.Game;
import ti4.map.Player;

import static java.util.stream.Collectors.toUnmodifiableMap;
import static java.util.stream.Collectors.toUnmodifiableSet;

@Getter
public class ManagedGame { // BE CAREFUL ADDING FIELDS TO THIS CLASS, AS IT CAN EASILY BALLOON THE DATA ON THE HEAP BY MEGABYTES PER FIELD

    private final String name;
    private final boolean hasEnded;
    private final boolean hasWinner;
    private final boolean vpGoalReached;
    private final boolean fowMode;
    private final boolean communityMode;
    private final boolean factionReactMode;
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
    private final Set<ManagedPlayer> players;
    private final Map<ManagedPlayer, Boolean> playerToIsReal; // TODO: keep all these in an object rather than a bunch of maps
    private final Map<ManagedPlayer, Integer> playerToTotalTurns; // TODO unsure if keeping
    private final Map<ManagedPlayer, Long> playerToTurnTime; // TODO unsure if keeping
    private final Map<ManagedPlayer, Integer> playerToExpectedHitsTimes10; // TODO unsure if keeping
    private final Map<ManagedPlayer, Integer> playerToActualHits; // TODO unsure if keeping
    //private final Map<ManagedPlayer, Role> playerToCommunityRole;
    //private final Map<ManagedPlayer, List<String>> playerToTeammates;

    public ManagedGame(Game game) {
        name = game.getName();
        hasEnded = game.isHasEnded();
        hasWinner = game.hasWinner();
        vpGoalReached = game.getPlayers().values().stream().anyMatch(player -> player.getTotalVictoryPoints() >= game.getVp());
        fowMode = game.isFowMode();
        communityMode = game.isCommunityMode();
        factionReactMode = game.isBotFactionReacts();
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

        players = game.getPlayers().values().stream().map(p -> GameManager.addOrMergePlayer(this, p)).collect(toUnmodifiableSet());
        playerToIsReal = game.getPlayers().values().stream().collect(toUnmodifiableMap(p -> getPlayer(p.getUserID()), Player::isRealPlayer));
        playerToTotalTurns = game.getRealPlayers().stream().collect(toUnmodifiableMap(p -> getPlayer(p.getUserID()), Player::getNumberTurns));
        playerToTurnTime = game.getRealPlayers().stream().collect(toUnmodifiableMap(p -> getPlayer(p.getUserID()), Player::getTotalTurnTime));
        playerToExpectedHitsTimes10 = game.getRealPlayers().stream().collect(toUnmodifiableMap(p -> getPlayer(p.getUserID()), Player::getExpectedHitsTimes10));
        playerToActualHits = game.getRealPlayers().stream().collect(toUnmodifiableMap(p -> getPlayer(p.getUserID()), Player::getActualHits));
//        playerToCommunityRole = !communityMode ? null :
//            game.getRealPlayers().stream()
//                .filter(p -> p.getRoleForCommunity() != null)
//                .collect(toUnmodifiableMap(p -> getPlayer(p.getUserID()), Player::getRoleForCommunity));
//        Map<ManagedPlayer, List<String>> playerToTeammates = game.getPlayers().values().stream()
//            .filter(p -> isNotEmpty(p.getTeamMateIDs()))
//            .collect(toUnmodifiableMap(p -> getPlayer(p.getUserID()), Player::getTeamMateIDs));
//        this.playerToTeammates = playerToTeammates.isEmpty() ? null : playerToTeammates;
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

    public List<ManagedPlayer> getRealPlayers() {
        return playerToIsReal.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
    }

    public List<String> getPlayerIds() {
        return players.stream().map(ManagedPlayer::getId).toList();
    }

    public boolean matches(Game game) {
        return name.equals(game.getName()) && lastModifiedDate == game.getLastModifiedDate();
    }

    public Game getGame() {
        return GameManager.getGame(name);
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
