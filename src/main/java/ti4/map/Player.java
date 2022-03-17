package ti4.map;

import java.util.ArrayList;
import java.util.HashSet;

public class Player {

    private String faction;
    private String color;

    private int tacticalCC = 3;
    private int fleetCC = 3;
    private int strategicCC = 2;

    private int tg = 0;
    private int commodities = 0;
    private int commoditiesTotal = 0;

    private int ac = 0;
    private int so = 0;
    private int soScored = 0;

    private int fragmentCultural = 0;
    private int fragmentIndustrial = 0;
    private int fragmentHazardous = 0;
    private HashSet<String> relics = new HashSet<>();

    private String SC = "";

}
