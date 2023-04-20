package ti4.commands.player;

import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class AbilityInfo extends PlayerSubcommandData {
    public AbilityInfo() {
		super(Constants.ABILITY_INFO, "Send faction abilities information to your Cards Info channel");
	}

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, Helper.getPlayerRepresentation(event, player));
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, getAbilityInfoText(player));

    }

    private String getAbilityInfoText(Player player) {
        List<String> playerAbilities = player.getFactionAbilities().stream().sorted().toList();
        StringBuilder sb = new StringBuilder("__**Ability Info**__\n");
        if (playerAbilities == null || playerAbilities.isEmpty() || playerAbilities.get(0).isBlank()) {
            sb.append("> No Abilities");
            return sb.toString();
        }
        int index = 1;
        for (String abilityID : playerAbilities) {
            sb.append("`").append(index).append(".` ");
            sb.append(getAbilityRepresentation(abilityID)).append("\n");
            index++;
        }        
        return sb.toString();
    }

    public static String getAbilityRepresentation(String abilityID) {
        HashMap<String, String> abilityInfo = Mapper.getFactionAbilities();
        String abilityRawText = abilityInfo.get(abilityID);
        StringTokenizer tokenizer = new StringTokenizer(abilityRawText, "|");
        int expectedTokenCount = 5;
        if (tokenizer.countTokens() != expectedTokenCount) {
            BotLogger.log("Ability info raw text is incorrectly formatted (needs " + (expectedTokenCount - 1) + " | to split properly):\n> " + abilityRawText);
            return abilityRawText;
        }
        String abilityName = tokenizer.nextToken();
        String abilitySourceFaction = tokenizer.nextToken();
        String abilityRawModifier = tokenizer.nextToken();
        String abilityWindow = tokenizer.nextToken();
        String abilityText = tokenizer.nextToken();

        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getFactionIconFromDiscord(abilitySourceFaction)).append("__**").append(abilityName).append("**__");
        if (!abilityRawModifier.isBlank()) sb.append(": ").append(abilityRawModifier);
        if (!abilityWindow.isBlank() || !abilityText.isBlank()) sb.append("\n> *").append(abilityWindow).append("*:\n> ").append(abilityText);


        return sb.toString();
    }
}
