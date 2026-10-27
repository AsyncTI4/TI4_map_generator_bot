package ti4.discord.interactions.buttons.handlers.strategycard;

import lombok.experimental.UtilityClass;
import ti4.discord.interactions.listeners.context.ButtonContext;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.helpers.ButtonHelper;
import ti4.service.strategycard.PlayStrategyCardService;

@UtilityClass
class StrategicActionButtonHandler {

    @ButtonHandler("strategicAction_")
    public static void playStrategyCard(ButtonContext context) {
        int scNum = Integer.parseInt(context.getButtonID().replace("strategicAction_", ""));
        PlayStrategyCardService.playSC(
                context.getEvent(), scNum, context.getGame(), context.getMainGameChannel(), context.getPlayer());
        ButtonHelper.deleteMessage(context.getEvent());
    }
}
