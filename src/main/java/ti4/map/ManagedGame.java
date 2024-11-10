package ti4.map;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import ti4.AsyncTI4DiscordBot;

@Getter
public class ManagedGame {

    private final String name;
    private final boolean hasEnded;
    private final boolean hasWinner;
    private final boolean fowMode;
    private final long lastModifiedDate;
    private final String creationDate;
    private final String activePlayerId;
    private final long lastActivePlayerChange;
    private final long endedDate;
    private final String guildId;
    // TODO: Better to have just ids?
    private final TextChannel mainGameChannel;
    private final TextChannel actionsChannel;
    private final TextChannel tableTalkChannel;
    private final List<ManagedPlayer> players;
    private final Map<String, String> playerIdToFaction; // TODO unsure if keeping
    //private final Map<String, String> playerIdToColor; // TODO unsure if keeping
    private final Map<String, Integer> playerIdToTotalTurns; // TODO unsure if keeping
    private final Map<String, Long> playerIdToTurnTime; // TODO unsure if keeping
    private final String winningPlayerId;
    // game.getGameModeText ?// TODO unsure if keeping

    public ManagedGame(Game game) {
        name = game.getName();
        hasEnded = game.isHasEnded();
        hasWinner = game.hasWinner();
        fowMode = game.isFowMode();
        lastModifiedDate = game.getLastModifiedDate();
        creationDate = game.getCreationDate();
        activePlayerId = game.getActivePlayerID();
        lastActivePlayerChange = game.getLastActivePlayerChange() == null ? 0 : game.getLastActivePlayerChange().getTime();
        endedDate = game.getEndedDate();
        guildId = game.getGuildId();
        mainGameChannel = game.getMainGameChannel();
        actionsChannel = game.getActionsChannel();
        tableTalkChannel = game.getTableTalkChannel();

        players = game.getRealPlayers().stream().map(player -> GameManager.addOrMergePlayer(this, player)).toList();
        winningPlayerId = game.getWinner().map(Player::getUserID).orElse(null);
        playerIdToFaction = game.getRealPlayers().stream().collect(Collectors.toMap(Player::getUserID, Player::getFaction));
        //playerIdToColor = game.getRealPlayers().stream().collect(Collectors.toMap(Player::getUserID, Player::getColor));
        playerIdToTotalTurns = game.getRealPlayers().stream().collect(Collectors.toMap(Player::getUserID, Player::getNumberTurns));
        playerIdToTurnTime = game.getRealPlayers().stream().collect(Collectors.toMap(Player::getUserID, Player::getTotalTurnTime));
    }

    public boolean matches(Game game) {
        return name.equals(game.getName()) && lastModifiedDate == game.getLastModifiedDate();
    }

    @Nullable
    public ManagedPlayer getManagedPlayer(String id) {
        return players.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
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
        Role role = getGameRole();
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

    private Role getGameRole() {
        if (guildId == null) {
            return null;
        }
        var guild = AsyncTI4DiscordBot.jda.getGuildById(guildId);
        if (guild == null) {
            return null;
        }
        return guild.getRoles().stream().filter(role -> getName().equals(role.getName().toLowerCase())).findFirst().orElse(null);
    }
}
