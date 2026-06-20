package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;

@UtilityClass
class FlawlessStrategyAcd2ButtonHandler {

    @ButtonHandler("resolveFlawlessStrategy")
    public static void resolveFlawlessStrategy(Player player, ButtonInteractionEvent event) {
        List<Button> scButtons = new ArrayList<>();
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        if (player.getSCs().contains(2)) {
            scButtons.add(Buttons.green("diploRefresh2", "Ready 2 Planets"));
        }
        if (player.getSCs().contains(3)) {
            scButtons.add(Buttons.gray("draw2 AC", "Draw 2 Action Cards", CardEmojis.ActionCard));
        }
        if (player.getSCs().contains(4)) {
            scButtons.add(Buttons.green("construction_spacedock", "Place 1 space dock", UnitEmojis.spacedock));
            scButtons.add(Buttons.green("construction_pds", "Place 1 PDS", UnitEmojis.pds));
        }
        if (player.getSCs().contains(5)) {
            scButtons.add(Buttons.gray("sc_refresh", "Replenish Commodities", MiscEmojis.comm));
        }
        if (player.getSCs().contains(6)) {
            scButtons.add(Buttons.green("warfareBuild", "Build At Home"));
        }
        if (player.getSCs().contains(7)) {
            scButtons.add(Buttons.GET_A_TECH);
        }
        if (player.getSCs().contains(8)) {
            scButtons.add(Buttons.gray("non_sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective));
        }
        scButtons.add(Buttons.red("deleteButtons", "Done resolving"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), player.getRepresentation() + ", use the buttons to resolve.", scButtons);
    }
}
