package ti4.service.game;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;
import ti4.service.JdaService;
import ti4.map.persistence.ManagedGame;

@UtilityClass
public class ManagedGameService {

    public String getGameNameForSorting(ManagedGame game) {
        String gameName = game.getName();
        if (gameName.startsWith("pbd")) {
            return StringUtils.leftPad(gameName, 10, "0");
        }
        if (gameName.startsWith("fow")) {
            return StringUtils.leftPad(gameName, 10, "1");
        }
        return gameName;
    }

    public String getPingAllPlayers(ManagedGame game) {
        Role role = game.getGuild() == null
                ? null
                : game.getGuild().getRoles().stream()
                        .filter(r -> game.getName().equals(r.getName().toLowerCase()))
                        .findFirst()
                        .orElse(null);
        if (role != null) {
            return role.getAsMention();
        }
        StringBuilder sb = new StringBuilder(game.getName()).append(" ");
        for (var player : game.getPlayers()) {
            User user = JdaService.jda.getUserById(player.getId());
            if (user != null) sb.append(user.getAsMention()).append(" ");
        }
        return sb.toString();
    }
}
