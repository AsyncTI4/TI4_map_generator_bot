package ti4.map;

import java.util.ArrayList;
import java.util.HashSet;

public class Player {

    private String userID;
    private String userName;

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

    public Player(String userID, String userName) {
        this.userID = userID;
        this.userName = userName;
    }

    public String getUserID() {
        return userID;
    }

    public String getUserName() {
        return userName;
    }

    public String getFaction() {
        return faction;
    }

    public void setFaction(String faction) {
        this.faction = faction;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getTacticalCC() {
        return tacticalCC;
    }

    public void setTacticalCC(int tacticalCC) {
        this.tacticalCC = tacticalCC;
    }

    public int getFleetCC() {
        return fleetCC;
    }

    public void setFleetCC(int fleetCC) {
        this.fleetCC = fleetCC;
    }

    public int getStrategicCC() {
        return strategicCC;
    }

    public void setStrategicCC(int strategicCC) {
        this.strategicCC = strategicCC;
    }

    public int getTg() {
        return tg;
    }

    public void setTg(int tg) {
        this.tg = tg;
    }

    public int getCommodities() {
        return commodities;
    }

    public void setCommodities(int commodities) {
        this.commodities = commodities;
    }

    public int getCommoditiesTotal() {
        return commoditiesTotal;
    }

    public void setCommoditiesTotal(int commoditiesTotal) {
        this.commoditiesTotal = commoditiesTotal;
    }
}
