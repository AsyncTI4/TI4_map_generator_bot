package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.migo;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.logging.BotLogger;

@UtilityClass
public class MigoPromissoryHandler {

    public static final String EXTRACTION_SCIENCE = "bepnmigo";
    private static final String SPEND_EXTRACTION_SCIENCE = "spendExtractionScience_";

    public static boolean hasExtractionScience(Player player, Game game) {
        return player.getPromissoryNotes().containsKey(EXTRACTION_SCIENCE)
                && player != game.getPNOwner(EXTRACTION_SCIENCE);
    }

    public static Button getExtractionScienceSpendButton(Player player, String whatIsItFor) {
        return Buttons.green(
                player.factionButtonChecker() + SPEND_EXTRACTION_SCIENCE + whatIsItFor, "Use Extraction Science");
    }

    @ButtonHandler(SPEND_EXTRACTION_SCIENCE)
    public static void spendExtractionScience(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String whatIsItFor = buttonID.substring(SPEND_EXTRACTION_SCIENCE.length());
        if (!whatIsItFor.contains("tech") || !hasExtractionScience(player, game)) {
            return;
        }

        player.addSpentThing(EXTRACTION_SCIENCE);
        PromissoryNoteHelper.resolvePNPlay(EXTRACTION_SCIENCE, player, game, event);
        event.getMessage()
                .editMessage(Helper.buildSpentThingsMessage(player, game, whatIsItFor))
                .queue(Consumers.nop(), BotLogger::catchRestError);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
