package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapStatus;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Stats extends PlayerSubcommandData {
    public Stats() {
        super(Constants.STATS, "Player Stats: CC,TG,Commodities");
        addOptions(new OptionData(OptionType.STRING, Constants.CC, "CC's Example: 3/3/2"))
                .addOptions(new OptionData(OptionType.STRING, Constants.TACTICAL, "Tactical command counter count"))
                .addOptions(new OptionData(OptionType.STRING, Constants.FLEET, "Fleet command counter count"))
                .addOptions(new OptionData(OptionType.STRING, Constants.STRATEGY, "Strategy command counter count"))
                .addOptions(new OptionData(OptionType.STRING, Constants.TG, "Trade goods count"))
                .addOptions(new OptionData(OptionType.STRING, Constants.COMMODITIES, "Commodity count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.COMMODITIES_TOTAL, "Commodity total count"))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Card Number count"))
                .addOptions(new OptionData(OptionType.STRING, Constants.SC_PLAYED, "Strategy Card played y/n"))
                .addOptions(new OptionData(OptionType.STRING, Constants.PASSED, "Player passed y/n"))
                .addOptions(new OptionData(OptionType.STRING, Constants.SPEAKER, "Player is speaker y/n"))
                .addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"))
                .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getPlayer(activeMap, player, event);
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }


        OptionMapping optionCC = event.getOption(Constants.CC);
        OptionMapping optionT = event.getOption(Constants.TACTICAL);
        OptionMapping optionF = event.getOption(Constants.FLEET);
        OptionMapping optionS = event.getOption(Constants.STRATEGY);
        if (optionCC != null && (optionT != null || optionF != null || optionS != null)) {
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
                        setValue(event, "Tactics CC", player::setTacticalCC, player::getTacticalCC, tokenizer.nextToken());
                        setValue(event, "Fleet CC", player::setFleetCC, player::getFleetCC, tokenizer.nextToken());
                        setValue(event, "Strategy CC", player::setStrategicCC, player::getStrategicCC, tokenizer.nextToken());
                    } catch (Exception e) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), "Not number entered, check CC count again");
                    }
                }
                Helper.isCCCountCorrect(event, activeMap, player.getColor());
            }
            if (optionT != null) {
                setValue(event, player, optionT, player::setTacticalCC, player::getTacticalCC);
            }
            if (optionF != null) {
                setValue(event, player, optionF, player::setFleetCC, player::getFleetCC);
            }
            if (optionS != null) {
                setValue(event, player, optionS, player::setStrategicCC, player::getStrategicCC);
            }
            if (optionT != null || optionF != null || optionS != null) {
                Helper.isCCCountCorrect(event, activeMap, player.getColor());
            }
        }
        OptionMapping optionTG = event.getOption(Constants.TG);
        if (optionTG != null) {
            setValue(event, player, optionTG, player::setTg, player::getTg);
        }
        OptionMapping optionC = event.getOption(Constants.COMMODITIES);
        if (optionC != null) {
            setValue(event, player, optionC, player::setCommodities, player::getCommodities);
        }
        OptionMapping optionCT = event.getOption(Constants.COMMODITIES_TOTAL);
        if (optionCT != null) {
            player.setCommoditiesTotal(optionCT.getAsInt());
        }

        OptionMapping optionSpeaker = event.getOption(Constants.SPEAKER);
        if (optionSpeaker != null) {
            String value = optionSpeaker.getAsString().toLowerCase();
            if ("y".equals(value) || "yes".equals(value)) {
                activeMap.setSpeaker(player.getUserID());
            }
        }

        OptionMapping optionPassed = event.getOption(Constants.PASSED);
        if (optionPassed != null) {
            String value = optionPassed.getAsString().toLowerCase();
            if ("y".equals(value) || "yes".equals(value)) {
                player.setPassed(true);
            } else if ("n".equals(value) || "no".equals(value)) {
                player.setPassed(false);
            }
        }
        pickSC(event, activeMap, player, event.getOption(Constants.STRATEGY_CARD));

        OptionMapping optionSCPlayed = event.getOption(Constants.SC_PLAYED);
        if (optionSCPlayed != null) {
            int sc = player.getSC();
            if (sc > 0) {
                String value = optionSCPlayed.getAsString().toLowerCase();
                if ("y".equals(value) || "yes".equals(value)) {
                    activeMap.setSCPlayed(sc, true);
                } else if ("n".equals(value) || "no".equals(value)) {
                    activeMap.setSCPlayed(sc, false);
                }
            }
        }
    }

    public static void pickSC(SlashCommandInteractionEvent event, Map activeMap, Player player, OptionMapping optionSC) {
        if (optionSC != null) {
            if (activeMap.isMapOpen() && !activeMap.isCommunityMode()){
                activeMap.setMapStatus(MapStatus.locked);
            }
            int scNumber = optionSC.getAsInt();
            LinkedHashMap<Integer, Integer> scTradeGoods = activeMap.getScTradeGoods();
            if (player.getColor() == null || "white".equals(player.getColor()) || player.getFaction() == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Can pick SC only if faction and color picked");
                return;
            }
            if (!scTradeGoods.containsKey(scNumber) && scNumber != 0) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Strategy Card must be from possible ones in Game");
            } else {
                if (scNumber > 0) {
                    LinkedHashMap<String, Player> players = activeMap.getPlayers();
                    boolean scPickedAlready = false;
                    for (Player playerStats : players.values()) {
                        if (playerStats.getSC() == scNumber) {
                            MessageHelper.sendMessageToChannel(event.getChannel(), "SC is already picked.");
                            scPickedAlready = true;
                            break;
                        }
                    }
                    if (!scPickedAlready) {
                        player.setSC(scNumber);
                        Integer tgCount = scTradeGoods.get(scNumber);
                        if (tgCount != null) {
                            int tg = player.getTg();
                            tg += tgCount;
                            player.setTg(tg);
                        }
                    }
                } else if (scNumber == 0) {
                    int sc = player.getSC();
                    player.setSC(scNumber);
                    activeMap.setSCPlayed(sc, false);
                }
            }
        }
    }

    public static void setValue(SlashCommandInteractionEvent event, Player player, OptionMapping option, Consumer<Integer> consumer, Supplier<Integer> supplier) {
        setValue(event, option.getName(), consumer, supplier, option.getAsString());
    }

    public static void setValue(SlashCommandInteractionEvent event, String optionName, Consumer<Integer> consumer, Supplier<Integer> supplier, String value) {
        try {
            boolean setValue = !value.startsWith("+") && !value.startsWith("-");
            int number = Integer.parseInt(value);
            int existingNumber = supplier.get();
            if (setValue) {
                consumer.accept(number);
            } else {
                number = existingNumber + number;
                number = Math.max(number, 0);
                consumer.accept(number);
            }
        } catch (Exception e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not parse number for: " + optionName);
        }
    }
}
