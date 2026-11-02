package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.morpha;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.Mapper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class MorphaBreakthroughHandler {
    private static final String METAMORPHIC = "morphabt";
    private static final String DRAW_METAMORPHIC = "drawMetamorphicActionCard";
    private static final String DRAW_COUNT = "morphaMetamorphicDrawCount_";

    public static void addMetamorphicCombatButton(List<Button> buttons, Game game, Player player) {
        if (!player.hasUnlockedBreakthrough(METAMORPHIC) || getDrawCount(game, player) >= 2) return;
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + DRAW_METAMORPHIC,
                "Draw Action Card to Metamorphic (On Win)",
                FactionEmojis.morpha));
    }

    @ButtonHandler(DRAW_METAMORPHIC)
    public static void drawMetamorphicActionCard(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnlockedBreakthrough(METAMORPHIC) || getDrawCount(game, player) >= 2) return;
        String actionCardId = game.drawActionCardToMetamorphic();
        if (actionCardId == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "The action card deck is empty.");
            return;
        }
        game.setStoredValue(DRAW_COUNT + player.getFaction(), Integer.toString(getDrawCount(game, player) + 1));
        ActionCardModel actionCard = Mapper.getActionCard(actionCardId);
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : game.getMainGameChannel(),
                player.getRepresentationNoPing() + " drew _" + actionCard.getName() + "_ and placed it on "
                        + FactionEmojis.morpha + " _Metamorphic_.");
    }

    public static void clearMetamorphicDrawCount(Game game, Player player) {
        game.removeStoredValue(DRAW_COUNT + player.getFaction());
    }

    private static int getDrawCount(Game game, Player player) {
        try {
            return Integer.parseInt(game.getStoredValue(DRAW_COUNT + player.getFaction()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
