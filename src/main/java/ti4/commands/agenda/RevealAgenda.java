package ti4.commands.agenda;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;

public class RevealAgenda extends AgendaSubcommandData {
    public RevealAgenda() {
        super(Constants.REVEAL, "Reveal top Agenda from deck");
        addOption(OptionType.BOOLEAN, Constants.REVEAL_FROM_BOTTOM, "Reveal the agenda from the bottom of the deck instead of the top");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        OptionMapping revealFromBottomOption = event.getOption(Constants.REVEAL_FROM_BOTTOM);
        boolean revealFromBottom = false;
        if (revealFromBottomOption != null) {
            revealFromBottom = revealFromBottomOption.getAsBoolean();
        }

        StringBuilder sb = new StringBuilder();
        String id = activeMap.revealAgenda(revealFromBottom);
        LinkedHashMap<String, Integer> discardAgendas = activeMap.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(id);
        String[] agendaDetails = Mapper.getAgenda(id).split(";");
        String agendaName = agendaDetails[0];
        String agendaType = agendaDetails[1];
        String agendaTarget = agendaDetails[2];
        String arg1 = agendaDetails[3];
        String arg2 = agendaDetails[4];
        String agendaSource = agendaDetails[5];

        if (agendaName == null || agendaType == null || agendaTarget == null || arg1 == null || arg2 == null || agendaSource == null) {
            BotLogger.log("Agenda improperly formatted: " + id);
            sb.append("Agenda ----------\n").append(Mapper.getAgenda(id)).append("\n------------------");
        } else {
            sb.append("**__");
            if (uniqueID != null) {
                sb.append("(").append(uniqueID).append(") - ");
            }
            sb.append(agendaName).append("__** ");
            switch (agendaSource) {
                case "absol" -> sb.append(Emojis.Absol);
                case "PoK" -> sb.append(Emojis.AgendaWhite);
                default -> sb.append(Emojis.AsyncTI4Logo);
            }
            sb.append("\n");

            sb.append("> **").append(agendaType).append(":** *").append(agendaTarget).append("*\n");
            if (arg1.length() > 0) {
                arg1 = arg1.replace("For:", "**For:**");
                sb.append("> ").append(arg1).append("\n");
            }
            if (arg2.length() > 0) {
                arg2 = arg2.replace("Against:", "**Against:**");
                sb.append("> ").append(arg2).append("\n");
            }
        }

        switch (id) {
            case ("mutiny") -> sb.append("Use this command to add the objective: `/status po_add_custom public_name:Mutiny public_vp_worth:1`").append("\n");
            case ("seed_empire") -> sb.append("Use this command to add the objective: `/status po_add_custom public_name:Seed of an Empire public_vp_worth:1`").append("\n");
            case ("censure") -> sb.append("Use this command to add the objective: `/status po_add_custom public_name:Political Censure public_vp_worth:1`").append("\n");
        }

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
