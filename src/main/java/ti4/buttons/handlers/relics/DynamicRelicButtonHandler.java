package ti4.buttons.handlers.relics;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.ComponentActionService;
import ti4.service.MessageService;
import ti4.service.RelicService;

public class DynamicRelicButtonHandler {

    private final RelicService relicService;
    private final ComponentActionService componentActionService;
    private final MessageService messageService;

    public DynamicRelicButtonHandler(RelicService relicService,
                                     ComponentActionService componentActionService,
                                     MessageService messageService) {
        this.relicService = relicService;
        this.componentActionService = componentActionService;
        this.messageService = messageService;
    }

    public void drawRelicFromFrag(ButtonInteractionEvent event, Player player, Game game) {
        relicService.drawRelicAndNotify(player, event, game);
        componentActionService.serveNextComponentActionButtons(event, game, player);
        messageService.deleteMessage(event);
    }
}