package ti4.service.emoji;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum PlanetEmojis implements TI4Emoji {

    // Base/PoK Home planets
    Nestphar, // Arborec
    Avar,
    Valk,
    Ylir, // Argent
    Acheron, // Cabal
    PlanetCreuss, // Creuss
    TheDark, // Empyrean
    Arretze,
    Kamdorn,
    Hercant, // Hacan
    Jol,
    Nar, // Jol-nar
    Planet000, // L1
    ArcPrime,
    WrenTerra, // Letnev
    Ixth, // Mahact
    MollPrimus, // Mentak
    PlanetMuaat, // Muaat
    Druaa,
    Maaluuk, // Naalu
    Naazir,
    Rokha, // NRA
    Mordai, // Nekro
    Arcturus, // Nomad
    Rahg,
    LisisII, // Saar
    Trenlak,
    Quinarra, // Sardakk
    Jord, // Sol
    Elysium, // Titans
    PlanetWinnu, // Winnu
    ArchonRen,
    ArchonTau, // Xxcha
    Darien, // Yin
    Retillion,
    Shalloq, // Yssaril

    // Base/PoK non-home
    Ang,
    ArchonVail,
    Atlas,
    Cormund, //
    Everra,
    HopesEnd,
    Lodor,
    Mallice, //
    Mecatol,
    MeharXull,
    Mirage,
    Perimeter, //
    Primor,
    Quann,
    Saudor,
    SemLore, //
    Tarmann,
    Thibah,
    Vefut,
    Vorhal,
    Wellon, //
    Abyz,
    Fria,
    Accoen,
    JeolIr, //
    Bakal,
    AlioPrima,
    Arinam,
    Meer, //
    Arnor,
    Lor,
    Bereg,
    LirtaIV, //
    Cealdri,
    Xanhact,
    Centauri,
    Gral, //
    Corneeq,
    Resculon,
    DalBootha,
    Xxehan, //
    Kraag,
    Siig,
    Lazar,
    Sakulag, //
    Lisis,
    Velnor,
    Mellon,
    Zohbat, //
    Starpoint,
    NewAlbion,
    Qucenn,
    Rarron, //
    Tequran,
    Torkan,
    VegaMajor,
    VegaMinor, //
    RigelI,
    RigelII,
    RigelIII,
    Abaddon,
    Ashtroth,
    Loki, //

    // DS
    Derbrae,
    Detic,
    Domna,
    Dorvok,
    Echo,
    EtirV,
    Fakrenn,
    Gwiyun,
    Inan,
    Larred,
    Lliot,
    Lodran,
    Mandle, //
    Moln,
    Nairb,
    Prism,
    Qaak,
    Regnem,
    Rysaa,
    Salin,
    Sanvit,
    Sierpen,
    Silence,
    Swog,
    Tarrock,
    Troac,
    Vioss, //

    // Ero
    Adoriah,
    Adrian,
    Akhassi,
    Ako,
    Aranndan,
    Argenum,
    Behjan,
    Breakpoint,
    Brilenci,
    Cahgaris,
    Cantris,
    Casibann, //
    Cerberus,
    Char,
    DeathsGate,
    Dognui,
    Dwuuit,
    ElansRest,
    ElokNu,
    ElokPhi,
    Erissiha,
    Erodius,
    Eshonia,
    Ferrust, //
    Fyrain,
    Ghanis,
    Grishinu,
    Gryenorn,
    Grywon,
    HellsMaw,
    Hersey,
    Heska,
    Hevahold,
    HranCus,
    Hurigati,
    IkrusIII, //
    IlVoshu,
    KanHis,
    Kelgate,
    Khjan,
    KkitaUlIn,
    Kris,
    Kytos,
    Leonelli,
    Limbo,
    Lunerus,
    Lust,
    Lynntani,
    Malbolge, //
    MaonLor,
    Mayris,
    Mecantor,
    MekoII,
    Meranna,
    Merjae,
    Migyro,
    MorRock,
    Mornn,
    Myrwater,
    Nix,
    Nokrurn,
    Norrk, //
    Orchard,
    Perpetual,
    Phylo,
    Plutus,
    Prymis,
    Quwon,
    RayonV,
    Renhult,
    Rhyah,
    RialArchon,
    RylFang,
    Sehnn,
    Selen, //
    Sentuim,
    Shigonas,
    Shul,
    Sigilus,
    Sokaris,
    Solin,
    Station309,
    Stygain,
    SuPrima,
    Syvian,
    TaalDorn,
    Telahas, //
    TethnSekus,
    TethnTirs,
    Thenphase,
    Tir,
    Uhott,
    UlonGamma,
    UlonRho,
    Ultimur,
    Venhalo,
    Vent,
    Verdis,
    Vernium, //
    Veyhrune,
    Viliguard,
    Violence,
    Volgan,
    Volra,
    VygarII,
    Vylanua,
    Xyon,
    Yncranti,
    Ynnis,
    Zhgen, //

    // Bonus Semlores
    SemLor,
    SemLord,
    SemiLor,
    ArchonFail;

    public static TI4Emoji getRandomSemLore() {
        List<TI4Emoji> semLores = new ArrayList<>(List.of(SemLor, SemLord, SemiLor, SemLore));
        Random seed = ThreadLocalRandom.current();
        Collections.shuffle(semLores, seed);
        return semLores.getFirst();
    }

    public static TI4Emoji getRandomArchonVail() {
        return ThreadLocalRandom.current().nextInt() % 2 == 0 ? ArchonVail : ArchonFail;
    }

    @NotNull
    public static String getPlanetEmojiString(String planet) {
        return getPlanetEmoji(planet).toString();
    }

    @NotNull
    public static TI4Emoji getPlanetEmoji(String planet) {
        TI4Emoji emoji = getPlanetEmojiOrNull(planet);
        if (emoji == null) return SemLore;
        return emoji;
    }

    @Nullable
    public static TI4Emoji getPlanetEmojiOrNull(String planet) {
        return switch (planet.toLowerCase()) {
            case "0.0.0" -> Planet000;
            case "abaddon" -> Abaddon;
            case "abyz" -> Abyz;
            case "accoen" -> Accoen;
            case "acheron" -> Acheron;
            case "alioprima" -> AlioPrima;
            case "ang" -> Ang;
            case "arcprime" -> ArcPrime;
            case "archonren", "archonrenk" -> ArchonRen;
            case "archontau", "archontauk" -> ArchonTau;
            case "archonvail" -> getRandomArchonVail();
            case "arcturus" -> Arcturus;
            case "arinam" -> Arinam;
            case "arnor" -> Arnor;
            case "arretze" -> Arretze;
            case "ashtroth" -> Ashtroth;
            case "atlas" -> Atlas;
            case "avar", "avark" -> Avar;
            case "bakal" -> Bakal;
            case "bereg" -> Bereg;
            case "cealdri" -> Cealdri;
            case "centauri" -> Centauri;
            case "cormund" -> Cormund;
            case "corneeq" -> Corneeq;
            case "creuss", "hexcreuss" -> PlanetCreuss;
            case "dalbootha" -> DalBootha;
            case "darien" -> Darien;
            case "druaa" -> Druaa;
            case "elysium" -> Elysium;
            case "everra" -> Everra;
            case "fria" -> Fria;
            case "gral" -> Gral;
            case "hercant" -> Hercant;
            case "hopesend" -> HopesEnd;
            case "ixth" -> Ixth;
            case "jeolir" -> JeolIr;
            case "jol" -> Jol;
            case "jord" -> Jord;
            case "kamdorn" -> Kamdorn;
            case "kraag" -> Kraag;
            case "lazar" -> Lazar;
            case "lirtaiv" -> LirtaIV;
            case "lisis" -> Lisis;
            case "lisisii" -> LisisII;
            case "lodor" -> Lodor;
            case "loki" -> Loki;
            case "lor" -> Lor;
            case "maaluuk" -> Maaluuk;
            case "mallice", "lockedmallice", "hexmallice", "hexlockedmalice" -> Mallice;
            case "mr" -> Mecatol;
            case "meer" -> Meer;
            case "meharxull" -> MeharXull;
            case "mellon" -> Mellon;
            case "mirage" -> Mirage;
            case "mollprimus", "mollprimusk" -> MollPrimus;
            case "mordaiii", "mordai" -> Mordai;
            case "muaat" -> PlanetMuaat;
            case "naazir" -> Naazir;
            case "nar" -> Nar;
            case "nestphar" -> Nestphar;
            case "newalbion" -> NewAlbion;
            case "perimeter" -> Perimeter;
            case "primor" -> Primor;
            case "quann" -> Quann;
            case "qucenn" -> Qucenn;
            case "quinarra" -> Quinarra;
            case "rahg" -> Rahg;
            case "rarron" -> Rarron;
            case "resculon" -> Resculon;
            case "retillion", "retillon" -> Retillion;
            case "rigeli" -> RigelI;
            case "rigelii" -> RigelII;
            case "rigeliii" -> RigelIII;
            case "rokha" -> Rokha;
            case "sakulag" -> Sakulag;
            case "saudor" -> Saudor;
            case "semlore" -> getRandomSemLore();
            case "shalloq" -> Shalloq;
            case "siig" -> Siig;
            case "starpoint" -> Starpoint;
            case "tarmann" -> Tarmann;
            case "tequran" -> Tequran;
            case "thedark" -> TheDark;
            case "thibah" -> Thibah;
            case "torkan" -> Torkan;
            case "trenlak" -> Trenlak;
            case "valk", "valkk" -> Valk;
            case "vefutii", "vefut" -> Vefut;
            case "vegamajor" -> VegaMajor;
            case "vegaminor" -> VegaMinor;
            case "velnor" -> Velnor;
            case "vorhal" -> Vorhal;
            case "wellon" -> Wellon;
            case "winnu" -> PlanetWinnu;
            case "wrenterra" -> WrenTerra;
            case "xanhact" -> Xanhact;
            case "xxehan" -> Xxehan;
            case "ylir", "ylirk" -> Ylir;
            case "zohbat" -> Zohbat;

            case "derbrae", "debrbrae" -> Derbrae;
            case "detic" -> Detic;
            case "domna" -> Domna;
            case "dorvok" -> Dorvok;
            case "echo" -> Echo;
            case "etirv" -> EtirV;
            case "fakrenn" -> Fakrenn;
            case "gwiyun" -> Gwiyun;
            case "inan" -> Inan;
            case "larred" -> Larred;
            case "lliot" -> Lliot;
            case "lodran" -> Lodran;
            case "mandle" -> Mandle;
            case "moln" -> Moln;
            case "nairb" -> Nairb;
            case "prism" -> Prism;
            case "qaak" -> Qaak;
            case "regnem" -> Regnem;
            case "rysaa" -> Rysaa;
            case "salin" -> Salin;
            case "sanvit" -> Sanvit;
            case "sierpen" -> Sierpen;
            case "silence" -> Silence;
            case "swog" -> Swog;
            case "tarrock" -> Tarrock;
            case "troac" -> Troac;
            case "vioss" -> Vioss;

            // Eronous' Planets
            case "adoriah" -> Adoriah;
            case "adrian" -> Adrian;
            case "akhassi" -> Akhassi;
            case "ako" -> Ako;
            case "aranndan" -> Aranndan;
            case "aranndanb" -> Aranndan;
            case "argenum" -> Argenum;
            case "behjan" -> Behjan;
            case "breakpoint" -> Breakpoint;
            case "brilenci" -> Brilenci;
            case "cahgaris" -> Cahgaris;
            case "cantris" -> Cantris;
            case "casibann" -> Casibann;
            case "cerberus" -> Cerberus;
            case "char" -> Char;
            case "deathsgate" -> DeathsGate;
            case "dognui" -> Dognui;
            case "dwuuit" -> Dwuuit;
            case "elansrest" -> ElansRest;
            case "eloknu" -> ElokNu;
            case "elokphi" -> ElokPhi;
            case "erissiha" -> Erissiha;
            case "erodius" -> Erodius;
            case "eshonia" -> Eshonia;
            case "ferrust" -> Ferrust;
            case "fyrain" -> Fyrain;
            case "ghanis" -> Ghanis;
            case "grishinu" -> Grishinu;
            case "gryenorn" -> Gryenorn;
            case "grywon" -> Grywon;
            case "grywonb" -> Grywon;
            case "hellsmaw" -> HellsMaw;
            case "hersey" -> Hersey;
            case "heska" -> Heska;
            case "hevahold" -> Hevahold;
            case "hevaholdb" -> Hevahold;
            case "hrancus" -> HranCus;
            case "hurigati" -> Hurigati;
            case "ikrusiii" -> IkrusIII;
            case "ilvoshu" -> IlVoshu;
            case "kanhis" -> KanHis;
            case "kelgate" -> Kelgate;
            case "khjan" -> Khjan;
            case "kkitaulin" -> KkitaUlIn;
            case "kris" -> Kris;
            case "kytos" -> Kytos;
            case "leonelli" -> Leonelli;
            case "limbo" -> Limbo;
            case "lunerus" -> Lunerus;
            case "lust" -> Lust;
            case "lynntani" -> Lynntani;
            case "malbolge" -> Malbolge;
            case "maonlor" -> MaonLor;
            case "mayris" -> Mayris;
            case "mecantor" -> Mecantor;
            case "mekoii" -> MekoII;
            case "meranna" -> Meranna;
            case "merjae" -> Merjae;
            case "migyro" -> Migyro;
            case "morrock" -> MorRock;
            case "mornn" -> Mornn;
            case "myrwater" -> Myrwater;
            case "nix" -> Nix;
            case "nokrurn" -> Nokrurn;
            case "norrk" -> Norrk;
            case "orchard" -> Orchard;
            case "perpetual" -> Perpetual;
            case "phylo" -> Phylo;
            case "plutus" -> Plutus;
            case "prymis" -> Prymis;
            case "quwon" -> Quwon;
            case "rayonv" -> RayonV;
            case "renhult" -> Renhult;
            case "rhyah" -> Rhyah;
            case "rialarchon" -> RialArchon;
            case "rylfang" -> RylFang;
            case "sehnn" -> Sehnn;
            case "selen" -> Selen;
            case "sentuim" -> Sentuim;
            case "shigonas" -> Shigonas;
            case "shul" -> Shul;
            case "sigilus" -> Sigilus;
            case "sokaris" -> Sokaris;
            case "solin" -> Solin;
            case "station309" -> Station309;
            case "stygain" -> Stygain;
            case "suprima" -> SuPrima;
            case "syvian" -> Syvian;
            case "taaldorn" -> TaalDorn;
            case "telahas" -> Telahas;
            case "tethnsekus" -> TethnSekus;
            case "tethntirs" -> TethnTirs;
            case "thenphase" -> Thenphase;
            case "tir" -> Tir;
            case "uhott" -> Uhott;
            case "ulongamma" -> UlonGamma;
            case "ulonrho" -> UlonRho;
            case "ultimur" -> Ultimur;
            case "ultimurb" -> Ultimur;
            case "venhalo" -> Venhalo;
            case "vent" -> Vent;
            case "verdis" -> Verdis;
            case "vernium" -> Vernium;
            case "veyhrune" -> Veyhrune;
            case "viliguard" -> Viliguard;
            case "violence" -> Violence;
            case "volgan" -> Volgan;
            case "volra" -> Volra;
            case "vygarii" -> VygarII;
            case "vylanua" -> Vylanua;
            case "xyon" -> Xyon;
            case "yncranti" -> Yncranti;
            case "ynnis" -> Ynnis;
            case "zhgen" -> Zhgen;

            case "space" -> TileEmojis.randomVoid();

            default -> null;
        };
    }

    @Override
    public String toString() {
        return emojiString();
    }
}
