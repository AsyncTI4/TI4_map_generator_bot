package ti4.buttons.handlers.relics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.ComponentActionService;
import ti4.service.MessageService;
import ti4.service.RelicService;

@Component
public class DynamicRelicButtonHandler {

    private final RelicService relicService;
    private final ComponentActionService componentActionService;
    private final MessageService messageService;

    @Autowired
    public DynamicRelicButtonHandler(RelicService relicService,
                                     ComponentActionService componentActionService,
                                     MessageService messageService) {
        this.relicService = relicService;
        this.componentActionService = componentActionService;
        this.messageService = messageService;
    }

    @ButtonHandler("drawRelicFromFrag")
    public void drawRelicFromFrag(ButtonInteractionEvent event, Player player, Game game) {
        relicService.drawRelicAndNotify(player, event, game);
        componentActionService.serveNextComponentActionButtons(event, game, player);
        messageService.deleteMessage(event);
    }
}