package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.TileHelper;
import ti4.helpers.Constants;

public class ReExportAllTiles extends BothelperSubcommandData {
    public ReExportAllTiles() {
        super(Constants.RE_EXPORT_TILES, "Re-exports all tiles and planets");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES to confirm").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if("YES".equals(event.getOption(Constants.CONFIRM).getAsString())) {
            TileHelper.exportAllPlanets();
            TileHelper.exportAllTiles();
        }
    }
}
