import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
//import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Storage;
import ti4.model.*;

import java.awt.*;
import java.io.File;
import java.io.IOException;
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
    public void loadPlanetInfoIntoJson() throws IOException {
        //set up all the loaders
        Mapper.init();
        System.out.println(Mapper.getTileRepresentations()); //id:names
        System.out.println(Mapper.getTileID("25")); //Gets tile image
        System.out.println(Mapper.getWormholes("25")); // gets which wormholes the tile has
        System.out.println(Mapper.getPlanetRepresentations()); //gets all planets as id:name
        System.out.println(Mapper.getPlanet("xanhact")); //given id, get all the details (r/i/type, etc)

        AliasHandler.init();
        System.out.println(AliasHandler.getTileAliasEntryList());
        System.out.println(AliasHandler.getPlanetAliasEntryList());

        PositionMapper.init();
        System.out.println(PositionMapper.getSpaceTokenPositions("25")); //list of points
        System.out.println(PositionMapper.getTileSpaceUnitLayout("25")); //returns name of tile type
        System.out.println(PositionMapper.getTilePlanetPositions("25")); //Gives planet names + coordinates in string

        //import everything into the new SystemModel object
        List<TileModel> tileModels = new ArrayList<>();
        HashMap<String, String> allTiles = Mapper.getTileRepresentations();
        Set<Object> allTileIds = Mapper.getAllTileIDs();
        System.out.println(Mapper.getTilesList());
        Map<String, String> allTileAliases = AliasHandler.getTileAliasEntryList();
        ShipPositionModel shipPositionModel = new ShipPositionModel();
        WormholeModel wormholeModel = new WormholeModel();

        List<PlanetModel> allPlanets = assembleAllPlanets();

        List<TileModel> tileObjects = new ArrayList<>();
        for (Object objId : allTileIds) {
            String id = (String) objId;
            TileModel tile = new TileModel(id);
            tile.setName(allTiles.get(id));
            String aliases = allTileAliases.get(id);
            if(Optional.ofNullable(aliases).isPresent())
                tile.setAliases(aliases.contains(",") ? Arrays.asList(aliases.split(",")) : List.of(aliases));
            tile.setImagePath(Mapper.getTileID(id));
            tile.setPlanets(enrichPlanetReferences(id, allPlanets));
            tile.setSpaceTokenLocations(PositionMapper.getSpaceTokenPositions(id));
            tile.setShipPositionsType(shipPositionModel.getTypeFromString(PositionMapper.getTileSpaceUnitLayout(id)));
            tile.setWormholes(Mapper.getWormholes(id).stream().map(wormholeModel::getWormholeFromString).collect(Collectors.toSet()));
            tileObjects.add(tile);
        }
        tileObjects.size();
        //export to json
        ObjectMapper objectMapper = new ObjectMapper();
        allPlanets.forEach(planetModel -> {
            try {
                objectMapper.writeValue(new File("src/main/resources/planets/"+planetModel.getId()+".json"),planetModel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        tileObjects.forEach(tileModel -> {
            try {
                objectMapper.writeValue(new File("src/main/resources/systems/"+tileModel.getId()+".json"),tileModel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        //verify all the output is the same
    }

    private List<PlanetModel> assembleAllPlanets() {
        Mapper.init();
        Map<String, String> planets = Mapper.getPlanetRepresentations();
        List<PlanetModel> allPlanets = new ArrayList<>();

        planets.forEach((planetId, planetName) -> {
            PlanetTypeModel planetTypeModel = new PlanetTypeModel();
            TechSpecialtyModel techSpecialtyModel = new TechSpecialtyModel();

            PlanetModel planetModel = new PlanetModel();

            String planetDetailsString = Mapper.getPlanet(planetId);
            if (Optional.ofNullable(planetDetailsString).isPresent()) {
                List<String> planetDetails = List.of(planetDetailsString.split(","));
                String planetType = planetDetails.get(1);
                int resources = Integer.parseInt(planetDetails.get(2));
                int influence = Integer.parseInt(planetDetails.get(3));
                if (planetDetails.size() == 5) {
                    String skipType = planetDetails.get(4);
                    planetModel.setTechSpecialties(List.of(techSpecialtyModel.getTechSpecialtyFromString(skipType)));
                }
                if (planetDetails.size() == 6) {
                    String[] legendary = planetDetails.get(5).split("-");
                    planetModel.setLegendaryAbilityName(legendary[0]);
                    planetModel.setLegendaryAbilityText(legendary[1]);
                }
                planetModel.setId(planetId);
                planetModel.setName(planetName);
                planetModel.setTileId(null);
                String aliases = AliasHandler.getPlanetAliasEntryList().get(planetId);
                if (Optional.ofNullable(aliases).isPresent())
                    planetModel.setAliases(aliases.contains(",") ? Arrays.asList(aliases.split(",")) : List.of(aliases));
                planetModel.setPositionInTile(null);
                planetModel.setResources(resources);
                planetModel.setInfluence(influence);
                planetModel.setPlanetType(planetTypeModel.getPlanetTypeFromString(planetType));
                planetModel.setUnitPositions(PositionMapper.getPlanetTokenPosition(planetId));
                allPlanets.add(planetModel);
            }
        });
        return allPlanets;
    }

    private List<String> enrichPlanetReferences(String tileId, List<PlanetModel> allPlanets) {
        List<PlanetModel> planetsInSystem = new ArrayList<>();

        if (PositionMapper.getTilePlanetPositions(tileId) != null) { //separates into name and point
            StringTokenizer tokenizer = new StringTokenizer(PositionMapper.getTilePlanetPositions(tileId), ";");
            while (tokenizer.hasMoreTokens()) {
                String planetInfo = tokenizer.nextToken();
                if (planetInfo.length() > 4) {
                    StringTokenizer planetTokenizer = new StringTokenizer(planetInfo, " ");
                    String planetId = planetTokenizer.nextToken().toLowerCase();
                    Point planetPosition = PositionMapper.getPoint(planetTokenizer.nextToken());

                    planetsInSystem.addAll(allPlanets.stream().filter(planetModel -> planetModel.getId().equals(planetId)).toList());
                    planetsInSystem.forEach(planetModel -> {
                        planetModel.setTileId(tileId);
                        planetModel.setPositionInTile(planetPosition);
                    });
                }
            }
        }
        return planetsInSystem.stream().map(PlanetModel::getId).toList();
    }
}