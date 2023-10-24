package ti4.commands.player;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;

public class AbilityInfo extends PlayerSubcommandData {
    public AbilityInfo() {
		super(Constants.ABILITY_INFO, "Send faction abilities information to your Cards Info channel");
	}

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        sendAbilityInfo(activeGame, player, event);
    }

    public static void sendAbilityInfo(Game activeGame, Player player, SlashCommandInteractionEvent event) {
        String headerText = player.getRepresentation() + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendAbilityInfo(activeGame, player);
    }

    public static void sendAbilityInfo(Game activeGame, Player player) {
        //ABILITY INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, getAbilityInfoText(player));
    }

    private static String getAbilityInfoText(Player player) {
        List<String> playerAbilities = player.getAbilities().stream().sorted().toList();
        StringBuilder sb = new StringBuilder("__**Ability Info**__\n");
        if (playerAbilities.isEmpty() || playerAbilities.get(0).isBlank()) {
            sb.append("> No Abilities");
            return sb.toString();
        }
        int index = 1;
        for (String abilityID : playerAbilities) {
            AbilityModel abilityModel = Mapper.getAbility(abilityID);
            sb.append("`").append(index).append(".` ");
            sb.append(abilityModel.getRepresentation()).append("\n");
            index++;
        }
        return sb.toString();
    }
}
