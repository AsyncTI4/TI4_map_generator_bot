package ti4.commands.special;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.List;

public class SwordsToPlowsharesTGGain extends SpecialSubcommandData {
    public SwordsToPlowsharesTGGain() {
        super(Constants.SWORDS_TO_PLOWSHARES, "Swords to plowshares, kill half your infantry to get that many tgs");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
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
        doSwords(player, event, activeGame);
    }
    public void doSwords(Player player, GenericInteractionCreateEvent event, Game activeGame){
        List<String> planets = player.getPlanets();
        String ident = Helper.getFactionIconFromDiscord(player.getFaction());
        String message = "";
        for (Tile tile : activeGame.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (planets.contains(unitHolder.getName())){
                    int numInf = 0;
                    String colorID = Mapper.getColorID(player.getColor());
                    String infKey = colorID + "_gf.png";
                    if (unitHolder.getUnits() != null) {
                        if (unitHolder.getUnits().get(infKey) != null) {
                            numInf = unitHolder.getUnits().get(infKey);
                        }
                    }
                    if (numInf > 0) {
                        int numTG = (numInf+1)/2;
                        int cTG = player.getTg();
                        int fTG = cTG+numTG;
                        player.setTg(fTG);
                        message = message + ident+" removed "+numTG+" infantry from "+Helper.getPlanetRepresentation(unitHolder.getName(), activeGame)+" and gained that many tg (" + cTG + "->" + fTG + "). \n";
                        tile.removeUnit(unitHolder.getName(), infKey, numTG);
                        if(player.hasInf2Tech() ){
                            ButtonHelper.resolveInfantryDeath(activeGame, player, numTG);
                        }
                       
                    }
                }
            }  
        }
        if((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2"))){
            ButtonHelperFactionSpecific.offerMahactInfButtons(player, activeGame);
        }
         MessageChannel channel = activeGame.getMainGameChannel();
            if(activeGame.isFoWMode()){
                channel = player.getPrivateChannel();
            }
            MessageHelper.sendMessageToChannel(channel,message );
        ButtonHelperFactionSpecific.pillageCheck(player, activeGame);

    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        SpecialCommand.reply(event);
    }
}
