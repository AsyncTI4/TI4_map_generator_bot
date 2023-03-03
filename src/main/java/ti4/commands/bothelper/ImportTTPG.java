package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map_ttpg.ConvertTTPGtoAsync;
import ti4.message.MessageHelper;

public class ImportTTPG extends BothelperSubcommandData {
    public ImportTTPG(){
        super(Constants.IMPORT_TTPG, "Import a recent TTPG Export to a new Async game");
        addOptions(new OptionData(OptionType.STRING, Constants.TTPG_FILE_NAME, "FilePath to Load").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "GameName to Create").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping optionFileName = event.getOption(Constants.TTPG_FILE_NAME);
        OptionMapping optionGameName = event.getOption(Constants.GAME_NAME);
        String filepath = optionFileName.getAsString();
        String gameName = optionGameName.getAsString();
        Boolean imported = ConvertTTPGtoAsync.ImportTTPG(filepath, gameName);
        if (imported) {
            sendMessage("TTPG File: `" + filepath + "` has been imported as game name: **" + gameName + "**");
        } else {
            sendMessage("TTPG File: `" + filepath + "` failed to be imported");
        }
    }
}
