package ti4.map;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import ti4.AsyncTI4DiscordBot;

@Getter
public class ManagedGame { // BE CAREFUL ADDING FIELDS TO THIS CLASS, AS IT CAN EASILY BALLOON THE DATA ON THE HEAP BY MEGABYTES PER FIELD

    private final String name;
    private final boolean hasEnded;
    private final boolean hasWinner;
    private final boolean fowMode;
    private final boolean communityMode;
    private final long lastModifiedDate;
    private final String creationDate;
    private final String activePlayerId;
    private final long lastActivePlayerChange;
    private final long endedDate;
    private final Guild guild;
    private final TextChannel mainGameChannel;
    private final TextChannel actionsChannel;
    private final TextChannel tableTalkChannel;
    private final ThreadChannel botMapUpdateThread;
    private final ManagedPlayer winner;
    private final List<ManagedPlayer> players;
    private final Map<ManagedPlayer, String> playerIdToFaction; // TODO unsure if keeping
    private final Map<ManagedPlayer, Integer> playerIdToTotalTurns; // TODO unsure if keeping
    private final Map<ManagedPlayer, Long> playerIdToTurnTime; // TODO unsure if keeping
    private final Map<ManagedPlayer, Integer> playerIdToExpectedHitsTimes10; // TODO unsure if keeping
    private final Map<ManagedPlayer, Integer> playerIdToActualHits; // TODO unsure if keeping

    public ManagedGame(Game game) {
        name = game.getName();
        hasEnded = game.isHasEnded();
        hasWinner = game.hasWinner();
        fowMode = game.isFowMode();
        communityMode = game.isCommunityMode();
        lastModifiedDate = game.getLastModifiedDate();
        creationDate = game.getCreationDate();
        activePlayerId = sanitizeToNull(game.getActivePlayerID());
        lastActivePlayerChange = game.getLastActivePlayerChange() == null ? 0 : game.getLastActivePlayerChange().getTime();
        endedDate = game.getEndedDate();
        guild = game.getGuild();
        mainGameChannel = game.getMainGameChannel();
        actionsChannel = game.getActionsChannel();
        tableTalkChannel = game.getTableTalkChannel();
        botMapUpdateThread = game.getBotMapUpdatesThread();

        players = game.getRealPlayers().stream().map(player -> GameManager.addOrMergePlayer(this, player)).toList();
        var winningPlayerId = game.getWinner().map(Player::getUserID).orElse(null);
        winner = winningPlayerId == null ? null : getManagedPlayer(winningPlayerId);
        playerIdToFaction = game.getRealPlayers().stream().collect(Collectors.toUnmodifiableMap(p -> getManagedPlayer(p.getUserID()), Player::getFaction));
        //playerIdToColor = game.getRealPlayers().stream().collect(Collectors.toMap(Player::getUserID, Player::getColor));
        playerIdToTotalTurns = game.getRealPlayers().stream().collect(Collectors.toUnmodifiableMap(p -> getManagedPlayer(p.getUserID()), Player::getNumberTurns));
        playerIdToTurnTime = game.getRealPlayers().stream().collect(Collectors.toUnmodifiableMap(p -> getManagedPlayer(p.getUserID()), Player::getTotalTurnTime));
        playerIdToExpectedHitsTimes10 = game.getRealPlayers().stream().collect(Collectors.toUnmodifiableMap(p -> getManagedPlayer(p.getUserID()), Player::getExpectedHitsTimes10));
        playerIdToActualHits = game.getRealPlayers().stream().collect(Collectors.toUnmodifiableMap(p -> getManagedPlayer(p.getUserID()), Player::getActualHits));
    }

    private static String sanitizeToNull(String str) {
        if (StringUtils.isBlank(str) || "null".equalsIgnoreCase(str)) {
            return null;
        }
        return str;
    }

    public boolean matches(Game game) {
        return name.equals(game.getName()) && lastModifiedDate == game.getLastModifiedDate();
    }

    @Nullable
    public ManagedPlayer getManagedPlayer(String id) {
        return players.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
    }

    public boolean hasPlayer(String playerId) {
        return players.stream().anyMatch(p -> p.getId().equals(playerId));
    }

    public String getGameNameForSorting() {
        if (getName().startsWith("pbd")) {
            return StringUtils.leftPad(getName(), 10, "0");
        }
        if (getName().startsWith("fow")) {
            return StringUtils.leftPad(getName(), 10, "1");
        }
        return getName();
    }

    public String getPing() {
        Role role = guild == null ? null :
                guild.getRoles().stream().filter(r -> getName().equals(r.getName().toLowerCase())).findFirst().orElse(null);
        if (role != null) {
            return role.getAsMention();
        }
        StringBuilder sb = new StringBuilder(getName()).append(" ");
        for (var player : players) {
            User user = AsyncTI4DiscordBot.jda.getUserById(player.getId());
            if (user != null) sb.append(user.getAsMention()).append(" ");
        }
        return sb.toString();
    }
}
