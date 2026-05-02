package ti4.discord.interactions.buttons.handlers.faction.base.jolnar;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.PatternHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.tech.ListTechService;

@UtilityClass
class JolnarButtonHandler {

    @ButtonHandler("jnHeroSwapOut_")
    public static void resolveAJolNarSwapStep1(
            Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        List<Button> buttons = getJolNarHeroSwapInOptions(player, game, buttonID);
        String message = player.getRepresentationUnfogged() + ", please choose the technology you wish to acquire.";
        if (game.getPhaseOfGame().toLowerCase().contains("status")) {
            ButtonHelper.deleteMessage(event);
            message += " It must contain exactly 1 more prerequisite than the technology you are swapping out.";
        } else {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    private static List<Button> getJolNarHeroSwapInOptions(Player player, Game game, String buttonID) {
        String tech = buttonID.replace("jnHeroSwapOut_", "");
        TechnologyModel techM = Mapper.getTech(tech);
        List<TechnologyModel> techs = new ArrayList<>();
        for (TechnologyModel.TechnologyType type : techM.getTypes())
            techs.addAll(ListTechService.getAllTechOfAType(game, type.toString(), player));
        return ListTechService.getTechButtons(techs, player, tech);
    }

    @ButtonHandler("swapTechs_")
    public static void resolveAJolNarSwapStep2(Player player, String buttonID, ButtonInteractionEvent event) {
        buttonID = buttonID.replace("swapTechs__", "");
        String techOut = PatternHelper.DOUBLE_UNDERSCORE_PATTERN.split(buttonID)[0];
        String techIn = PatternHelper.DOUBLE_UNDERSCORE_PATTERN.split(buttonID)[1];
        TechnologyModel techM1 = Mapper.getTech(techOut);
        TechnologyModel techM2 = Mapper.getTech(techIn);
        player.addTech(techIn);
        player.removeTech(techOut);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " swapped the technology _" + techM1.getName() + "_ for _" + techM2.getName()
                        + "_.");
        ButtonHelper.deleteMessage(event);
    }
}
