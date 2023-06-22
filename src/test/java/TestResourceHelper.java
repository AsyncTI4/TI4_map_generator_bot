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
    public void checkTokenLocations() {
        TileHelper.init();

        Collection<TileModel> allTiles = TileHelper.getAllTiles().values();

        allTiles.forEach(tile -> {
            if(tile.getShipPositionsType() == ShipPositionModel.ShipPosition.TYPE16) {
                System.out.print(String.format("%12s", tile.getId()) + " - ");
                List<String> pointString = tile.getSpaceTokenLocations().stream().map(point -> point.x + "/" + point.y).toList();
                System.out.print(String.format("%6s", tile.getShipPositionsType()) + " - ");
                System.out.println(pointString);
            }
        });
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
            Map<String, List<String>> relativePositionMap = new HashMap<>();

            positions.getCoordinateMap().forEach((category, points) -> {
                List<BigDecimal> distances = (points.stream().map(point -> point.distance(planetCenter.x, planetCenter.y))
                        .map(dist -> new BigDecimal(dist).setScale(0, RoundingMode.HALF_UP)).toList());

                distanceMap.put(category, distances);
            });

            positions.getCoordinateMap().forEach((category, points) -> {
                List<String> relativePos = (points.stream().map(point ->
                        new Point(point.x - planetCenter.x, point.y - planetCenter.y))
                        .map(point -> point.x + "/" + point.y)
                        .toList());

                relativePositionMap.put(category, relativePos);
            });
            String key = "att";
            System.out.print(String.format("%12s", planet.getId()) + " - ");
            System.out.println(relativePositionMap.get(key));
            allPlanetsWithDistances.put(planet.getId(), distanceMap);
        }
        //System.out.println(allPlanetsWithDistances.entrySet());
    }
}