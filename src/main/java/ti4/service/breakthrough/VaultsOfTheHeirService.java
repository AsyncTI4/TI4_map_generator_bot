package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.RelicHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;

@UtilityClass
public class VaultsOfTheHeirService {

    private String vaultsRep() {
        return Mapper.getBreakthrough("mahactbt").getNameRepresentation();
    }

    public void postInitialButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        String message = player.getRepresentation() + " Choose a technology to purge using " + vaultsRep() + ":";
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getTechs()) {
            TechnologyModel model = Mapper.getTech(tech);
            buttons.add(Buttons.red(player.finChecker() + "purgeTechVaults_" + tech, model.getName(), model.getCondensedReqsEmojis(true)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("purgeTechVaults_")
    private static void resolveVaultsOfTheHeir(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String tech = buttonID.replace("purgeTechVaults_", "");
        ButtonHelperHeroes.purgeTech(player, game, event, tech); //deletes message
        RelicHelper.drawRelicAndNotify(player, event, game);
        ButtonHelper.deleteMessage(event);
    }
}
