import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
//import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.generator.TileHelper;
import ti4.generator.UnitTokenPosition;
import ti4.helpers.AliasHandler;
import ti4.helpers.Storage;
import ti4.model.*;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestResourceHelper {

    @Test
    public void testHelper() {
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();
        String tileID = "18";
        //String tileName = Mapper.getTileID(tileID);
        //String tilePath = ResourceHelper.getInstance().getTileFile(tileID);

        assertEquals(tileID, tileID);
    }

    @Test
    public void checkPlanetCenterpointFromUnitCoordinates() {
        TileHelper.init();

        Collection<PlanetModel> allPlanets = TileHelper.getAllPlanets().values();
        Map<String, Map<String, List<BigDecimal>>> allPlanetsWithDistances = new HashMap<>();

        for(PlanetModel planet : allPlanets) {
            UnitTokenPosition positions = planet.getUnitPositions();
            Point planetCenter = planet.getPositionInTile();
            if(Optional.ofNullable(positions).isEmpty() || Optional.ofNullable(planetCenter).isEmpty()) {
                continue;
            }

            Map<String, List<BigDecimal>> distanceMap = new HashMap<>();

            positions.getCoordinateMap().forEach((category, points) -> {
                List<BigDecimal> distances = (points.stream().map(point -> point.distance(planetCenter.x, planetCenter.y))
                        .map(dist -> new BigDecimal(dist).setScale(0, RoundingMode.HALF_UP)).toList());

                distanceMap.put(category, distances);
            });
            allPlanetsWithDistances.put(planet.getId(), distanceMap);
        }

        Map<Map<String, List<BigDecimal>>, String> positionGroups = new HashMap<>();
        int threshold = 5;

        System.out.println(allPlanetsWithDistances.entrySet());

        /*allPlanetsWithDistances.entrySet().forEach(entry -> {
            String id = entry.getKey();
            Map<String, List<BigDecimal>> coordinateMap = entry.getValue();
            if(allPlanetsWithDistances.keySet().isEmpty()) {
                positionGroups.put(coordinateMap, id);
            }
            else {
                //coordinate map is the current one being looked at
                Map<String, List<BigDecimal>> currentCoordinateMap = coordinateMap;
                //iterate through all the group keys
                boolean needsNewGroup = true;
                for(Map<String, List<BigDecimal>> currentGroup : positionGroups.keySet()) {
                    boolean isWithinRange = true;
                    //if all distances < threshold

                    for(Map.Entry<String, List<BigDecimal>> locationEntries : currentCoordinateMap.entrySet()) {
                        String category = locationEntries.getKey();
                        List<BigDecimal> locations = locationEntries.getValue();
                        if(!isWithinRange)
                            break;

                        if(currentGroup.get(category).size() != locations.size()) {
                            isWithinRange = false;
                            break;
                        }
                        else {
                            for(int i = 0; i < locations.size(); i++) {
                                if (Math.abs(locations.get(i).subtract(currentGroup.get(category).get(i)).doubleValue()) > threshold) {
                                    isWithinRange = false;
                                    break;
                                }
                            }
                        }
                    }
                    //add to that group
                    if(isWithinRange) {
                        positionGroups.put(currentGroup, id);
                        needsNewGroup = false;
                        break;
                    }
                }
                //else, make new group
                if (needsNewGroup) {
                    positionGroups.put(currentCoordinateMap, id);
                }
            }
        });
        System.out.println(positionGroups);*/
    }
}