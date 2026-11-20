package ti4.service.megagame;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;

@UtilityClass
public class MegagameTradeInfoService {

    public static final String OPEN_BUTTON = "megagameTradablesOpen";
    public static final String PICK_SELECT = "megagameTradablesPick";
    public static final String TRADE_WITH = "megagameTradeWith_";
    private static final int MAX_MENUS = 5;

    public static boolean isAvailable(Game game) {
        return MegagameTradeOverviewService.isActive(game);
    }

    public static Button openButton() {
        return Buttons.gray(OPEN_BUTTON, "Tradables");
    }

    public static void sendFactionPicker(InteractionHook hook, Game game) {
        List<ActionRow> rows =
                factionMenus(game.getRealPlayers()).stream().map(ActionRow::of).toList();
        hook.setEphemeral(true)
                .sendMessage(new MessageCreateBuilder()
                        .setContent("Choose a faction to see its public tradables.")
                        .setComponents(rows)
                        .build())
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static void sendPrivateTradeInfo(InteractionHook hook, Game game, Player viewer, Player target) {
        boolean canTrade = viewer != null && viewer != target && viewer.isRealPlayer();
        sendTradeInfo(
                game,
                target,
                canTrade,
                message ->
                        hook.setEphemeral(true).sendMessage(message).queue(Consumers.nop(), BotLogger::catchRestError));
    }

    public static void sendPublicTradeInfo(MessageChannel channel, Game game, Player target) {
        sendTradeInfo(
                game,
                target,
                true,
                message -> channel.sendMessage(message).queue(Consumers.nop(), BotLogger::catchRestError));
    }

    private static void sendTradeInfo(
            Game game, Player target, boolean withTradeButton, Consumer<MessageCreateData> send) {
        MessageEmbed card = MegagameTradablesPanel.card(game, target);
        List<Button> buttons = new ArrayList<>();
        if (withTradeButton) buttons.add(tradeWithButton(target));
        MegagameTradeOverviewService.existingTradeThread(game, target)
                .thenAccept(thread -> {
                    List<Button> withLink = new ArrayList<>(buttons);
                    thread.ifPresent(found -> withLink.add(Button.link(
                            found.getJumpUrl(), MegagameTradeOverviewService.shortName(target) + "'s trades")));
                    MessageCreateBuilder message = new MessageCreateBuilder().setEmbeds(card);
                    if (!withLink.isEmpty()) message.setComponents(ActionRow.of(withLink));
                    send.accept(message.build());
                })
                .exceptionally(error -> {
                    BotLogger.error(new LogOrigin(game), "Failed to send megagame trade info.", error);
                    return null;
                });
    }

    public static Button tradeWithButton(Player target) {
        return Buttons.green(
                TRADE_WITH + target.getFaction(),
                "Trade with " + MegagameTradeOverviewService.shortName(target),
                target.getFactionEmoji());
    }

    static List<StringSelectMenu> factionMenus(List<Player> players) {
        List<StringSelectMenu> menus = new ArrayList<>();
        int perMenu = StringSelectMenu.OPTIONS_MAX_AMOUNT;
        for (int start = 0; start < players.size() && menus.size() < MAX_MENUS; start += perMenu) {
            List<Player> slice = players.subList(start, Math.min(start + perMenu, players.size()));
            StringSelectMenu.Builder menu =
                    StringSelectMenu.create(PICK_SELECT + "_" + menus.size()).setPlaceholder(menuPlaceholder(slice));
            slice.forEach(player -> menu.addOptions(option(player)));
            menus.add(menu.build());
        }
        return menus;
    }

    private static String menuPlaceholder(List<Player> slice) {
        return "Factions: " + MegagameTradeOverviewService.shortName(slice.getFirst()) + " – "
                + MegagameTradeOverviewService.shortName(slice.getLast());
    }

    private static SelectOption option(Player player) {
        SelectOption option = SelectOption.of(
                MegagameTradeOverviewService.shortName(player) + " (" + StringUtils.capitalize(player.getColor()) + ")",
                player.getFaction());
        Emoji emoji = emojiOrNull(player.getFactionEmoji());
        return emoji == null ? option : option.withEmoji(emoji);
    }

    private static Emoji emojiOrNull(String formatted) {
        if (StringUtils.isBlank(formatted)) return null;
        try {
            return Emoji.fromFormatted(formatted);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
