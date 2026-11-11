package ti4.service.megagame;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.info.HandCheckService;
import ti4.service.info.SecretObjectiveInfoService;

@UtilityClass
public class MegagameGmPanelService {

    public static final String PREFIX = "megaGm";
    private static final String HANDS = PREFIX + "Hands_";
    private static final String CARDS_INFO = PREFIX + "CardsInfo_";

    private static final List<Button> HAND_CHECK_BUTTONS = List.of(
            Buttons.gray(HANDS + HandCheckService.SABOTAGE, "Sabotages", CardEmojis.ActionCard),
            Buttons.gray(HANDS + HandCheckService.WHENS, "Whens/Afters", CardEmojis.ActionCard),
            Buttons.gray(HANDS + HandCheckService.DEADLY, "Deadlies/Briberies", CardEmojis.ActionCard),
            Buttons.gray(HANDS + HandCheckService.CONFUSING, "Confusing/Confounding", CardEmojis.ActionCard),
            Buttons.gray(HANDS + HandCheckService.SECRET, "Unscored SOs", CardEmojis.SecretObjective),
            Buttons.gray(HANDS + HandCheckService.ACS, "All ACs", CardEmojis.ActionCard),
            Buttons.gray(HANDS + HandCheckService.PNS, "All PNs", CardEmojis.PN),
            Buttons.gray(HANDS + HandCheckService.RELICS, "All Relics", ExploreEmojis.Relic),
            Buttons.gray(HANDS + HandCheckService.LEADERS, "All Leaders"));

    public static boolean isAvailable(Game game) {
        return MegagameGroupService.hasTag(game) && !game.isFowMode();
    }

    public static void sendPanel(InteractionHook hook, Game game, Member member) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.REFRESH_MAP);
        buttons.add(Buttons.SHOW_DECKS);
        buttons.add(Buttons.green(HANDS, "Check Player Hands for..."));
        buttons.add(Buttons.green(CARDS_INFO, "Player Cards Info..."));
        String content = "Megagame GM Buttons (only you can see this)";
        if (member != null && game.getPlayer(member.getId()) != null) {
            buttons.add(Buttons.EDIT_SUMMARIES);
        } else {
            content += "\n-# _Edit Summaries_ needs you to be in the game.";
        }
        sendEphemeral(hook, content, buttons);
    }

    @ButtonHandler(value = HANDS, save = false)
    public static void checkPlayerHands(ButtonInteractionEvent event, String buttonID, Game game) {
        if (!mayUse(event, game)) return;
        List<String> reports = HandCheckService.reports(game, buttonID.replace(HANDS, ""));
        String prompt = reports.isEmpty() ? "Please choose what to look for:" : "Check something else:";
        sendEphemeral(event.getHook(), joinedWith(reports, prompt), new ArrayList<>(HAND_CHECK_BUTTONS));
    }

    @ButtonHandler(value = CARDS_INFO, save = false)
    public static void playerCardsInfo(ButtonInteractionEvent event, String buttonID, Game game) {
        if (!mayUse(event, game)) return;
        String faction = buttonID.replace(CARDS_INFO, "");
        Player target = faction.isEmpty() ? null : game.getPlayerFromColorOrFaction(faction);
        if (target == null) {
            sendEphemeral(event.getHook(), "Whose cards info do you want to see?", factionButtons(game, CARDS_INFO));
            return;
        }
        String text = joinedWith(List.of(cardsInfo(game, target)), "See another player's cards info:");
        sendEphemeral(event.getHook(), text, factionButtons(game, CARDS_INFO));
    }

    private static String joinedWith(List<String> reports, String prompt) {
        List<String> parts = new ArrayList<>(reports);
        parts.add(prompt);
        return String.join("\n", parts);
    }

    public static String cardsInfo(Game game, Player player) {
        return "## " + player.getRepresentationUnfoggedNoPing() + " cards info\n"
                + SecretObjectiveInfoService.getSecretObjectiveCardInfo(game, player) + "\n"
                + ActionCardHelper.getActionCardInfo(game, player) + "\n"
                + PromissoryNoteHelper.getPromissoryNoteCardInfo(game, player, false, false);
    }

    public static void sendEphemeral(InteractionHook hook, String text, List<Button> buttons) {
        for (MessageCreateData message : MessageHelper.getMessageCreateDataObjects(text, buttons)) {
            hook.sendMessage(message).setEphemeral(true).queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    private static List<Button> factionButtons(Game game, String prefix) {
        List<Button> buttons = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            buttons.add(Buttons.green(
                    prefix + player.getFaction(),
                    StringUtils.capitalize(player.getColor()) + ", " + player.getUserName(),
                    player.getFactionEmoji()));
        }
        return buttons;
    }

    private static boolean mayUse(ButtonInteractionEvent event, Game game) {
        if (game != null && isAvailable(game) && MegagameGmAccess.canUseGmTools(event.getMember())) {
            return true;
        }
        MessageHelper.sendEphemeralMessageToEventChannel(event, "Only a megagame GM can use this.");
        return false;
    }
}
