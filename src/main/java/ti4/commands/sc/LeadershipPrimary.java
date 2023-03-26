package ti4.commands.sc;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.player.Stats;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.StringTokenizer;

public class LeadershipPrimary extends SCSubcommandData {
    public LeadershipPrimary() {
        super(Constants.LEADERSHIP_PRIMARY, "Leadership primary");
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TACTICAL, "Tactical command counter count"));
        addOptions(new OptionData(OptionType.STRING, Constants.FLEET, "Fleet command counter count"));
        addOptions(new OptionData(OptionType.STRING, Constants.STRATEGY, "Strategy command counter count"));
        addOptions(new OptionData(OptionType.STRING, Constants.TG, "Trade goods count"));
        addOptions(new OptionData(OptionType.STRING, Constants.COMMODITIES, "Commodity count"));


        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }


        OptionMapping optionCC = event.getOption(Constants.CC);
        OptionMapping optionT = event.getOption(Constants.TACTICAL);
        OptionMapping optionF = event.getOption(Constants.FLEET);
        OptionMapping optionS = event.getOption(Constants.STRATEGY);
        Stats stats = new Stats();
        if (optionCC != null && (optionT != null || optionF != null && optionS != null)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Use format 3/3/3 for command counters or individual values, not both");
        } else {

            if (optionCC != null) {
                @SuppressWarnings("ConstantConditions")
                String cc = AliasHandler.resolveFaction(optionCC.getAsString().toLowerCase());
                StringTokenizer tokenizer = new StringTokenizer(cc, "/");
                if (tokenizer.countTokens() != 3) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Wrong format for tokens count. Must be 3/3/3");
                } else {
                    try {
                        stats.setValue(event, player, "Tactics CC", player::setTacticalCC, player::getTacticalCC, tokenizer.nextToken());
                        stats.setValue(event, player, "Fleet CC", player::setFleetCC, player::getFleetCC, tokenizer.nextToken());
                        stats.setValue(event, player, "Strategy CC", player::setStrategicCC, player::getStrategicCC, tokenizer.nextToken());
                    } catch (Exception e) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), "Not number entered, check CC count again");
                    }
                }
            }
            if (optionT != null) {
                stats.setValue(event, player, optionT, player::setTacticalCC, player::getTacticalCC);
            }
            if (optionF != null) {
                stats.setValue(event, player, optionF, player::setFleetCC, player::getFleetCC);
            }
            if (optionS != null) {
                stats.setValue(event, player, optionS, player::setStrategicCC, player::getStrategicCC);
            }
        }
        OptionMapping optionTG = event.getOption(Constants.TG);
        if (optionTG != null) {
            stats.setValue(event, player, optionTG, player::setTg, player::getTg);
        }
        OptionMapping optionC = event.getOption(Constants.COMMODITIES);
        if (optionC != null) {
            stats.setValue(event, player, optionC, player::setCommodities, player::getCommodities);
        }

    }
}
