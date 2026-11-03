package ti4.discord.interactions.buttons.handlers.actioncards.thirtyk;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.actioncards.theodisi.TransitRiderLLButtonHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.thirtyk.ThirtyKAcState;
import ti4.helpers.thirtyk.ThirtyKAgendaRiders;
import ti4.helpers.thirtyk.ThirtyKAgendaRiders.RiderEntry;
import ti4.helpers.thirtyk.ThirtyKDistance;
import ti4.message.MessageHelper;
import ti4.service.fow.PlanetTargetService;

@UtilityClass
public class ThirtyKAgendaAcButtonHandler {

    public static final String SAME = "same";
    public static final String OPPOSITE = "opposite";
    public static final String RIDER_COPY_LIST = "thkAcRiderCopyList_";
    public static final String SUBJECTIVE = "thkAcSubjective_";
    private static final String RIDER_COPY = "thkAcRiderCopy_";
    private static final int SUBJECTIVE_VOTES = 5;

    @ButtonHandler(RIDER_COPY_LIST)
    public static void riderCopyList(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String mode = buttonID.substring(RIDER_COPY_LIST.length());
        String cardName = OPPOSITE.equals(mode) ? "Objection Gambit" : "Hide Within the Fine Print";
        List<Button> buttons = new ArrayList<>();
        for (RiderEntry entry : ThirtyKAgendaRiders.copyableRiders(game.getCurrentAgendaVotes())) {
            String outcome = OPPOSITE.equals(mode)
                    ? ThirtyKAgendaRiders.opposingOutcome(entry.outcome())
                    : entry.outcome().toLowerCase();
            String id = player.factionButtonChecker() + RIDER_COPY + mode + "_" + outcome + "_" + entry.rider();
            if (buttons.stream().anyMatch(button -> id.equals(button.getCustomId()))) continue;
            buttons.add(Buttons.green(id, entry.rider() + " On " + StringUtils.capitalize(outcome)));
        }
        if (buttons.isEmpty()) {
            ThirtyKAcTargets.noTarget(event, player, cardName);
            return;
        }
        ButtonHelper.deleteMessage(event);
        ThirtyKAcTargets.send(
                player,
                player.getRepresentationUnfogged() + ", choose the Rider whose text you follow with _" + cardName
                        + "_.",
                buttons);
    }

    @ButtonHandler(RIDER_COPY)
    public static void riderCopy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String afterPrefix = buttonID.substring(RIDER_COPY.length());
        String mode = StringUtils.substringBefore(afterPrefix, "_");
        String rest = StringUtils.substringAfter(afterPrefix, "_");
        int separator = rest.indexOf('_');
        if (separator < 0) return;
        String outcome = rest.substring(0, separator);
        String rider = rest.substring(separator + 1);
        if (!ThirtyKAgendaRiders.isCopyable(outcome, rider)) return;
        String identifier = game.isFowMode() ? player.getColor() : player.getFaction();
        game.setCurrentAgendaVote(
                outcome,
                ThirtyKAgendaRiders.withEntry(game.getCurrentAgendaVotes().get(outcome), identifier + "_" + rider));
        ThirtyKAcState.markRiderCard(game, player, rider);
        if (ThirtyKAgendaRiders.forbidsVoting(rider)) {
            TransitRiderLLButtonHandler.registerPrediction(game, player);
        }
        if (ThirtyKAgendaRiders.DUMMY_RIDER.equals(rider)) {
            ThirtyKAcState.addDummyRiderCopy(
                    game, player, outcome, OPPOSITE.equals(mode) ? "objectiongambit30k" : "hidewithinthefineprint30k");
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : game.getMainGameChannel(),
                FoWHelper.actorOrAnon(game, player, "A player") + " invokes an obscure clause and follows the text"
                        + " of a _" + rider + "_ on \"" + StringUtils.capitalize(outcome) + "\".");
    }

    public static List<Button> subjectiveJusticeButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player voter : game.getRealPlayers()) {
            if (voter == player || votedOutcome(game, voter) == null) continue;
            if (!game.isFowMode() && !ThirtyKDistance.homesMoreThanFiveApart(game, player, voter)) continue;
            buttons.add(FoWHelper.fogSafeTargetButton(
                    player.factionButtonChecker() + SUBJECTIVE + voter.getColor(), "gray", voter));
        }
        return buttons;
    }

    @ButtonHandler(SUBJECTIVE)
    public static void subjectiveJustice(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player voter = ThirtyKAcTargets.target(game, buttonID, SUBJECTIVE);
        String outcome = voter == null ? null : votedOutcome(game, voter);
        if (outcome == null || voter == player || !ThirtyKDistance.homesMoreThanFiveApart(game, player, voter)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        String identifier = game.isFowMode() ? player.getColor() : player.getFaction();
        game.setCurrentAgendaVote(
                outcome,
                ThirtyKAgendaRiders.withEntry(
                        game.getCurrentAgendaVotes().get(outcome), identifier + "_" + SUBJECTIVE_VOTES));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", seen from afar the verdict looks clear: you cast "
                        + SUBJECTIVE_VOTES + " additional votes for \"" + outcome + "\" (_Subjective Justice_).");
    }

    private static String votedOutcome(Game game, Player voter) {
        List<String> outcomes = ThirtyKAgendaRiders.numericVoteOutcomes(
                game.getCurrentAgendaVotes(),
                Set.of(voter.getColor().toLowerCase(), voter.getFaction().toLowerCase()));
        return outcomes.isEmpty() ? null : outcomes.getLast();
    }
}
