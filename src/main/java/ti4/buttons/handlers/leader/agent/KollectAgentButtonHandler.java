package ti4.buttons.handlers.leader.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
class KollectAgentButtonHandler {

    @ButtonHandler("kolleccAgentRes_")
    public static void kolleccAgentResStep1(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        String msg2 = player.getFactionEmojiOrColor() + " selected "
                + p2.getFactionEmojiOrColor() + " as user of "
                + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Captain Dust, the Kollecc" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                + " agent.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        List<Button> buttons = getKolleccAgentButtons(game, p2);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), p2.getRepresentationUnfogged() + " use buttons to resolve", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("kolleccAgentResStep2_")
    public static void kolleccAgentResStep2(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String type = buttonID.split("_")[1];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            String cardID = game.drawExplore(type);
            ExploreModel card = Mapper.getExplore(cardID);
            sb.append(card.textRepresentation()).append(System.lineSeparator());
            String cardType = card.getResolution();
            if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
                sb.append(player.getRepresentationUnfogged()).append(" Gained relic fragment\n");
                player.addFragment(cardID);
                game.purgeExplore(cardID);
            }
        }
        CommanderUnlockCheckService.checkPlayer(player, "kollecc");
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannel(channel, sb.toString());
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getKolleccAgentButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        Set<String> types = ButtonHelper.getTypesOfPlanetPlayerHas(game, player);
        for (String type : types) {
            if ("industrial".equals(type)) {
                buttons.add(Buttons.green("kolleccAgentResStep2_industrial", "Explore Industrials X 2"));
            }
            if ("cultural".equals(type)) {
                buttons.add(Buttons.blue("kolleccAgentResStep2_cultural", "Explore Culturals X 2"));
            }
            if ("hazardous".equals(type)) {
                buttons.add(Buttons.red("kolleccAgentResStep2_hazardous", "Explore Hazardous X 2"));
            }
        }
        return buttons;
    }
}
