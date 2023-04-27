package ti4.commands.special;

import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SwapSC extends SpecialSubcommandData {
    public SwapSC() {
        super(Constants.SWAP_SC, "Swap your SC with player2. Use OPTIONAL faction_or_color_2 to swap two other players' SCs");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to swap SC with").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR_2, "Faction or Color to swap SC with").setAutoComplete(true).setRequired(false));
    }
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        
        //resolve player1
        Player player1 = null; //OG player
        OptionMapping player1option = event.getOption(Constants.FACTION_COLOR_2);
        if (player1option == null) {
            player1 = activeMap.getPlayer(getUser().getId());
            player1 = Helper.getGamePlayer(activeMap, player1, event, null);
            if (player1 == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
                return;
            }
        } else {
            String factionColor = AliasHandler.resolveColor(player1option.getAsString().toLowerCase());
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : activeMap.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) || Objects.equals(factionColor, player_.getColor())) {
                    player1 = player_;
                    break;
                }
            }
            if (player1 == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
                return;
            }
        }
        
        //resolve player2
        Player player2 = null; //Player to swap with
        OptionMapping player2option = event.getOption(Constants.FACTION_COLOR);
        if (player2option != null) {
            String factionColor = AliasHandler.resolveColor(player2option.getAsString().toLowerCase());
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : activeMap.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) || Objects.equals(factionColor, player_.getColor())) {
                    player2 = player_;
                    break;
                }
            }
            if (player2 == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
                return;
            }       
        }

        if (player1.equals(player2)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Players provided are the same player");
            return;
        }

        if (player1.getSCs().size() > 1 || player2.getSCs().size() > 1) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Cannot swap SCs because One or more players have multiple SCs. Command not yet implemented for this scenario");
            return;
        }
                
        Integer player1SC = player1.getSCs().stream().findFirst().get();
        Integer player2SC = player2.getSCs().stream().findFirst().get();

        if (player1SC == 0 || player2SC == 0) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Cannot swap SCs because One or more players have no selected an SC yet");
            return;
        }

        player1.addSC(player2SC);
        player1.removeSC(player1SC);

        player2.addSC(player1SC);
        player2.removeSC(player2SC);

        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getPlayerRepresentation(event, player1)).append(" swapped SC with ").append(Helper.getPlayerRepresentation(event, player2)).append("\n");
        sb.append("> ").append(Helper.getPlayerRepresentation(event, player2)).append(Helper.getSCEmojiFromInteger(player2SC)).append(" ").append(":arrow_right:").append(" ").append(Helper.getSCEmojiFromInteger(player1SC)).append("\n");
        sb.append("> ").append(Helper.getPlayerRepresentation(event, player1)).append(Helper.getSCEmojiFromInteger(player1SC)).append(" ").append(":arrow_right:").append(" ").append(Helper.getSCEmojiFromInteger(player2SC)).append("\n");
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        // ListTurnOrder.turnOrder(event, activeMap);
    }
}
