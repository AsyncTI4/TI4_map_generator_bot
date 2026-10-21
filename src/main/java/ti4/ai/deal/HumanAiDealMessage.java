package ti4.ai.deal;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
public class HumanAiDealMessage {

    public static final String ACCEPT_PREFIX = "aiDealAccept_";
    public static final String DENY_PREFIX = "aiDealDeny_";
    static final int TITLE_LIMIT = 256;
    static final int FIELD_LIMIT = 1024;
    private static final int MAX_FIELDS = 25;

    public static void post(Game game, HumanAiDeal deal) {
        Player ai = game.getPlayerFromColorOrFaction(deal.aiFaction());
        Player human = game.getPlayerFromColorOrFaction(deal.humanFaction());
        MessageChannel channel = channelFor(game, human);
        if (ai == null || human == null || channel == null) return;
        HumanAiDealStore.replaceLatest(game, deal.aiFaction());
        HumanAiDealStore.save(game, deal);
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                channel,
                human.getRepresentationUnfogged() + ", " + ai.getFactionEmojiOrColor() + " has an offer for you.",
                List.of(embed(game, deal)),
                buttons(deal),
                message -> HumanAiDealStore.save(game, deal.withMessage(message.getChannelId(), message.getId())));
    }

    public static void disable(HumanAiDeal deal) {
        if (JdaService.jda == null || StringUtils.isAnyBlank(deal.channelId(), deal.messageId())) return;
        GuildMessageChannel channel = JdaService.jda.getChannelById(GuildMessageChannel.class, deal.channelId());
        if (channel == null) return;
        channel.retrieveMessageById(deal.messageId())
                .queue(
                        message -> message.editMessageComponents(List.of()).queue(null, BotLogger::catchRestError),
                        BotLogger::catchRestError);
    }

    public static List<Button> buttons(HumanAiDeal deal) {
        String owned = ComponentIdEnvelope.ownedBy(deal.humanFaction());
        return List.of(
                Buttons.green(owned + ACCEPT_PREFIX + deal.id(), "Accept"),
                Buttons.red(owned + DENY_PREFIX + deal.id(), "Deny"));
    }

    public static MessageEmbed embed(Game game, HumanAiDeal deal) {
        Player ai = game.getPlayerFromColorOrFaction(deal.aiFaction());
        String aiName = ai.getFactionEmojiOrColor();
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(StringUtils.abbreviate("Trade offer from " + aiName, TITLE_LIMIT));
        embed.setDescription(deal.purpose());
        List<String> give = new ArrayList<>();
        List<String> get = new ArrayList<>();
        List<String> does = new ArrayList<>();
        for (HumanAiDeal.Step step : deal.steps()) {
            switch (step) {
                case HumanAiDeal.Step.ExhaustLeader exhaust -> does.add(exhaust.description());
                case HumanAiDeal.Step.Transfer transfer -> {
                    String line = describe(transfer);
                    if (transfer.fromFaction().equals(deal.humanFaction())) give.add(line);
                    else get.add(line);
                }
            }
        }
        addField(embed, "You give", give);
        addField(embed, "You get", get);
        addField(embed, aiName + " does", does);
        embed.addField(
                "This offer stands",
                "Until <t:" + deal.expiresAt() / 1000 + ":R>, or the end of the action phase.",
                false);
        return embed.build();
    }

    private static void addField(EmbedBuilder embed, String name, List<String> lines) {
        if (lines.isEmpty() || embed.getFields().size() >= MAX_FIELDS) return;
        embed.addField(
                StringUtils.abbreviate(name, TITLE_LIMIT),
                StringUtils.abbreviate(String.join("\n", lines), FIELD_LIMIT),
                false);
    }

    private static String describe(HumanAiDeal.Step.Transfer transfer) {
        return switch (transfer.item()) {
            case COMMODITIES -> transfer.amount() + " commodities (they become trade goods for the receiver)";
            case TRADE_GOODS -> transfer.amount() + " trade goods";
            case DEBT -> transfer.amount() + " debt tokens";
            case PROMISSORY -> "the promissory note " + transfer.detail();
        };
    }

    private static MessageChannel channelFor(Game game, Player human) {
        if (human == null) return null;
        return game.isFowMode() ? human.getCorrectChannel() : game.getMainGameChannel();
    }
}
