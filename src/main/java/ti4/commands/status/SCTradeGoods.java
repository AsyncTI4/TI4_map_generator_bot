package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;

public class SCTradeGoods extends StatusSubcommandData {
    public SCTradeGoods() {
        super(Constants.SC_TRADE_GOODS, "Add Trade goods to Strategy Cards");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC, "Strategy Cards number").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "Trade good count on card").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        OptionMapping scOption = event.getOption(Constants.SC);
        if (scOption == null) {
             MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify Strategy Card");
             return;

        }
        OptionMapping tgOption = event.getOption(Constants.TG);
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
        Set<Integer> scPicked = activeMap.getPlayers().values().stream().map(Player::getSC).collect(Collectors.toSet());
        if (scPicked.contains(sc)){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Strategy Card is already picked, can't add Trade Goods");
            return;
        }
        activeMap.setScTradeGood(sc, tg);
    }
}
