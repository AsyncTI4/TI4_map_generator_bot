package ti4.commands2.user;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands2.Subcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.users.UserSettingsManager;

class SetPreferredColourList extends Subcommand {

    public SetPreferredColourList() {
        super("set_preferred_colours", "Set your preferred colour list");
        addOption(OptionType.STRING, "colour_list", "Enter an ordered comma separated list of your preferred player colour.", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<String> colourList = Helper.getListFromCSV(event.getOption("colour_list", null, OptionMapping::getAsString).toLowerCase());
        colourList = new ArrayList<>(colourList.stream().map(AliasHandler::resolveColor).toList());
        List<String> badColours = new ArrayList<>();
        for (String colour : colourList) {
            if (!Mapper.isValidColor(colour)) {
                badColours.add(colour);
            }
        }
        colourList.removeAll(badColours);
        var userSettings = UserSettingsManager.get(event.getUser().getId());
        userSettings.setPreferredColourList(colourList);
        UserSettingsManager.save(userSettings);
        StringBuilder sb = new StringBuilder();
        sb.append("Preferred Colour List updated to: `").append(colourList).append("`");
        if (!badColours.isEmpty()) {
            sb.append("\nThe following colours were invalid and were not added: `").append(badColours).append("`");
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
