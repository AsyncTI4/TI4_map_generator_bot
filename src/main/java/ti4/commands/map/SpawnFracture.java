package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.map.FractureService;

public class SpawnFracture extends GameStateSubcommand {
    public SpawnFracture() {
        super(Constants.FRACTURE, "Add The Fracture tiles, neutral units, and ingress tokens to the map", true, true);
        addOption(OptionType.STRING, Constants.FACTION_COLOR, "Faction that triggered The Fracture", true, true);
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
            MessageHelper.sendMessageToEventChannel(event, "The Fracture is already in play.");
        }

        FractureService.spawnFracture(event, game);
        FractureService.spawnIngressTokens(event, game, player, null);
    }
}
