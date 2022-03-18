package ti4.map;

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
    private int pn = 0;
    private int so = 0;
    private int soScored = 0;

    private int crf = 0;
    private int irf = 0;
    private int hrf = 0;
    private int vrf = 0;
    private HashSet<String> relics = new HashSet<>();

    public int getCrf() {
        return crf;
    }

    public void setCrf(int crf) {
        this.crf = crf;
    }

    public int getIrf() {
        return irf;
    }

    public void setIrf(int irf) {
        this.irf = irf;
    }

    public int getHrf() {
        return hrf;
    }

    public void setHrf(int hrf) {
        this.hrf = hrf;
    }

    public int getVrf() {
        return vrf;
    }

    public void setVrf(int vrf) {
        this.vrf = vrf;
    }

    private int SC = 0;

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

    public int getAc() {
        return ac;
    }

    public void setAc(int ac) {
        this.ac = ac;
    }

    public int getPn() {
        return pn;
    }

    public void setPn(int pn) {
        this.pn = pn;
    }

    public int getSo() {
        return so;
    }

    public void setSo(int so) {
        this.so = so;
    }

    public int getSoScored() {
        return soScored;
    }

    public void setSoScored(int soScored) {
        this.soScored = soScored;
    }



    public int getSC() {
        return SC;
    }

    public void setSC(int SC) {
        this.SC = SC;
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
