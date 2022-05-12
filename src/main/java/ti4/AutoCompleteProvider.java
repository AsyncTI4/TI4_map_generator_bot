package ti4;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import ti4.generator.Mapper;
import ti4.helpers.Constants;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutoCompleteProvider {

    public static void autoCompleteListener(CommandAutoCompleteInteractionEvent event) {
        String optionName = event.getFocusedOption().getName();
        if (optionName.equals(Constants.COLOR)) {
            String enteredValue = event.getFocusedOption().getValue();
            List<Command.Choice> options = Mapper.getColors().stream()
                    .limit(25)
                    .filter(color -> color.startsWith(enteredValue))
                    .map(color -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(color, color))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        }  else if (optionName.equals(Constants.FACTION)) {
            String enteredValue = event.getFocusedOption().getValue();
            List<net.dv8tion.jda.api.interactions.commands.Command.Choice> options = Mapper.getFactions().stream()
                    .filter(token -> token.contains(enteredValue))
                    .limit(25)
                    .map(token -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(token, token))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        } else if (optionName.equals(Constants.TOKEN)) {
            String enteredValue = event.getFocusedOption().getValue();
            List<net.dv8tion.jda.api.interactions.commands.Command.Choice> options = Mapper.getTokens().stream()
                    .filter(token -> token.contains(enteredValue))
                    .limit(25)
                    .map(token -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(token, token))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        } else if (optionName.equals(Constants.GAME_STATUS)) {
            String enteredValue = event.getFocusedOption().getValue();
            List<net.dv8tion.jda.api.interactions.commands.Command.Choice> options = Stream.of("open", "locked")
                    .filter(value -> value.contains(enteredValue))
                    .limit(25)
                    .map(value -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(value, value))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        } else if (optionName.equals(Constants.DISPLAY_TYPE)) {
            String enteredValue = event.getFocusedOption().getValue();
            List<net.dv8tion.jda.api.interactions.commands.Command.Choice> options = Stream.of("all", "map", "stats", "none")
                    .filter(value -> value.contains(enteredValue))
                    .limit(25)
                    .map(value -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(value, value))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        } else if (optionName.equals(Constants.TECH) || optionName.equals(Constants.TECH2) || optionName.equals(Constants.TECH3) || optionName.equals(Constants.TECH4)) {
            String enteredValue = event.getFocusedOption().getValue().toLowerCase();
            HashMap<String, String> techs = Mapper.getTechs();
            List<net.dv8tion.jda.api.interactions.commands.Command.Choice> options = techs.entrySet().stream()
                    .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                    .limit(25)
                    .map(value -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(value.getValue(), value.getKey()))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        } else if (optionName.equals(Constants.EXPLORE_TYPE)) {
            String enteredValue = event.getFocusedOption().getValue();
            List<net.dv8tion.jda.api.interactions.commands.Command.Choice> options = Stream.of(Constants.CULTURAL, Constants.INDUSTRIAL, Constants.HAZARDOUS, Constants.FRONTIER)
                    .filter(value -> value.contains(enteredValue))
                    .limit(25)
                    .map(value -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(value, value))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        }
    }
}
