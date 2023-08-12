package ti4.commands.custom;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class SpinTilesInFirstThreeRings extends CustomSubcommandData {
    public SpinTilesInFirstThreeRings() {
        super(Constants.SPIN_TILES_IN_FIRST_THREE_RINGS, "Rotate the map according to fin logic");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        List<Tile> tilesToSet = new ArrayList<Tile>();
        //first ring
        for(int y = 1; y < 4; y++){
            for(int x = 1; x < (y*6+1); x++){
                if(y == 3 && (x-1)%3 == 0){
                    continue;
                }
                Tile tile;
                if(x < 10){
                    tile = activeMap.getTileByPosition(y+"0"+x);
                }else{
                    tile = activeMap.getTileByPosition(y+""+x);
                }
                if(y==2){
                    if((x-y) < 1){
                        tile.setPosition(y+ ""+((x-y)+(y*6)));
                    }else{
                        if((x-y)<10){
                            tile.setPosition(y+"0"+(x-y));
                        }else{
                            tile.setPosition(y+""+(x-y));
                        }  
                    }
                }else{
                    if((x+y) > (y*6)){
                        tile.setPosition(y+ "0"+((x+y)%(y*6)));
                    }else{
                        if((x+y)<10){
                            tile.setPosition(y+"0"+(x+y));
                        }else{
                            tile.setPosition(y+""+(x+y));
                        }  
                    }
                }
                tilesToSet.add(tile);
            }
        }
        for(Tile tile : tilesToSet){
            activeMap.setTile(tile);
        }
        activeMap.rebuildTilePositionAutoCompleteList();
        MapSaveLoadManager.saveMap(activeMap, event);
        DisplayType displayType = DisplayType.map;
        File file = GenerateMap.getInstance().saveImage(activeMap, displayType, event);
        MessageHelper.sendFileToChannel(event.getChannel(), file);
    }
}
