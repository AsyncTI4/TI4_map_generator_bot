package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.message.MessageHelper;

public class LawInfo extends GameStateSubcommand {

    public LawInfo() {
        super("law_info", "Show laws in play", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var game = getGame();
        var stringBuilder = new StringBuilder();
        stringBuilder.append("__**Laws Currently in Play:**__\n");
        int lawNumber = 1;
        for (var law : game.getLaws().entrySet()) {
            var agenda = Mapper.getAgenda(law.getKey());
            var agendaText = game.getLawsInfo().get(law.getKey());
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
            stringBuilder.toString());
    }
}
