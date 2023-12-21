package ti4.commands.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public class PlanetsInfo implements Command, InfoThreadCommand {
    @Override
    public String getActionID() {
        return Constants.PLANETS_INFO;
    }

    public boolean accept(SlashCommandInteractionEvent event) {
        return acceptEvent(event, getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var user = event.getUser();
        var activeGame = GameManager.getInstance().getUserActiveGame(user.getId());

        var player = activeGame.getPlayer(user.getId());
        var planets = activeGame.getPlayer(user.getId()).getPlanets()
            .stream()
            .map(planetId -> Mapper.getPlanet(planetId).getRepresentationEmbed())
            .toList();

        MessageHelper.sendMessageEmbedsToCardsInfoThread(activeGame, player, "__**Planets:**__\n", planets);
    }

    protected String getActionDescription() {
        return "Sends list of owned planets to your Cards-Info thread";
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(Commands.slash(getActionID(), getActionDescription()));
    }
}
