package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.StringTokenizer;

public class Stats extends PlayerSubcommandData {
    public Stats() {
        super(Constants.STATS, "Player Stats: CC,TG,Commodities");
        addOptions(new OptionData(OptionType.STRING, Constants.CC, "CC's Example: 3/3/2"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.TACTICAL, "Tactical command counter count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.FLEET, "Fleet command counter count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY, "Strategy command counter count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "Trade goods count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.COMMODITIES, "Commodity count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.COMMODITIES_TOTAL, "Commodity total count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.AC, "Action Card count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.PN, "Promissory Note count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.SO, "Secret Objective count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.SO_SCORED, "Score Secret Objective count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.CRF, "Cultural Relic Fragment count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.HRF, "Hazardous Relic Fragment count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.IRF, "Industrial Relic Fragment count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.VRF, "Unknown Relic Fragment count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.SC, "Strategy Card Number count"))
                .addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        if (playerOption != null) {
            String playerID = playerOption.getAsUser().getId();
            if (activeMap.getPlayer(playerID) != null) {
                player = activeMap.getPlayers().get(playerID);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Player:" + playerOption.getAsUser().getName() + " could not be found in map:" + activeMap.getName());
                return;
            }
        }

        OptionMapping option = event.getOption(Constants.CC);
        OptionMapping optionT = event.getOption(Constants.TACTICAL);
        OptionMapping optionF = event.getOption(Constants.FLEET);
        OptionMapping optionS = event.getOption(Constants.STRATEGY);
        if (option != null && (optionT != null || optionF != null && optionS != null)){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Use format 3/3/3 for command counters or individual values, not both");
        } else {

            if (option != null) {
                @SuppressWarnings("ConstantConditions")
                String cc = AliasHandler.resolveFaction(option.getAsString().toLowerCase());
                StringTokenizer tokenizer = new StringTokenizer(cc, "/");
                if (tokenizer.countTokens() != 3) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Wrong format for tokens count. Must be 3/3/3");
                } else {
                    try {
                        player.setTacticalCC(Integer.parseInt(tokenizer.nextToken()));
                        player.setFleetCC(Integer.parseInt(tokenizer.nextToken()));
                        player.setStrategicCC(Integer.parseInt(tokenizer.nextToken()));
                    } catch (Exception e) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), "Not number entered, check CC count again");
                    }
                }
            }
            if (optionT != null) {
                player.setTacticalCC(optionT.getAsInt());
            }
            if (optionF != null) {
                player.setFleetCC(optionF.getAsInt());
            }
            if (optionS != null) {
                player.setStrategicCC(optionS.getAsInt());
            }
        }
        option = event.getOption(Constants.TG);
        if (option != null) {
            player.setTg(option.getAsInt());
        }
        option = event.getOption(Constants.COMMODITIES);
        if (option != null) {
            player.setCommodities(option.getAsInt());
        }
        option = event.getOption(Constants.COMMODITIES_TOTAL);
        if (option != null) {
            player.setCommoditiesTotal(option.getAsInt());
        }
        option = event.getOption(Constants.AC);
        if (option != null) {
            player.setAc(option.getAsInt());
        }
        option = event.getOption(Constants.PN);
        if (option != null) {
            player.setPn(option.getAsInt());
        }
        option = event.getOption(Constants.SO);
        if (option != null) {
            player.setSo(option.getAsInt());
        }
        option = event.getOption(Constants.SO_SCORED);
        if (option != null) {
            player.setSoScored(option.getAsInt());
        }
        option = event.getOption(Constants.CRF);
        if (option != null) {
            player.setCrf(option.getAsInt());
        }
        option = event.getOption(Constants.HRF);
        if (option != null) {
            player.setHrf(option.getAsInt());
        }
        option = event.getOption(Constants.IRF);
        if (option != null) {
            player.setIrf(option.getAsInt());
        }
        option = event.getOption(Constants.VRF);
        if (option != null) {
            player.setVrf(option.getAsInt());
        }
        option = event.getOption(Constants.SC);
        if (option != null) {
            player.setSC(option.getAsInt());
        }
    }
}
