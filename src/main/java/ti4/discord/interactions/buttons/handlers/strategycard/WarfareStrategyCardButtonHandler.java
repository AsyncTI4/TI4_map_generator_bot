package ti4.discord.interactions.buttons.handlers.strategycard;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;

@UtilityClass
class WarfareStrategyCardButtonHandler {

    @ButtonHandler("primaryOfWarfare")
    public static void primaryOfWarfare(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "warfare");
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannelWithButtons(
                channel, "Please choose which system you wish to remove your command token from.", buttons);
    }

    @ButtonHandler("primaryOfTeWarfare")
    public static void primaryOfTeWarfare(Player player) {
        StrategyCardModel model = Mapper.getStrategyCard("te6warfare");
        if (model == null) return;

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue(
                player.factionButtonChecker() + "redistributeCCButtons_deleteThisButton",
                "Redistribute Command Tokens"));
        buttons.add(Buttons.red(player.factionButtonChecker() + "beginTacticalTeWarfare", "Perform Tactical Action"));
        String message =
                "Use the buttons to resolve the primary of **Warfare**. Redistributing your command tokens is optional.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("beginTacticalTeWarfare")
    public static void beginTacticalTeWarfare(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        ButtonHelperTacticalAction.resetStoredValuesForTacticalAction(game);
        game.setWarfareAction(true);
        ButtonHelperTacticalAction.beginTacticalAction(game, player);
    }
}
