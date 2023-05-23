package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

public class SCTradeGoods extends StatusSubcommandData {
    public SCTradeGoods() {
        super(Constants.SC_TRADE_GOODS, "Add Trade goods to Strategy Cards");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Cards number").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "Trade good count on card").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        OptionMapping scOption = event.getOption(Constants.STRATEGY_CARD);
        OptionMapping tgOption = event.getOption(Constants.TG);
        if (scOption != null || tgOption != null) {
            if (scOption == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify Strategy Card");
                return;

            }
            //noinspection ConstantConditions
            if (tgOption == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify Trade Good Count");
                return;

            }
            int sc = scOption.getAsInt();
            int tg = tgOption.getAsInt();
            LinkedHashMap<Integer, Integer> scTradeGoods = activeMap.getScTradeGoods();
            if (!scTradeGoods.containsKey(sc)){
                MessageHelper.sendMessageToChannel(event.getChannel(), "Strategy Card must be from possible ones in Game");
                return;
            }
            Set<Integer> scPicked = new HashSet<>();
            for (Player player_ : activeMap.getPlayers().values()) {
                scPicked.addAll(player_.getSCs());
            }
            if (scPicked.contains(sc)){
                MessageHelper.sendMessageToChannel(event.getChannel(), "Strategy Card is already picked, can't add Trade Goods");
                return;
            }
            activeMap.setScTradeGood(sc, tg);
            return;
        }

        LinkedHashMap<Integer, Integer> scTradeGoods = activeMap.getScTradeGoods();
        Set<Integer> scPicked = new HashSet<>();
        for (Player player_ : activeMap.getPlayers().values()) {
            scPicked.addAll(player_.getSCs());
        }
        for (Integer scNumber :  scTradeGoods.keySet()) {
            if (!scPicked.contains(scNumber)){
                Integer tgCount = scTradeGoods.get(scNumber);
                tgCount = tgCount == null ? 1 : tgCount + 1;
                activeMap.setScTradeGood(scNumber, tgCount);
            }
        }
    }
}
