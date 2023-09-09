package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.LinkedHashMap;

public class TrapToken extends DiscordantStarsSubcommandData {

    public TrapToken() {
        super(Constants.LIZHO_TRAP, "Select planets were to add/remove trap tokens");
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.LIZHO_TRAP_ID, "Trap ID"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveMap();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        trapForPlanet(event, activeGame, Constants.PLANET, Constants.LIZHO_TRAP_ID, player);

    }

    private void trapForPlanet(SlashCommandInteractionEvent event, Game activeGame, String planet, String lizhoTrapId, Player player) {
        OptionMapping planetOption = event.getOption(planet);
        if (planetOption == null){
            return;
        }
        String planetName = planetOption.getAsString();
        if (!activeGame.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }

        Tile tile = null;
        UnitHolder unitHolder = null;
        for (Tile tile_ : activeGame.getTileMap().values()) {
            if (tile != null) {
                break;
            }
            for (java.util.Map.Entry<String, UnitHolder> unitHolderEntry : tile_.getUnitHolders().entrySet()) {
                if (unitHolderEntry.getValue() instanceof Planet && unitHolderEntry.getKey().equals(planetName)) {
                    tile = tile_;
                    unitHolder = unitHolderEntry.getValue();
                    break;
                }
            }
        }
        if (tile == null) {
            MessageHelper.replyToMessage(event, "Planet not found");
            return;
        }

        if (unitHolder.getTokenList().contains(Constants.LIZHO_TRAP_PNG)){
            tile.removeToken(Constants.LIZHO_TRAP_PNG, unitHolder.getName());
            LinkedHashMap<String, String> trapCardsPlanets = new LinkedHashMap<>(player.getTrapCardsPlanets());
            for (java.util.Map.Entry<String, String> entry : trapCardsPlanets.entrySet()) {
                if (planetName.equals(entry.getValue())) {
                    player.setTrapCardPlanet(entry.getKey(), null);
                }
            }
        } else {
            OptionMapping trapIDOption = event.getOption(lizhoTrapId);
            if (trapIDOption == null){
                return;
            }

            Collection<Integer> values = player.getTrapCards().values();
            int trapID = trapIDOption.getAsInt();
            if (!values.contains(trapID)){
                MessageHelper.replyToMessage(event, "Trap ID not found");
                return;
            }

            LinkedHashMap<String, Integer> trapCards = player.getTrapCards();
            LinkedHashMap<String, String> trapCardsPlanets = player.getTrapCardsPlanets();
            String trap = null;
            for (java.util.Map.Entry<String, Integer> traps : trapCards.entrySet()) {
                if (traps.getValue() == trapID){
                    trap = traps.getKey();
                    String planetUnitHolderName = trapCardsPlanets.get(traps.getKey());
                    if (planetUnitHolderName != null){
                        MessageHelper.replyToMessage(event, "Trap used on other planet, please use trap swap or remove first");
                        return;
                    }
                }
            }
            if (trap == null){
                MessageHelper.replyToMessage(event, "Trap ID not found");
                return;
            }

            tile.addToken(Constants.LIZHO_TRAP_PNG, unitHolder.getName());
            player.setTrapCardPlanet(trap, unitHolder.getName());
            player.setTrapCard(trap);
        }
    }
}
