package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.map.FractureService;

public class SpawnFracture extends GameStateSubcommand {
    public SpawnFracture() {
        super(Constants.FRACTURE, "Add the fracture tiles, neutral units, and ingress tokens to the map", true, true);
        addOptions(new OptionData(
                        OptionType.STRING, Constants.FACTION_COLOR, "Faction or color that triggered the fracture")
                .setAutoComplete(true)
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        if (FractureService.isFractureInPlay(game)) {
            MessageHelper.sendMessageToEventChannel(event, "Fracture is already in play.");
        }

        FractureService.spawnFracture(event, game);
        FractureService.spawnIngressTokens(event, game, player, false);
    }
}
