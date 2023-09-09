package ti4.commands.ds;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;

public class TrapReveal extends DiscordantStarsSubcommandData {

    public TrapReveal() {
        super(Constants.LIZHO_REVEAL_TRAP, "Select planets were to reveal trap tokens");
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping planetOption = event.getOption(Constants.PLANET);
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
            for (Map.Entry<String, UnitHolder> unitHolderEntry : tile_.getUnitHolders().entrySet()) {
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

        if (unitHolder.getTokenList().contains(Constants.LIZHO_TRAP_PNG)) {

            LinkedHashMap<String, String> trapCardsPlanets = player.getTrapCardsPlanets();
            for (Map.Entry<String, String> entry : trapCardsPlanets.entrySet()) {
                String planet = entry.getValue();
                String trap = entry.getKey();
                if (planetName.equals(planet)) {
                    tile.removeToken(Constants.LIZHO_TRAP_PNG, unitHolder.getName());
                    player.setTrapCardPlanet(trap, null);
                    player.setTrapCard(trap);

                    Map<String, String> dsHandcards = Mapper.getDSHandcards();
                    String info = dsHandcards.get(trap);
                    String[] split = info.split(";");
                    String trapType = split[0];
                    String trapName = split[1];
                    String trapText = split[2];

                    Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
                    String representation = planetRepresentations.get(planet);
                    if (representation == null) {
                        representation = planet;
                    }
                    String sb = "__**" + "Trap: " + trapName + "**__" + " - " + trapText + "\n" +
                            "__**" + "For planet: " + representation + "**__";
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb);
                    return;
                }
            }

        }
        MessageHelper.replyToMessage(event, "Planet has no Traps");
    }
}
