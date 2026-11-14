package ti4.service.megagame;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import ti4.discord.JdaService;
import ti4.game.Game;

@UtilityClass
public class MegagameGmAccess {

    public static final String GM_ROLE_NAME = "pbd100three GM";
    private static final List<String> GM_ROLE_NAMES = List.of(GM_ROLE_NAME, "bothelper");
    private static final List<String> SEATLESS_GM_COMPONENT_PREFIXES =
            List.of(MegagameGmPanelService.PREFIX, "offerDeckButtons", "showDeck_", "showTextOfDeck_", "showObjInfo_");

    public static boolean hasGmRole(Member member) {
        return member != null
                && member.getRoles().stream()
                        .anyMatch(role -> GM_ROLE_NAMES.stream().anyMatch(role.getName()::equalsIgnoreCase));
    }

    public static boolean canUseGmTools(Member member) {
        return hasGmRole(member)
                || (member != null && member.getRoles().stream().anyMatch(JdaService.bothelperRoles::contains));
    }

    public static boolean allowsSeatlessGm(Game game, Member member, String componentId) {
        return game != null
                && MegagameGroupService.hasTag(game)
                && !game.isFowMode()
                && canUseGmTools(member)
                && SEATLESS_GM_COMPONENT_PREFIXES.stream().anyMatch(componentId::startsWith);
    }

    public static String gmMention(Guild guild) {
        if (guild == null) {
            return "@" + GM_ROLE_NAME;
        }
        return guild.getRolesByName(GM_ROLE_NAME, true).stream()
                .findFirst()
                .map(Role::getAsMention)
                .orElse("@" + GM_ROLE_NAME);
    }
}
