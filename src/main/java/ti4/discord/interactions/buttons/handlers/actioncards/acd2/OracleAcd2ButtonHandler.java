package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class OracleAcd2ButtonHandler {

    @ButtonHandler("resolveOracle")
    public static void resolveOracle(Player player, Game game, ButtonInteractionEvent event) {
        List<MessageEmbed> embeds = new ArrayList<>();
        game.peekAtAllUnrevealedPublicObjectives(player);

        for (String objectiveId : game.getPublicObjectives1Peekable()) {
            embeds.add(Mapper.getPublicObjective(objectiveId).getRepresentationEmbed());
        }

        for (String objectiveId : game.getPublicObjectives2Peekable()) {
            embeds.add(Mapper.getPublicObjective(objectiveId).getRepresentationEmbed());
        }

        for (String secretId : game.peekAtSecrets(5)) {
            embeds.add(Mapper.getSecretObjective(secretId).getRepresentationEmbed(true));
        }

        MessageHelper.sendMessageEmbedsToCardsInfoThread(
                player,
                "Showing all unrevealed public objectives and the top 5 secret objectives from the deck.",
                embeds);
        Collections.shuffle(game.getSecretObjectives());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Sent _Oracle_ results to " + player.getFactionEmojiOrColor()
                        + " `#cards-info` thread and shuffled the secret objective deck.");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
