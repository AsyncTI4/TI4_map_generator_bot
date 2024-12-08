package ti4.commands2.user;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands2.Subcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.settings.users.UserSettingsManager;

class SetPreferredColourList extends Subcommand {

    public SetPreferredColourList() {
        super("set_preferred_colours", "Set your preferred colour list");
        addOption(OptionType.STRING, "colour_list", "Enter an ordered comma separated list of your preferred player colour.", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<String> colors = Helper.getListFromCSV(event.getOption("colour_list", null, OptionMapping::getAsString).toLowerCase());
        colors = new ArrayList<>(colors.stream().map(AliasHandler::resolveColor).toList());

        List<String> badColours = new ArrayList<>();
        for (String colour : colors) {
            if (!Mapper.isValidColor(colour)) {
                badColours.add(colour);
            }
        }
        colors.removeAll(badColours);

        var userSettings = UserSettingsManager.get(event.getUser().getId());
        userSettings.setPreferredColors(new HashSet<>(colors));
        UserSettingsManager.save(userSettings);

        StringBuilder sb = new StringBuilder();
        sb.append("Preferred Colour List updated to: `").append(colors).append("`");
        if (!badColours.isEmpty()) {
            sb.append("\nThe following colours were invalid and were not added: `").append(badColours).append("`");
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
