package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.emoji.ExploreEmojis;

@UtilityClass
public class OverlookedFindingsLLButtonHandler {
    private static final String RESOLVE_OVERLOOKED_FINDINGS = "resolveOverlookedFindings";
    private static final String CHOOSE_OVERLOOKED_FINDINGS_TRAIT = "chooseOverlookedFindingsTrait_";
    private static final String SELECT_OVERLOOKED_FINDINGS = "selectOverlookedFindings_";

    @ButtonHandler(RESOLVE_OVERLOOKED_FINDINGS)
    public static void resolveOverlookedFindings(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + CHOOSE_OVERLOOKED_FINDINGS_TRAIT + Constants.CULTURAL,
                        "Cultural",
                        ExploreEmojis.Cultural),
                Buttons.green(
                        player.factionButtonChecker() + CHOOSE_OVERLOOKED_FINDINGS_TRAIT + Constants.HAZARDOUS,
                        "Hazardous",
                        ExploreEmojis.Hazardous),
                Buttons.green(
                        player.factionButtonChecker() + CHOOSE_OVERLOOKED_FINDINGS_TRAIT + Constants.INDUSTRIAL,
                        "Industrial",
                        ExploreEmojis.Industrial));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose the explored planet's trait for _Overlooked Findings_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CHOOSE_OVERLOOKED_FINDINGS_TRAIT)
    public static void chooseOverlookedFindingsTrait(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String trait = buttonID.substring(CHOOSE_OVERLOOKED_FINDINGS_TRAIT.length());
        sendExploreDiscardButtons(event, game, player, trait);
    }

    @ButtonHandler(SELECT_OVERLOOKED_FINDINGS)
    public static void selectOverlookedFindings(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(SELECT_OVERLOOKED_FINDINGS.length());
        int separator = payload.indexOf('|');
        if (separator < 1) return;
        String trait = payload.substring(0, separator);
        List<Button> buttons = getOverlookedFindingsButtons(game, player, trait);
        String buttonPrefix = player.factionButtonChecker() + SELECT_OVERLOOKED_FINDINGS + trait + "|";
        String message = getOverlookedFindingsMessage(player, trait);

        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        String exploreId = payload.substring(separator + 1);
        ExploreModel explore = Mapper.getExplore(exploreId);

        if (!game.getExploreDiscard(trait).contains(exploreId) || explore == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.getAllExploreDiscard().remove(exploreId);
        game.getAllExplores().addFirst(exploreId);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed _" + explore.getName() + "_ on top of the "
                        + trait.toLowerCase() + " exploration deck with _Overlooked Findings_.");

        ButtonHelper.deleteMessage(event);
    }

    private static void sendExploreDiscardButtons(
            ButtonInteractionEvent event, Game game, Player player, String trait) {
        List<Button> buttons = getOverlookedFindingsButtons(game, player, trait);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no " + trait + " exploration cards in the discard pile.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                getOverlookedFindingsMessage(player, trait),
                NewStuffHelper.buttonPagination(
                        buttons, player.factionButtonChecker() + SELECT_OVERLOOKED_FINDINGS + trait + "|", 0));
    }

    private static String getOverlookedFindingsMessage(Player player, String trait) {
        return player.getRepresentationNoPing() + ", choose a " + trait.toLowerCase()
                + " exploration card from the discard pile to place on top of its deck with _Overlooked Findings_.";
    }

    private static List<Button> getOverlookedFindingsButtons(Game game, Player player, String trait) {
        List<Button> buttons = new ArrayList<>();
        String prefix = player.factionButtonChecker() + SELECT_OVERLOOKED_FINDINGS + trait + "|";
        if (!List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL)
                .contains(trait)) return buttons;
        for (String exploreId : game.getExploreDiscard(trait)) {
            ExploreModel explore = Mapper.getExplore(exploreId);
            if (explore != null) {
                buttons.add(Buttons.gray(
                        prefix + exploreId,
                        "Place " + explore.getName() + " on Top",
                        ExploreEmojis.getTraitEmoji(trait)));
            }
        }
        return buttons;
    }
}
