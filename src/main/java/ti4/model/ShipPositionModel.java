package ti4.model;

public enum ShipPositionModel {

    TYPE1("dd 208,83; ca 175,230; cv 100,225; dn 40,70; fs 145,3; ws 80,5; sd 250,190; mf 55,210; tkn_gf 270,115; tkn_gf 240,115; tkn_ff 270,150; tkn_ff 240,150"),
    TYPE2("dd 126,138; ca 51,158; cv 186,80; dn 90,227; fs 207,5; ws 24,86; sd 164,5; mf 192,254; tkn_gf 270,115; tkn_gf 240,115; tkn_ff 270,150; tkn_ff 240,150"),
    TYPE3("dd 44,196; ca 55,233; cv 124,129; dn 62,31; fs 115,222; ws 113,71; sd 122,5; mf 259,203; tkn_gf 270,115; tkn_gf 240,115; tkn_ff 270,150; tkn_ff 240,150"),
    TYPE4("dd 200,75; ca 175,230; cv 100,225; dn 40,70; fs 135,10; ws 80,5; sd 250,190; mf 55,210; tkn_gf 270,115; tkn_gf 240,115; tkn_ff 270,150; tkn_ff 240,150"),
    TYPE5("dd 126,138; ca 51,158; cv 186,80; dn 60,227; fs 207,5; ws 24,86; sd 164,5; mf 192,254; tkn_gf 270,115; tkn_gf 240,115; tkn_ff 270,150; tkn_ff 240,150"),
    TYPE6("dd 44,196; ca 55,233; cv 124,129; dn 62,31; fs 115,222; ws 113,71; sd 122,5; mf 259,203; tkn_gf 270,115; tkn_gf 240,115; tkn_ff 270,150; tkn_ff 240,150"),
    TYPE7("dd 115,25; ca 115,85; cv 180,115; dn 210,190; fs 40,85; ws 60,35; sd 210,245; mf 220,55; tkn_gf 270,115; tkn_gf 240,115; tkn_ff 270,150; tkn_ff 240,150"),
    TYPE8("dd 210,80; ca 210,195; cv 60,210; dn 115,150; fs 20,110; ws 175,235; sd 130,255; mf 180,15; tkn_gf 270,115; tkn_gf 240,115; tkn_ff 270,150; tkn_ff 240,150"),
    TYPE9("dd 35,205; ca 65,250; cv 135,295; dn 290,180; fs 160,240; ws 230,235; sd 295,255; mf 65,65; tkn_gf 60,115; tkn_gf 30,115; tkn_ff 60,150; tkn_ff 30,150"),
    TYPE10("dd 60,35; ca 160,220; cv 140,20; dn 70,215; fs 225,30; ws 185,70; sd 35,90; mf 235,195; tkn_gf 270,115; tkn_gf 240,115; tkn_ff 270,150; tkn_ff 240,150"),
    TYPE11("dd 60,65; ca 65,130; cv 130,190; dn 160,215; fs 180,155; ws 20,105; sd 170,250; mf 225,225; tkn_gf 270,115; tkn_gf 240,115; tkn_ff 270,150; tkn_ff 240,150"),
    TYPE12("dd 115,135; ca 130,190; cv 205,250; dn 70,240; fs 85,35; ws 170,55; sd 300,205; mf 155,305; tkn_gf 60,115; tkn_gf 30,115; tkn_ff 60,150; tkn_ff 30,150"),
    TYPE13("dd 65,45; ca 20,120; cv 100,155; dn 70,220; fs 170,205; ws 125,185; sd 215,10; mf 150,15; tkn_gf 60,115; tkn_gf 30,115; tkn_ff 60,150; tkn_ff 30,150"),
    TYPE14("dd 130,175; ca 180,220; cv 165,95; dn 80,220; fs 15,130; ws 225,35; sd 90,10; mf 150,15; tkn_gf 60,115; tkn_gf 30,115; tkn_ff 60,150; tkn_ff 30,150"),
    TYPE15("dd 275,215; ca 230,130; cv 145,125; dn 130,295; fs 185,195; ws 225,45; sd 180,75; mf 130,50; tkn_gf 85,115; tkn_gf 55,80; tkn_ff 55,115; tkn_ff 85,115"),
    TYPE16("dd 34,175; ca 79,33; cv 192,38; dn 133,210; fs 204,171; ws 47,86; sd 146,9; mf 79,237; tkn_gf 270,115; tkn_gf 240,115; tkn_ff 270,150; tkn_ff 240,150");

    private String positions;
    ShipPositionModel(String positions) {
        this.positions = positions;
    }

    private String getPositions() {
        return this.positions;
    }
}
