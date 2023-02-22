package ti4.commands.agenda;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
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
        sb.append(Mapper.getAgenda(id)).append("\n");
        switch (id) {
            case ("mutiny") -> sb.append("Use this command to add the objective: `/status po_add_custom public_name:Mutiny public_vp_worth:1`").append("\n");
            case ("seed_empire") -> sb.append("Use this command to add the objective: `/status po_add_custom public_name:Seed of an Empire public_vp_worth:1`").append("\n");
            case ("censure") -> sb.append("Use this command to add the objective: `/status po_add_custom public_name:Political Censure public_vp_worth:1`").append("\n");
        }
        sb.append("-----------\n");
        MessageHelper.sendMessageToChannel(event, sb.toString());
        String text = Helper.getGamePing(event, activeMap) + " Please indicate whether you will **Play a When** or **Play an After** or not by pressing the buttons below:";

        Button playWhen = Button.danger("play_when", "Play When");
        Button noWhen = Button.primary("no_when", "No Whens").withEmoji(Emoji.fromFormatted(Emojis.nowhens));

        Button playAfter = Button.danger("play_after", "Play After");
        Button noAfter = Button.primary("no_after", "No Afters").withEmoji(Emoji.fromFormatted(Emojis.noafters));

        MessageHelper.sendMessageToChannel(event, text);
        MessageHelper.sendMessageToChannelWithButtons(event, Emojis.nowhens, playWhen, noWhen);
        MessageHelper.sendMessageToChannelWithButtons(event, Emojis.noafters, playAfter, noAfter);
        ListVoteCount.turnOrder(event, activeMap);
    }
}
