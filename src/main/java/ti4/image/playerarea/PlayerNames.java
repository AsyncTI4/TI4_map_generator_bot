package ti4.image.playerarea;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.ColorModel;
import ti4.model.Mapper;
import ti4.service.user.AFKService;

final class PlayerNames {

    private PlayerNames() {}

    static String factionDisplayName(Player player) {
        StringBuilder text = new StringBuilder();
        if (player.getDisplayName() != null && !"null".equalsIgnoreCase(player.getDisplayName())) {
            text.append("[").append(player.getDisplayName()).append("]");
        } else if (player.getFactionModel() == null) {
            text.append("[").append(StringUtils.capitalize(player.getFaction())).append("]");
        } else {
            text.append("[").append(player.getFactionModel().getShortName()).append("]");
        }

        ColorModel colorModel = Mapper.getColor(player.getColor());
        if (colorModel != null && !text.isEmpty())
            text.append(" (").append(colorModel.getDisplayName()).append(")");
        return text.toString();
    }

    static String discordName(Game game, Player player, String userID) {
        String afk = AFKService.userIsAFK(userID) ? " (AFK)" : "";
        try {
            User user = JdaService.jda.getUserById(userID);
            Member member = game.getGuild().getMember(user);
            if (member == null) {
                member = JdaService.guildPrimary.getMember(user);
            }

            if (member != null) return member.getEffectiveName() + afk;
            return user.getEffectiveName() + afk;
        } catch (Exception _) {
        }
        return player.getUserName() + afk;
    }
}
