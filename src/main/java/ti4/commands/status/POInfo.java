package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.message.MessageHelper;

public class POInfo extends StatusSubcommandData {
    public POInfo() {
        super("po_info", "Show Public Objectives");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var publicObjectiveIDs = getActiveGame().getRevealedPublicObjectives();
        var publicObjectives = publicObjectiveIDs.entrySet().stream()
            .filter(id -> Mapper.isValidPublicObjective(id.getKey()))
            .map(id -> Mapper.getPublicObjective(id.getKey()))
            .toList();

        var stringBuilder = new StringBuilder();
        stringBuilder.append("__**Current Public Objectives**__\n");
        int publicObjectiveNumber = 1;
        for (var publicObjective : publicObjectives) {
            stringBuilder.append(publicObjectiveNumber)
                .append(". ")
                .append(publicObjective.getRepresentation());
            publicObjectiveNumber++;
        }

        MessageHelper.sendMessageToChannel(
            event.getMessageChannel(),
            stringBuilder.toString()
        );
    }
}
