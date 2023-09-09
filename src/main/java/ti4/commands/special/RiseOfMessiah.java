package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddUnits;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.HashSet;
import java.util.List;

public class RiseOfMessiah extends SpecialSubcommandData {
    public RiseOfMessiah() {
        super(Constants.RISE_OF_A_MESSIAH, "RiseOfMessiah +1 Inf to each planet");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
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
        doRise(player, event, activeGame);
        
    }

    public void doRise(Player player, GenericInteractionCreateEvent event, Game activeGame){
        List<String> planets = player.getPlanets(activeGame);
        for (Tile tile : activeGame.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (planets.contains(unitHolder.getName())){
                    HashSet<String> tokenList = unitHolder.getTokenList();
                    boolean ignorePlanet = false;
                    for (String token : tokenList) {
                        if (token.contains("dmz") || token.contains(Constants.WORLD_DESTROYED_PNG)){
                            ignorePlanet = true;
                            break;
                        }
                    }
                    if (ignorePlanet){
                        continue;
                    }
                    new AddUnits().unitParsing(event, player.getColor(), tile, "inf "+unitHolder.getName(), activeGame);
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Added 1 infantry to each planet for "+ player.getColor());
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        SpecialCommand.reply(event);
    }
}
