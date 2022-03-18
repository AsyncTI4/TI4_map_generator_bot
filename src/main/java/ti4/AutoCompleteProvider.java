package ti4;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import ti4.generator.Mapper;
import ti4.helpers.Constants;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutoCompleteProvider {

    public static void autoCompleteListener(CommandAutoCompleteInteractionEvent event) {
        if (event.getFocusedOption().getName().equals(Constants.COLOR)) {
            String enteredValue = event.getFocusedOption().getValue();
            List<Command.Choice> options = Mapper.getColors().stream()
                    .limit(25)
                    .filter(color -> color.startsWith(enteredValue))
                    .map(color -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(color, color))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        } else if (event.getFocusedOption().getName().equals(Constants.TOKEN)) {
            String enteredValue = event.getFocusedOption().getValue();
            List<net.dv8tion.jda.api.interactions.commands.Command.Choice> options = Mapper.getTokens().stream()
                    .filter(token -> token.contains(enteredValue))
                    .limit(25)
                    .map(token -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(token, token))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        } else if (event.getFocusedOption().getName().equals(Constants.MAP_STATUS)) {
            String enteredValue = event.getFocusedOption().getValue();
            List<net.dv8tion.jda.api.interactions.commands.Command.Choice> options = Stream.of("open", "locked")
                    .filter(value -> value.contains(enteredValue))
                    .limit(25)
                    .map(value -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(value, value))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        }
    }
}
