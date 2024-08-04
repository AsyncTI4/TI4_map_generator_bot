package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SCTradeGoods extends StatusSubcommandData {
    public SCTradeGoods() {
        super(Constants.SC_TRADE_GOODS, "Add trade goods to strategy cards.");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy card initiative number").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "Number of trade goods to have on strategy card").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        OptionMapping scOption = event.getOption(Constants.STRATEGY_CARD);
        OptionMapping tgOption = event.getOption(Constants.TG);
        if (scOption != null || tgOption != null) {
            if (scOption == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify strategy card.");
                return;

            }
            if (tgOption == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify trade good count.");
                return;

            }
            int sc = scOption.getAsInt();
            int tg = tgOption.getAsInt();
            Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();
            if (!scTradeGoods.containsKey(sc)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Strategy card must be a strategy card that's in the game.");
                return;
            }
            Set<Integer> scPicked = new HashSet<>();
            for (Player player_ : game.getPlayers().values()) {
                scPicked.addAll(player_.getSCs());
            }
            if (scPicked.contains(sc)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Strategy card is already picked, can't add trade goods to it.");
                return;
            }
            game.setScTradeGood(sc, tg);
            return;
        }

        Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();
        Set<Integer> scPicked = new HashSet<>();
        for (Player player_ : game.getPlayers().values()) {
            scPicked.addAll(player_.getSCs());
        }
        for (Integer scNumber : scTradeGoods.keySet()) {
            if (!scPicked.contains(scNumber)) {
                Integer tgCount = scTradeGoods.get(scNumber);
                tgCount = tgCount == null ? 1 : tgCount + 1;
                game.setScTradeGood(scNumber, tgCount);
            }
        }
    }
}
