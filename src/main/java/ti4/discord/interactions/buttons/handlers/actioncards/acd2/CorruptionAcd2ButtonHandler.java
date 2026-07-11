package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.objectives.RevealPublicObjectiveService;

@UtilityClass
class CorruptionAcd2ButtonHandler {

    @ButtonHandler("resolveCorruption")
    public static void resolveCorruption(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> entry :
                game.getRevealedPublicObjectives().entrySet()) {
            String poId = entry.getKey();
            int stage = corruptionStageOf(poId);
            if (stage == 0) {
                continue;
            }
            PublicObjectiveModel po = Mapper.getPublicObjective(poId);
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "resolveCorruptionStep2_" + entry.getValue(),
                    po.getName(),
                    stage == 1 ? CardEmojis.Public1 : CardEmojis.Public2));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + ", there are no revealed public objectives to resolve _Corruption_ on.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", choose the public objective that was just revealed. You will look at the top card of the deck that matches it.",
                buttons);
    }

    @ButtonHandler("resolveCorruptionStep2_")
    public static void resolveCorruptionStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Integer identifier = parseCorruptionInt(buttonID.replace("resolveCorruptionStep2_", ""));
        String revealedId = identifier == null ? null : corruptionRevealedObjectiveById(game, identifier);
        int stage = revealedId == null ? 0 : corruptionStageOf(revealedId);
        if (revealedId == null || stage == 0) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + ", could not find that public objective for _Corruption_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String topId = corruptionTopOfDeck(game, stage);
        if (topId == null) {
            game.shuffleObjectiveDeck(stage);
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + ", the stage " + stage
                            + " public objective deck is empty, so there is no card to look at. The deck has been shuffled.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        PublicObjectiveModel topCard = Mapper.getPublicObjective(topId);
        PublicObjectiveModel revealed = Mapper.getPublicObjective(revealedId);

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "resolveCorruptionReplace_" + identifier,
                "Replace with \"" + topCard.getName() + "\"",
                stage == 1 ? CardEmojis.Public1 : CardEmojis.Public2));
        buttons.add(Buttons.red(
                player.factionButtonChecker() + "resolveCorruptionKeep_" + stage,
                "Keep \"" + revealed.getName() + "\""));

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageEmbedsToCardsInfoThread(
                player,
                player.getRepresentationUnfogged() + ", this is the top card of the stage " + stage
                        + " public objective deck. You may replace \"" + revealed.getName()
                        + "\" with it for _Corruption_. Either way, the deck will be shuffled afterward.",
                List.of(topCard.getRepresentationEmbed()));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose how to resolve _Corruption_.",
                buttons);
    }

    @ButtonHandler("resolveCorruptionReplace_")
    public static void resolveCorruptionReplace(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Integer identifier = parseCorruptionInt(buttonID.replace("resolveCorruptionReplace_", ""));
        String revealedId = identifier == null ? null : corruptionRevealedObjectiveById(game, identifier);
        int stage = revealedId == null ? 0 : corruptionStageOf(revealedId);
        if (revealedId == null || stage == 0 || corruptionTopOfDeck(game, stage) == null) {
            if (stage == 1 || stage == 2) {
                game.shuffleObjectiveDeck(stage);
            }
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + ", could not replace the objective for _Corruption_ (the deck may be empty). The deck has been shuffled.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        PublicObjectiveModel oldPo = Mapper.getPublicObjective(revealedId);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getFactionEmojiOrColor() + " resolved _Corruption_, returning \"" + oldPo.getName()
                        + "\" to the deck and revealing the next stage " + stage
                        + " public objective in its place. The deck has been shuffled.");

        // Reveal the peeked card from the deck (like Incentive Program), then return the replaced objective and
        // shuffle.
        if (stage == 1) {
            RevealPublicObjectiveService.revealS1FromDeck(game, event);
        } else {
            RevealPublicObjectiveService.revealS2FromDeck(game, event);
        }
        game.shuffleObjectiveBackIntoDeck(identifier);
    }

    @ButtonHandler("resolveCorruptionKeep_")
    public static void resolveCorruptionKeep(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Integer stage = parseCorruptionInt(buttonID.replace("resolveCorruptionKeep_", ""));
        if (stage != null && (stage == 1 || stage == 2)) {
            game.shuffleObjectiveDeck(stage);
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getFactionEmojiOrColor()
                        + " resolved _Corruption_ without replacing the revealed objective. The public objective deck has been shuffled.");
    }

    // The top card of the deck itself (not the peekable upcoming objectives), matching the deck-based reveal below.
    private static String corruptionTopOfDeck(Game game, int stage) {
        List<String> deck = stage == 1 ? game.getPublicObjectives1() : game.getPublicObjectives2();
        return deck == null || deck.isEmpty() ? null : deck.getFirst();
    }

    private static int corruptionStageOf(String poId) {
        if (Mapper.getPublicObjectivesStage1().containsKey(poId)) {
            return 1;
        }
        if (Mapper.getPublicObjectivesStage2().containsKey(poId)) {
            return 2;
        }
        return 0;
    }

    private static String corruptionRevealedObjectiveById(Game game, int identifier) {
        for (Map.Entry<String, Integer> entry :
                game.getRevealedPublicObjectives().entrySet()) {
            if (entry.getValue() != null && entry.getValue() == identifier) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Integer parseCorruptionInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
