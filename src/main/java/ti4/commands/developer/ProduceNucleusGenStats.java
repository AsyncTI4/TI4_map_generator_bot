package ti4.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.service.draft.DraftSpec;
import ti4.service.draft.NucleusSliceGeneratorService;

class ProduceNucleusGenStats extends GameStateSubcommand {

    ProduceNucleusGenStats() {
        super("nucleus_gen_stats", "Generate statistics for nucleus generation for the in-channel game.", false, false);
        addOptions(new OptionData(
                OptionType.INTEGER, "num_iterations", "Number of iterations to run - default 1,000", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int numIterations = event.getOption("num_iterations", 1000, OptionMapping::getAsInt);
        Game game = getGame();
        DraftSpec draftSpec = new DraftSpec(game);
        if (game.getMiltySettingsUnsafe() != null) {
            draftSpec = DraftSpec.CreateFromMiltySettings(game.getMiltySettingsUnsafe());
        } else {
            String mapTemplateId = game.getMapTemplateID();
            MapTemplateModel mapTemplate = mapTemplateId != null ? Mapper.getMapTemplate(mapTemplateId) : null;
            if (mapTemplate == null) {
                mapTemplate = Mapper.getDefaultMapTemplateForPlayerCount(
                        game.getPlayers().size());
            }
            draftSpec.setTemplate(mapTemplate);
            draftSpec.numSlices = mapTemplate.getPlayerCount() + 1;
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Generating nucleus generation stats for " + numIterations + " iterations to make "
                        + draftSpec.numSlices + " slices for "
                        + draftSpec.getTemplate().getAlias() + ".");
        NucleusSliceGeneratorService.runAndPrintData(event, draftSpec, numIterations);
    }
}
