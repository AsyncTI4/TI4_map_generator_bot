package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;

public class Setup extends PlayerSubcommandData {
    public Setup() {
        super(Constants.SETUP, "Player initialisation: Faction and Color");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction Name").setRequired(true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of units").setRequired(true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        @SuppressWarnings("ConstantConditions")
        String faction = AliasHandler.resolveFaction(event.getOption(Constants.FACTION).getAsString().toLowerCase());
        if (!Mapper.isFaction(faction)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Faction not valid");
            return;
        }
        @SuppressWarnings("ConstantConditions")
        String color = event.getOption(Constants.COLOR).getAsString().toLowerCase();
        if (!Mapper.isColorValid(color)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Color not valid");
            return;
        }
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

        LinkedHashMap<String, Player> players = activeMap.getPlayers();
        for (Player playerInfo : players.values()) {
            if (playerInfo != player) {
                if (playerInfo.getColor().equals(color)) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Player:" + playerInfo.getUserName() + " already uses color:" + color);
                    return;
                } else if (playerInfo.getFaction().equals(faction)) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Player:" + playerInfo.getUserName() + " already uses faction:" + faction);
                    return;
                }
            }
        }

        player.setColor(color);
        player.setFaction(faction);
    }
}
