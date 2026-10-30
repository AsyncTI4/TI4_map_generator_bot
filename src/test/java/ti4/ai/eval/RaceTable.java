package ti4.ai.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ti4.ai.AiTestGame;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.service.map.MapStringMapper;
import ti4.service.testbed.TestBedPresetService;

/**
 * The default six-seat map in the round 1 strategy phase with the custodians on Mecatol Rex: Nekro, Sol, Sardakk,
 * Hacan, Letnev and Naalu at the six home positions with their starting techs, home planets, ships and ground forces.
 */
public final class RaceTable {

    public static final String MECATOL = "000";

    public final AiTestGame test = new AiTestGame();
    public final Map<String, Player> seats = new LinkedHashMap<>();

    public RaceTable() {
        test.game.setRound(1);
        test.game.setPhaseOfGame("strategy");
        placeDefaultMap();
        seatTheFactions();
        test.game.getTileByPosition(MECATOL).addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
    }

    public Player seat(String faction) {
        return seats.get(faction);
    }

    private void placeDefaultMap() {
        MapStringMapper.getMappedTilesToPosition(TestBedPresetService.DEFAULT_MAP_STRING, test.game)
                .forEach((position, tileId) -> {
                    if (!TestBedPresetService.DEFAULT_HOME_POSITIONS.contains(position)) {
                        test.place(tileId, position);
                    }
                });
    }

    private void seatTheFactions() {
        List<String> homes = TestBedPresetService.DEFAULT_HOME_POSITIONS;
        seats.put("nekro", test.nekro);
        seats.put("sol", test.sol);
        seats.put("sardakk", test.addSeat("200000000000000011", "sardakk", "red"));
        seats.put("hacan", test.addSeat("200000000000000012", "hacan", "green"));
        seats.put("letnev", test.addSeat("200000000000000013", "letnev", "orange"));
        seats.put("naalu", test.addSeat("200000000000000014", "naalu", "yellow"));
        int index = 0;
        for (Map.Entry<String, Player> seat : seats.entrySet()) {
            String faction = seat.getKey();
            Tile home = test.place(Mapper.getFaction(faction).getHomeSystem(), homes.get(index++));
            home.getPlanetUnitHolders().forEach(planet -> seat.getValue().addPlanet(planet.getName()));
            placeKit(home, seat.getValue(), Mapper.getFaction(faction).getStartingFleet());
        }
    }

    private void placeKit(Tile home, Player owner, String fleet) {
        Planet first = home.getPlanetUnitHolders().getFirst();
        for (String token : fleet.split(",")) {
            String[] words = token.trim().split(" ");
            boolean counted = Character.isDigit(words[0].charAt(0));
            int count = counted ? Integer.parseInt(words[0]) : 1;
            UnitType type = unitType(counted ? words[1] : words[0]);
            if (type == null) continue;
            boolean ship = BoardView.MOVING_SHIPS.contains(type) || type == UnitType.Fighter;
            test.units(home, ship ? "space" : first.getName(), owner, type, count);
        }
    }

    private static UnitType unitType(String code) {
        return switch (code) {
            case "cv" -> UnitType.Carrier;
            case "cr" -> UnitType.Cruiser;
            case "dd" -> UnitType.Destroyer;
            case "dn" -> UnitType.Dreadnought;
            case "ff" -> UnitType.Fighter;
            case "fs" -> UnitType.Flagship;
            case "inf" -> UnitType.Infantry;
            case "sd" -> UnitType.Spacedock;
            case "pds" -> UnitType.Pds;
            default -> null;
        };
    }
}
