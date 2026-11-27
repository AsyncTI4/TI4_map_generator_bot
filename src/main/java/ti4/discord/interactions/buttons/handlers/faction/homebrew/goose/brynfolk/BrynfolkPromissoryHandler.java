package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.brynfolk;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.AgendaSummaryHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class BrynfolkPromissoryHandler {
    private static final String ALTHING_BARRISTERS = "aopnbrynfolk";
    private static final String BLOT_DEBT_POOL = "Blot";
    private static final String USE_ALTHING_BARRISTERS = "useAlthingBarristers";

    public static List<Button> getAgendaVotingButtons(Player player, Game game) {
        if (!player.hasPlayablePromissoryInHand(ALTHING_BARRISTERS)) {
            return List.of();
        }
        return List.of(Buttons.blue(
                player.factionButtonChecker() + USE_ALTHING_BARRISTERS,
                "Use Althing Barristers (" + getBlotVoteValue(game) + ")",
                FactionEmojis.brynfolk));
    }

    @ButtonHandler(USE_ALTHING_BARRISTERS)
    public static void useAlthingBarristers(ButtonInteractionEvent event, Game game, Player player) {
        Player owner = game.getPNOwner(ALTHING_BARRISTERS);
        if (owner == null || !player.hasPlayablePromissoryInHand(ALTHING_BARRISTERS)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        int votes = getBlotVoteValue(game);
        player.addSpentThing("althingBarristers_" + votes);
        boolean tokenPlaced = BrynfolkAbilityHandler.hasBlotCapacity(owner);
        if (tokenPlaced) {
            game.setDebtPoolIcon(BLOT_DEBT_POOL, FactionEmojis.brynfolk.emojiString());
            owner.addDebtTokens(player.getColor(), 1, BLOT_DEBT_POOL);
        }
        player.removePromissoryNote(ALTHING_BARRISTERS);
        owner.setPromissoryNote(ALTHING_BARRISTERS);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
        String result = player.getRepresentationNoPing() + " used _Althing Barristers_ to add " + votes + " vote"
                + (votes == 1 ? "" : "s") + " and returned it to " + owner.getRepresentationNoPing() + ".";
        if (tokenPlaced) {
            result += " They placed 1 control token on Blot.";
        } else {
            result += " Blot has no capacity for another control token.";
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), result);
        String message = AgendaSummaryHelper.getSummaryOfVotes(game, true) + "\n\n"
                + Helper.buildSpentThingsMessageForVoting(player, game, false);
        event.getMessage().editMessage(message).queue(Consumers.nop(), BotLogger::catchRestError);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static boolean isAlthingBarristers(String promissoryNote) {
        return ALTHING_BARRISTERS.equalsIgnoreCase(promissoryNote);
    }

    private static int getBlotVoteValue(Game game) {
        Planet blot = game.getPlanetsInfo().get("blot");
        return blot == null ? 0 : blot.getResources() + blot.getInfluence();
    }
}
