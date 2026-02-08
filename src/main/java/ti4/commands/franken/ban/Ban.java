package ti4.commands.franken.ban;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.franken.FrankenBanList;

public class Ban extends GameStateSubcommand {

    private final IBanService banService = new BanService();

    public Ban() {
        super(Constants.BAN, "Ban Something From The Draft", true, false);

        addOptions(new OptionData(OptionType.STRING, Constants.ABILITY, "Ability").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER, "Leader").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PROMISSORY_NOTE_ID, "Promissory Note")
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID, "Unit (mech/flagship)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH, "Technology").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile").setAutoComplete(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.BAN_COMMODITIES, "Ban Commodities").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.BAN_FLEET, "Ban Starting Fleet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.BAN_HS, "Ban Home System").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.BAN_STARTING_TECH, "Ban Starting Technology")
                .setAutoComplete(true));

        OptionData banListOption =
                new OptionData(OptionType.STRING, Constants.BAN_LIST, "Choose a Predefined Ban List", false);
        for (FrankenBanList list : FrankenBanList.getAllBanLists()) {
            banListOption.addChoice(list.getAutoCompleteName(), list.getName());
        }
        addOptions(banListOption);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        StringBuilder sb = new StringBuilder();
        sb.append("Ban Complete:\n");

        List<String> optionNames = List.of(
                Constants.ABILITY,
                Constants.LEADER,
                Constants.PROMISSORY_NOTE_ID,
                Constants.UNIT_ID,
                Constants.TECH,
                Constants.TILE_NAME,
                Constants.BAN_COMMODITIES,
                Constants.BAN_FLEET,
                Constants.BAN_HS,
                Constants.BAN_STARTING_TECH);

        for (String optionName : optionNames) {
            OptionMapping opt = event.getOption(optionName);
            if (opt == null) continue;
            String value = opt.getAsString();
            if (value.isEmpty()) continue;
            String line = banService.applyOption(game, optionName, value);
            if (line != null && !line.isEmpty()) sb.append(line);
        }

        OptionMapping banListOpt = event.getOption(Constants.BAN_LIST);
        if (banListOpt != null) {
            String banListValue = banListOpt.getAsString();
            if (!banListValue.isEmpty()) {
                FrankenBanList list = FrankenBanList.fromString(banListValue);
                if (list == null) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Invalid ban list selection.");
                    return;
                }
                sb.append(banService.applyBanList(game, list));
            }
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }
}
