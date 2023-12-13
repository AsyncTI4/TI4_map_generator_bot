package ti4.commands.agenda;

import ti4.generator.Mapper;
import ti4.message.MessageHelper;

import java.util.Map;

import org.apache.commons.lang3.ObjectUtils.Null;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class ShowLaws extends AgendaSubcommandData {
    public ShowLaws() {
        super("show_laws", "Show laws in play");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var activeGame = getActiveGame();
        var stringBuilder = new StringBuilder();

        stringBuilder.append("__**Laws Currently in Play:**__\n");
        int lawNumber = 1;
        for (var law : activeGame.getLaws().entrySet()) {
            var agenda = Mapper.getAgenda(law.getKey());
            var agendaText = activeGame.getLawsInfo().get(law.getKey());
            stringBuilder.append(lawNumber)
                .append(". ")
                .append(agenda.getRepresentation(null));
            if (agendaText != null) {
                if (Mapper.isValidFaction(agendaText)) {
                    var elected = Mapper.getFaction(agendaText).getFactionName();
                    stringBuilder.append("> **Player Elected:** ")
                        .append(elected)
                        .append("\n");
                } else if (Mapper.isValidPlanet(agendaText)) {
                    var elected = Mapper.getPlanet(agendaText).getName();
                    stringBuilder.append("> **Planet Elected:** ")
                        .append(elected)
                        .append("\n");
                } else if (Mapper.isValidSecretObjective(agendaText)) {
                    var elected = Mapper.getSecretObjective(agendaText).getName();
                    stringBuilder.append("> **Objective Elected:** ")
                        .append(elected)
                        .append("\n");
                }
            }
            lawNumber++;
        }
        MessageHelper.sendMessageToChannel(
            event.getMessageChannel(),
            stringBuilder.toString()
        );
    }
}
