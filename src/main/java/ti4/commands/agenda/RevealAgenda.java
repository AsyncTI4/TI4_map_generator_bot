package ti4.commands.agenda;

import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;

public class RevealAgenda extends AgendaSubcommandData {
    public RevealAgenda() {
        super(Constants.REVEAL, "Reveal top Agenda from deck");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        StringBuilder sb = new StringBuilder();
        sb.append("-----------");
        sb.append("Agenda:\n");
        String id = activeMap.revealAgenda();
        LinkedHashMap<String, Integer> discardAgendas = activeMap.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(id);
        if (uniqueID != null) {
            sb.append("(").append(uniqueID).append(") - ");
        }
        sb.append(Mapper.getAgenda(id));
        sb.append("\n-----------\n");
        MessageHelper.sendMessageToChannel(event, sb.toString());
        String text = Helper.getGamePing(event, activeMap) + " Please respond to <:nowhens:962921609671364658> for no whens and respond to <:noafters:962923748938362931> for no afters.";

        Button playWhen = Button.danger("play_when", "Play When");
        Button noWhen = Button.primary("no_when", "No Whens");

        Button playAfter = Button.danger("play_after", "Play After");
        Button noAfter = Button.primary("no_after", "No Afters");

        MessageHelper.sendMessageToChannel(event, text);
        MessageHelper.sendMessageToChannelWithButtons(event, "<:nowhens:962921609671364658>", playWhen, noWhen);
        MessageHelper.sendMessageToChannelWithButtons(event, "<:noafters:962923748938362931>", playAfter, noAfter);
        ListVoteCount.turnOrder(event, activeMap);
    }
}
