package ti4.discord.interactions.buttons.handlers.faction.discordantstars.kollect;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
class KollectAgentButtonHandler {

    @ButtonHandler("kolleccAgentResStep2_")
    public static void kolleccAgentResStep2(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String type = buttonID.split("_")[1];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            String cardID = game.drawExplore(type);
            ExploreModel card = Mapper.getExplore(cardID);
            sb.append(card.textRepresentation()).append(System.lineSeparator());
            String cardType = card.getResolution();
            if (Constants.FRAGMENT.equalsIgnoreCase(cardType)) {
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
}
