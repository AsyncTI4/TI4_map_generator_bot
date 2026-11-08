package ti4.service.megagame;

import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.helpers.Constants;

@UtilityClass
public class MegagameActionCardRules {

    private static final Set<String> PRESET_ONLY = Set.of("coup", "crisis", "extremeduress");

    public static String presetOnlyRefusal(
            GenericInteractionCreateEvent event, Game game, String acID, String actionCardTitle) {
        if (!MegagameGroupService.isActive(game) || !PRESET_ONLY.contains(acID) || !isDirectPlay(event)) {
            return null;
        }
        return "In this megagame, _" + actionCardTitle
                + "_ can only be played through the pre-assign buttons in your cards info thread.";
    }

    private static boolean isDirectPlay(GenericInteractionCreateEvent event) {
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            return StringUtils.contains(buttonEvent.getComponentId(), Constants.AC_PLAY_FROM_HAND);
        }
        return event instanceof SlashCommandInteractionEvent slashEvent && "ac".equals(slashEvent.getName());
    }
}
