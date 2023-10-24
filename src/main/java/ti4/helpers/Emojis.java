package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.jetbrains.annotations.NotNull;

import ti4.map.Game;
import ti4.map.Leader;

public class Emojis {
    // FACTIONS
    public static final String Arborec = "<:Arborec:946891797567799356>";
    public static final String Argent = "<:Argent:946891797366472725>";
    public static final String Cabal = "<:cabal:946891797236441089>";
    public static final String Empyrean = "<:Empyrean:946891797257404466>";
    public static final String Ghost = "<:Creuss:946891797609721866>";
    public static final String Hacan = "<:Hacan:946891797228060684>";
    public static final String Jolnar = "<:JolNar:946891797114789918>";
    public static final String L1Z1X = "<:L1Z1X:946891797219647559>";
    public static final String Letnev = "<:Letnev:946891797458714704>";
    public static final String Yssaril = "<:Yssaril:946891798138196008>";
    public static final String Mahact = "<:Mahact:946891797274165248>";
    public static final String Mentak = "<:Mentak:946891797395800084>";
    public static final String Muaat = "<:Muaat:946891797177716777>";
    public static final String Naalu = "<:Naalu:946891797412601926>";
    public static final String Naaz = "<:Naaz:946891797437747200>";
    public static final String Nekro = "<:Nekro:946891797681025054>";
    public static final String Nomad = "<:Nomad:946891797400002561>";
    public static final String Saar = "<:Saar:946891797366472735>";
    public static final String Sardakk = "<:Sardakk:946891797307748436>";
    public static final String Sol = "<:Sol:946891797706194995>";
    public static final String Titans = "<:Titans:946891798062694400>";
    public static final String Winnu = "<:Winnu:946891798050136095>";
    public static final String Xxcha = "<:Xxcha:946891797639086090>";
    public static final String Yin = "<:Yin:946891797475491892>";
    public static final String Lazax = "<:Lazax:946891797639073884>";
    public static final String Keleres = "<:Keleres:968233661654765578>";

    // FACTIONS - DISCORDANT STARS
    public static final String augers = "<:augurs:1082705489722363904>";
    public static final String axis = "<:axis:1082705549092737044>";
    public static final String bentor = "<:bentor:1082705559897264199>";
    public static final String blex = "<:blex:1082705569351204995>";
    public static final String celdauri = "<:celdauri:1082705576691253288>";
    public static final String cheiran = "<:cheiran:1082705584886915204>";
    public static final String cymiae = "<:cymiae:1082705596836487238>";
    public static final String dihmohn = "<:dihmohn:1082705607624233041>";
    public static final String edyn = "<:edyn:1082705616415502396>";
    public static final String florzen = "<:florzen:1082705625018024010>";
    public static final String freesystems = "<:freesystems:1082705633352089620>";
    public static final String ghemina = "<:ghemina:1082705641904279612>";
    public static final String ghoti = "<:ghoti:1082705649076543580>";
    public static final String gledge = "<:gledge:1082705658052366346>";
    public static final String khrask = "<:khrask:1082705665715359786>";
    public static final String kjalengard = "<:kjalengard:1082705673596448778>";
    public static final String kollecc = "<:kollecc:1082705681108447313>";
    public static final String kolume = "<:kolume:1082724367575814245>";
    public static final String kortali = "<:kortali:1082724379114340392>";
    public static final String lanefir = "<:lanefir:1082724385598742599>";
    public static final String lizho = "<:lizho:1082724396235497552>";
    public static final String mirveda = "<:mirveda:1082724403076411472>";
    public static final String mortheus = "<:mortheus:1082724410290610186>";
    public static final String mykomentori = "<:mykomentori:1082724417412530197>";
    public static final String nivyn = "<:nivyn:1082724425851482282>";
    public static final String nokar = "<:nokar:1082724447162728558>";
    public static final String olradin = "<:olradin:1082724458189570238>";
    public static final String rohdhna = "<:rohdhna:1082724463767998564>";
    public static final String tnelis = "<:tnelis:1082724470311112756>";
    public static final String vaden = "<:vaden:1082724476287975486>";
    public static final String vaylerian = "<:vaylerian:1082724483200196720>";
    public static final String veldyr = "<:veldyr:1082724490049491026>";
    public static final String zealots = "<:zealots:1082724497083334827>";
    public static final String zelian = "<:zelian:1082724503190249547>";

    // FACTIONS - OTHER
    public static final String AdminsFaction = "<:Admins:1084476380584083527>";
    public static final String Qulane = "<:Qulane:1165445638096420895> ";

    // EXPLORATION
    public static final String HFrag = "<:HFrag:1053857012766752788>";
    public static final String CFrag = "<:CFrag:1053856733849722880>";
    public static final String IFrag = "<:IFrag:1053857037131460648>";
    public static final String UFrag = "<:UFrag:1053857056991490119>";
    public static final String Relic = "<:Relic:1054075788711964784>";
    public static final String Cultural = "<:Cultural:947250123333836801>";
    public static final String Industrial = "<:Industrial:946892033031819305>";
    public static final String Hazardous = "<:Hazardous:946892033006645318>";
    public static final String Frontier = "<:Frontier:966025493805678632>";

    // CARDS
    public static final String SC1 = "<:SC1:1056594715673366548>";
    public static final String SC1Back = "<:SC1Back:1065285486425411705>";
    public static final String SC2 = "<:SC2:1056594746023366716>";
    public static final String SC2Back = "<:SC2Back:1065285514569199677>";
    public static final String SC3 = "<:SC3:1056594774620110879>";
    public static final String SC3Back = "<:SC3Back:1065285537386205214>";
    public static final String SC4 = "<:SC4:1056594795193172009>";
    public static final String SC4Back = "<:SC4Back:1065285562380062730>";
    public static final String SC5 = "<:SC5:1056594816454107187>";
    public static final String SC5Back = "<:SC5Back:1065285585125769296>";
    public static final String SC6 = "<:SC6:1056594839778623599>";
    public static final String SC6Back = "<:SC6Back:1065285613265371256>";
    public static final String SC7 = "<:SC7:1056594860360073236>";
    public static final String SC7Back = "<:SC7Back:1065285634299809863>";
    public static final String SC8 = "<:SC8:1056594882141098055>";
    public static final String SC8Back = "<:SC8Back:1065285658207330354>";
    public static final String ActionCard = "<:Actioncard:1054660449515352114>";
    public static final String ActionCardAlt = "<:ActionCardAlt:1064838264520986655>";
    public static final String Agenda = "<:Agenda:1054660476874792990>";
    public static final String AgendaAlt = "<:AgendaAlt:1064838239690698812>";
    public static final String AgendaWhite = "<:Agendawhite:1060073913423495258>";
    public static final String AgendaBlack = "<:Agendablack:1060073912442036336>";
    public static final String PN = "<:PN:1054660504175521882>";
    public static final String PNALt = "<:PNALt:1064838292467613766>";
    public static final String RelicCard = "<:RelicCard:1147194759903989912>";
    public static final String CulturalCard = "<:CulturalCard:1147194826647928932>";
    public static final String HazardousCard = "<:HazardousCard:1147194829479100557>";
    public static final String IndustrialCard = "<:IndustrialCard:1147194830762545183>";
    public static final String FrontierCard = "<:FrontierCard:1147194828417929397>";

    // OBJECTIVES
    public static final String SecretObjective = "<:Secretobjective:1054660535544729670>";
    public static final String Public1 = "<:Public1:1054075764510826539>";
    public static final String Public2 = "<:Public2:1054075738602622986>";
    public static final String Public1alt = "<:Public1Alt:1058978029243728022>";
    public static final String Public2alt = "<:Public2Alt:1058977929725493398>";
    public static final String SecretObjectiveAlt = "<:SecretobjectiveAlt:1058977803728584734>";

    // COMPONENTS
    public static final String tg = "<:tg:1053857635570553024>";
    public static final String nomadcoin = "<:nomadcoin:1107100093791879178>";
    public static final String comm = "<:comm:1053857614028607538>";
    public static final String Sleeper = "<:Sleeper:1047871121451663371>";
    public static final String SleeperB = "<:SleeperB:1047871220831506484>";

    // UNITS
    public static final String warsun = "<:warsun:993064568626614375>";
    public static final String spacedock = "<:spacedock:993064508828418159>";
    public static final String pds = "<:pds:993064415639384064>";
    public static final String mech = "<:mech:993064350988390430>";
    public static final String infantry = "<:infantry:993064251994407004>";
    public static final String flagship = "<:flagship:993064196264710204>";
    public static final String fighter = "<:fighter:993064145907892284>";
    public static final String dreadnought = "<:dreadnought:993064090589216828>";
    public static final String destroyer = "<:destroyer:993063959840182323>";
    public static final String carrier = "<:carrier:993063885168967700>";
    public static final String cruiser = "<:cruiser:993063818844459098>";

    // EMOJI FARM 4
    public static final String ArborecAgent = "<:ArborecAgent:1159149650465525760>";
    public static final String ArborecCommander = "<:ArborecCommander:1159149652306825328>";
    public static final String ArborecHero = "<:ArborecHero:1159149653732896888>";
    public static final String ArgentAgent = "<:ArgentAgent:1159149654852784138>";
    public static final String ArgentCommander = "<:ArgentCommander:1159149657419685899>";
    public static final String ArgentHero = "<:ArgentHero:1159149658879303812>";
    public static final String CabalAgent = "<:CabalAgent:1159149661744017539>";
    public static final String CabalCommander = "<:CabalCommander:1159149662767431802>";
    public static final String CabalHero = "<:CabalHero:1159149665061716008>";
    public static final String CreussAgent = "<:CreussAgent:1162424911768338513>";
    public static final String CreussCommander = "<:CreussCommander:1162424913597038683>";
    public static final String CreussHero = "<:CreussHero:1162424914784047184>";
    public static final String EmpyreanAgent = "<:EmpyreanAgent:1162424934912499732>";
    public static final String EmpyreanCommander = "<:EmpyreanCommander:1162424936850260090>";
    public static final String EmpyreanHero = "<:EmpyreanHero:1162424937928216606>";
    public static final String HacanAgent = "<:HacanAgent:1162425906896326716>";
    public static final String HacanCommander = "<:HacanCommander:1162425908192366663>";
    public static final String HacanHero = "<:HacanHero:1162425909928796160>";
    public static final String JolNarAgent = "<:JolNarAgent:1162425929671397446>";
    public static final String JolNarCommander = "<:JolNarCommander:1162425931516874762>";
    public static final String JolNarHero = "<:JolNarHero:1162425933014237294>";
    public static final String L1Z1XAgent = "<:L1Z1XAgent:1162426312271605850>";
    public static final String L1Z1XHero = "<:L1Z1XHero:1162426315446698185>";
    public static final String L1Z1XCommander = "<:L1z1xCommander:1162426314154836119>";
    public static final String LetnevAgent = "<:LetnevAgent:1162426334702743612>";
    public static final String LetnevCommander = "<:LetnevCommander:1162426336489521272>";
    public static final String LetnevHero = "<:LetnevHero:1162426337747808367>";
    public static final String MahactAgent = "<:MahactAgent:1162426355460362280>";
    public static final String MahactCommander = "<:MahactCommander:1162426358056620093>";
    public static final String MahactHero = "<:MahactHero:1162426359541415976>";
    public static final String MentakAgent = "<:MentakAgent:1162426382035464304>";
    public static final String MentakCommander = "<:MentakCommander:1162426383734153336>";
    public static final String MentakHero = "<:MentakHero:1162426384795316265>";
    public static final String MuaatAgent = "<:MuaatAgent:1162426409063563274>";
    public static final String MuaatCommander = "<:MuaatCommander:1162426412226064555>";
    public static final String MuaatHero = "<:MuaatHero:1162426413505319012>";
    public static final String NaaluAgent = "<:NaaluAgent:1162426433436655656>";
    public static final String NaaluCommander = "<:NaaluCommander:1162426437966512158>";
    public static final String NaaluHero = "<:NaaluHero:1162426468601704541>";
    public static final String NaazAgent = "<:NaazAgent:1162426493423603802>";
    public static final String NaazCommander = "<:NaazCommander:1162426508304986172>";
    public static final String NaazHero = "<:NaazHero:1162426521659654185>";
    public static final String NekroAgent = "<:NekroAgent:1162426537526702231>";
    public static final String NekroCommander = "<:NekroCommander:1162426539208626307>";
    public static final String NekroHero = "<:NekroHero:1162426540705992836>";
    public static final String NomadAgentArtuno = "<:NomadAgentArtuno:1162426563590094859>";
    public static final String NomadAgentMercer = "<:NomadAgentMercer:1162426566517739681>";
    public static final String NomadAgentThundarian = "<:NomadAgentThundarian:1162426568140935350>";
    public static final String NomadCommander = "<:NomadCommander:1162426571014025296>";
    public static final String NomadHero = "<:NomadHero:1162426572465258546>";
    // END OF EMOJI FARM 4

    // EMOJI FARM 5
    public static final String KeleresAgent = "<:KeleresAgent:1162427226835402802>";
    public static final String KeleresHeroKuuasi = "<:KeleresArgentHero:1162427228295004230>";
    public static final String KeleresHeroHarka = "<:KeleresMentakHero:1162427229620412426>";
    public static final String KeleresHeroOdlynn = "<:KeleresXxchaHero:1162427231772082307>";
    public static final String SaarAgent = "<:SaarAgent:1162427257416069223>";
    public static final String SaarCommander = "<:SaarCommander:1162427259462877316>";
    public static final String SaarHero = "<:SaarHero:1162427260813451315>";
    public static final String SardakkAgent = "<:SardakkAgent:1162427270095450122>";
    public static final String SardakkCommander = "<:SardakkCommander:1162427271844466738>";
    public static final String SardakkHero = "<:SardakkHero:1162427273362821120>";
    public static final String SolAgent = "<:SolAgent:1162427284444168304>";
    public static final String SolCommander = "<:SolCommander:1162427286079938580>";
    public static final String SolHero = "<:SolHero:1162427287636033576>";
    public static final String TitansAgent = "<:TitansAgent:1162427306380361828>";
    public static final String TitansCommander = "<:TitansCommander:1162427307936452729>";
    public static final String TitansHero = "<:TitansHero:1162427309157007491>";
    public static final String WinnuAgent = "<:WinnuAgent:1162427326261375167>";
    public static final String WinnuCommander = "<:WinnuCommander:1162427328098488410>";
    public static final String WinnuHero = "<:WinnuHero:1162427329713287228>";
    public static final String XxchaAgent = "<:XxchaAgent:1162427362533707847>";
    public static final String XxchaCommander = "<:XxchaCommander:1162427363922038886>";
    public static final String XxchaHero = "<:XxchaHero:1162427360965046404>";
    public static final String YinAgent = "<:YinAgent:1162427379164119211>";
    public static final String YinCommander = "<:YinCommander:1162427381491970068>";
    public static final String YinHero = "<:YinHero:1162427383148712037>";
    public static final String YssarilAgent = "<:YssarilAgent:1162427398663454820>";
    public static final String YssarilCommander = "<:YssarilCommander:1162427400475381792>";
    public static final String YssarilHero = "<:YssarilHero:1162427402014699540>";
    public static final String AugersAgent = "<:AugersAgent:1162427437980860476>";
    public static final String AugersCommander = "<:AugersCommander:1162427440052842567>";
    public static final String AugersHero = "<:AugersHero:1162427448495980597>";
    public static final String AxisAgent = "<:AxisAgent:1162427470138577057>";
    public static final String AxisCommander = "<:AxisCommander:1162427471984070726>";
    public static final String AxisHero = "<:AxisHero:1162427473376592022>";
    public static final String BentorAgent = "<:BentorAgent:1162427492607459389>";
    public static final String BentorCommander = "<:BentorCommander:1162427494205489172>";
    public static final String BentorHero = "<:BentorHero:1162427495396683816>";
    public static final String BlexAgent = "<:BlexAgent:1162427509460172923>";
    public static final String BlexCommander = "<:BlexCommander:1162427511444099192>";
    public static final String BlexHero = "<:BlexHero:1162427512849186987>";
    public static final String CeldauriAgent = "<:CeldauriAgent:1162427529555091570>";
    public static final String CeldauriCommander = "<:CeldauriCommander:1162427531467698266>";
    public static final String CeldauriHero = "<:CeldauriHero:1162427533313200138>";
    public static final String CheiranAgent = "<:CheiranAgent:1162427551470321805>";
    public static final String CheiranCommander = "<:CheiranCommander:1162427552619561110>";
    public static final String CheiranHero = "<:CheiranHero:1162427554456674395>";
    public static final String GheminaAgent = "<:GheminaAgent:1162427719829684254>";
    public static final String GheminaCommander = "<:GheminaCommander:1162427721725526127>";
    public static final String GheminaHeroLady = "<:GheminaHeroLady:1162427723109646396>";
    public static final String GheminaHeroLord = "<:GheminaHeroLord:1162427717791256677>";
    // END OF EMOJI FARM 5

    // EMOJI FARM 6
    public static final String CymiaeAgent = "<:CymiaeAgent:1162423164702314547>";
    public static final String CymiaeCommander = "<:CymiaeCommander:1162423166262575165>";
    public static final String CymiaeHero = "<:CymiaeHero:1162423167546048677>";
    public static final String DihmohnAgent = "<:DihmohnAgent:1162423159648170094>";
    public static final String DihmohnCommander = "<:DihmohnCommander:1162423161988591706>";
    public static final String DihmohnHero = "<:DihmohnHero:1162423163368525845>";
    public static final String EdynAgent = "<:EdynAgent:1162423214190891029>";
    public static final String EdynCommander = "<:EdynCommander:1162423215990247535>";
    public static final String EdynHero = "<:EdynHero:1162423217235955722>";
    public static final String FlorzenAgent = "<:FlorzenAgent:1162423218540396594>";
    public static final String FlorzenCommander = "<:FlorzenCommander:1162423220369104978>";
    public static final String FlorzenHero = "<:FlorzenHero:1162423221690310656>";
    public static final String FreesystemCommander = "<:FreesystemCommander:1162423239646122055>";
    public static final String FreesystemHero = "<:FreesystemHero:1162423241286090762>";
    public static final String FreesystemsAgent = "<:FreesystemsAgent:1162423242456313996>";
    public static final String GhotiAgent = "<:GhotiAgent:1162423285485670561>";
    public static final String GhotiCommander = "<:GhotiCommander:1162423286899155025>";
    public static final String GhotiHero = "<:GhotiHero:1162423287981297755>";
    public static final String GledgeAgent = "<:GledgeAgent:1162423289281515632>";
    public static final String GledgeCommander = "<:GledgeCommander:1162423290837598248>";
    public static final String GledgeHero = "<:GledgeHero:1162423283841503292>";
    public static final String KhraskAgent = "<:KhraskAgent:1162423321388912760>";
    public static final String KhraskCommander = "<:KhraskCommander:1162423325977477220>";
    public static final String KhraskHero = "<:KhraskHero:1162423327143501967>";
    public static final String KjalengardAgent = "<:KjalengardAgent:1162423340141658112>";
    public static final String KjalengardCommander = "<:KjalengardCommander:1162423346768646266>";
    public static final String KjalengardHero = "<:KjalengardHero:1162423348828065932>";
    // END OF EMOJI FARM 6

    // RESOURCE AND INFLUENCE SYMBOLS
    public static final String influence = "<:influence:959575421337358336>";
    public static final String resources = "<:resources:959575421274451998>";
    public static final String ResInf = "<:ResInf:1104118692897374379>";
    public static final String Resources_0 = "<:R0:864278976553156640>";
    public static final String Resources_1 = "<:R1:864278976524189727>";
    public static final String Resources_2 = "<:R2:864278977133019157>";
    public static final String Resources_3 = "<:R3:864278977321631754>";
    public static final String Resources_4 = "<:R4:864278977355186176>";
    public static final String Resources_5 = "<:R5:864278977468170290>";
    public static final String Resources_6 = "<:R6:864278977459126278>";
    public static final String Resources_7 = "<:R7:864278977468432395>";
    public static final String Resources_8 = "<:R8:864278977530691604>";
    public static final String Resources_9 = "<:R9:864278977463451699>";
    public static final String Influence_0 = "<:I0:864278934032351282>";
    public static final String Influence_1 = "<:I1:864278934195798046>";
    public static final String Influence_2 = "<:I2:864278934249406474>";
    public static final String Influence_3 = "<:I3:864278934786932746>";
    public static final String Influence_4 = "<:I4:864278934501195836>";
    public static final String Influence_5 = "<:I5:864278934506176552>";
    public static final String Influence_6 = "<:I6:864278934504996866>";
    public static final String Influence_7 = "<:I7:864278934602776576>";
    public static final String Influence_8 = "<:I8:864278934173253653>";
    public static final String Influence_9 = "<:I9:864278934509322300>";

    // PLANETS
    public static final String MecatolRex = "<:MecatolRex:1072083209250152489>";

    public static final String SemLor = "<:SemLor:1072075882618961930>";
    public static final String SemLord = "<:SemLord:1072076401462738965>";
    public static final String SemiLor = "<:SemiLor:1072076567053869106>";

    // EMOJI FARM 7
    public static final String Planet000 = "<:000:1159490197151563797>";
    public static final String Abaddon = "<:Abaddon:1159490198992851068>";
    public static final String Abyz = "<:Abyz:1159490201316499578>";
    public static final String Accoen = "<:Accoen:1159490202960674836>";
    public static final String Acheron = "<:Acheron:1159490205322072185>";
    public static final String AlioPrima = "<:AlioPrima:1159490208199352330>";
    public static final String Ang = "<:Ang:1159490211277975562>";
    public static final String ArcPrime = "<:ArcPrime:1159490274519679117>";
    public static final String ArchonRen = "<:ArchonRen:1159490213874237511>";
    public static final String ArchonTau = "<:ArchonTau:1159490215258374154>";
    public static final String ArchonVail = "<:ArchonVail:1159490217997254776>";
    public static final String Arcturus = "<:Arcturus:1159490276583293000>";
    public static final String Arinam = "<:Arinam:1159490278097432617>";
    public static final String Arnor = "<:Arnor:1159490279267647498>";
    public static final String Arretze = "<:Arretze:1159490281364799558>";
    public static final String Ashtroth = "<:Ashtroth:1159490299668738169>";
    public static final String Atlas = "<:Atlas:1159490302613139566>";
    public static final String Avar = "<:Avar:1159490303804330087>";
    public static final String Bakal = "<:Bakal:1159490304987123743>";
    public static final String Bereg = "<:Bereg:1159490307117826048>";
    public static final String Cealdri = "<:Cealdri:1159490330110988359>";
    public static final String Centauri = "<:Centauri:1159490331931328562>";
    public static final String Cormund = "<:Cormund:1159490333705502840>";
    public static final String Corneeq = "<:Corneeq:1159490335056089178>";
    public static final String Creuss = "<:Creuss:1159490337400688682>";
    public static final String DalBootha = "<:DalBootha:1159490358967816212>";
    public static final String Darien = "<:Darien:1159490360859435070>";
    public static final String Druaa = "<:Druua:1159490362587488266>";
    public static final String Elysium = "<:Elysium:1159491456516505661>";
    public static final String Everra = "<:Everra:1159491751988432966>";
    public static final String Fria = "<:Fria:1159491774679629895>";
    public static final String Gral = "<:Gral:1159491776508350535>";
    public static final String Hercant = "<:Hercant:1159491778072825926>";
    public static final String HopesEnd = "<:HopesEnd:1159492039772217474>";
    public static final String Ixth = "<:Ixth:1159491781793173554>";
    public static final String JeolIr = "<:JeolIr:1159492098190491789>";
    public static final String Jol = "<:Jol:1159492099910156329>";
    public static final String Jord = "<:Jord:1159492101558521916>";
    public static final String Kamdorn = "<:Kamdorn:1159506093496610866>";
    public static final String Kraag = "<:Kraag:1159506108415737886>";
    public static final String Lazar = "<:Lazar:1159506143358484490>";
    public static final String LirtaIV = "<:LirtaIV:1159506145212370944>";
    public static final String Lisis = "<:Lisis:1159506146672001086>";
    public static final String LisisII = "<:LisisII:1159506148169371679>";
    public static final String Lodor = "<:Lodor:1159506150430089287>";
    public static final String Loki = "<:Loki:1159506175998570596>";
    public static final String Lor = "<:Lor:1159506177797935144>";
    public static final String Maaluuk = "<:Maaluuk:1159506179077193770>";
    public static final String Mallice = "<:Mallice:1159506180641656832>";
    public static final String Mecatol = "<:Mecatol:1159506366931664926>";
    // END OF EMOJI FARM 7

    // EMOJI FARM 8
    public static final String Meer = "<:Meer:1159512956778856519>";
    public static final String MeharXull = "<:MeharXull:1159512958821466243>";
    public static final String Mellon = "<:Mellon:1159512961006714951>";
    public static final String MollPrimus = "<:MollPrimus:1159512963347132436>";
    public static final String Mordai = "<:Mordai:1159512965398138960>";
    public static final String PlanetMuaat = "<:Muaat:1159512985757307030>";
    public static final String Naazir = "<:Naazir:1159512987422445578>";
    public static final String Nar = "<:Nar:1159512989058211860>";
    public static final String Nestphar = "<:Nestphar:1159512993780994159>";
    public static final String NewAlbion = "<:NewAlbion:1159512997186773062>";
    public static final String Perimeter = "<:Perimeter:1159513626605006959>";
    public static final String Primor = "<:Primor:1159513504764678225>";
    public static final String Quann = "<:Quann:1159513112551112744>";
    public static final String Qucenn = "<:Qucenn:1159513114459504680>";
    public static final String Quinarra = "<:Quinarra:1159513115608748103>";
    public static final String Rahg = "<:Rahg:1159513660822130728>";
    public static final String Rarron = "<:Rarron:1159513662923477062>";
    public static final String Resculon = "<:Resculon:1159513664899006615>";
    public static final String Retillon = "<:Retillon:1159513666232787045>";
    public static final String RigelI = "<:RigelI:1159513668589981736>";
    public static final String RigelII = "<:RigelII:1159513693374124112>";
    public static final String RigelIII = "<:RigelIII:1159513695081222204>";
    public static final String Rokha = "<:Rokha:1159513696595357776>";
    public static final String Sakulag = "<:Sakulag:1159513698134655026>";
    public static final String Saudor = "<:Saudor:1159513700437348393>";
    public static final String SemLore = "<:SemLore:1159513757677006969>";
    public static final String Shalloq = "<:Shalloq:1159513759925158008>";
    public static final String Siig = "<:Siig:1159513761288294492>";
    public static final String Starpoint = "<:Starpoint:1159513762701787176>";
    public static final String Tarmann = "<:Tarmann:1159513764522106890>";
    public static final String Tequran = "<:Tequran:1159513787838242960>";
    public static final String TheDark = "<:TheDark:1159513998870466600>";
    public static final String Thibah = "<:Thibah:1159513791420170250>";
    public static final String Torkan = "<:Torkan:1159513792548458608>";
    public static final String Trenlak = "<:Trenlak:1159513794054205471>";
    public static final String Valk = "<:Valk:1159514021951709254>";
    public static final String Vefut = "<:Vefut:1159514023285497937>";
    public static final String VegaMajor = "<:VegaMajor:1159514025084854282>";
    public static final String VegaMinor = "<:VegaMinor:1159514026557063198>";
    public static final String Velnor = "<:Velnor:1159514028687761408>";
    public static final String Vorhal = "<:Vorhal:1159514056294666310>";
    public static final String Wellon = "<:Wellon:1159514058135973949>";
    public static final String PlanetWinnu = "<:Winnu:1159514060090511502>";
    public static final String WrenTerra = "<:WrenTerra:1159528217892368465>";
    public static final String Xanhact = "<:Xanhact:1159528230508826694>";
    public static final String Xxehan = "<:Xxehan:1159528242626166794>";
    public static final String Ylir = "<:Ylir:1159528244769476649>";
    public static final String Zohbat = "<:Zohbat:1159528246166167586>";
    // END OF EMOJI FARM 8

    // LIST OF SEM-LORES
    public static final List<String> SemLores = Arrays.asList(SemLor, SemLord, SemiLor, SemLore);

    // EMOJI FARM 9 - SC COLOURS
    public static final String sc_1_1 = "<:sc_1_1:1164316518390190140>";
    public static final String sc_1_2 = "<:sc_1_2:1164316520986464267>";
    public static final String sc_1_3 = "<:sc_1_3:1164316522689339392>";
    public static final String sc_1_4 = "<:sc_1_4:1164316525021380729>";
    public static final String sc_1_5 = "<:sc_1_5:1164316526501965864>";
    public static final String sc_1_6 = "<:sc_1_6:1164316528024494130>";
    public static final String SC1Mention = sc_1_1 + sc_1_2 + sc_1_3 + sc_1_4 + sc_1_5 + sc_1_6;
    public static final String sc_2_1 = "<:sc_2_1:1164316530025177108>";
    public static final String sc_2_2 = "<:sc_2_2:1164316530926948383>";
    public static final String sc_2_3 = "<:sc_2_3:1164316532533366935>";
    public static final String sc_2_4 = "<:sc_2_4:1164316534982840422>";
    public static final String sc_2_5 = "<:sc_2_5:1164316536266309684>";
    public static final String sc_2_6 = "<:sc_2_6:1164316539789529108>";
    public static final String SC2Mention = sc_2_1 + sc_2_2 + sc_2_3 + sc_2_4 + sc_2_5 + sc_2_6;
    public static final String sc_3_1 = "<:sc_3_1:1164316650233942037>";
    public static final String sc_3_2 = "<:sc_3_2:1164316651823579177>";
    public static final String sc_3_3 = "<:sc_3_3:1164316653748764703>";
    public static final String sc_3_4 = "<:sc_3_4:1164316654948323378>";
    public static final String sc_3_5 = "<:sc_3_5:1164316657783689297>";
    public static final String SC3Mention = sc_3_1 + sc_3_2 + sc_3_3 + sc_3_4 + sc_3_5;
    public static final String sc_4_1 = "<:sc_4_1:1164316658970660998>";
    public static final String sc_4_2 = "<:sc_4_2:1164316660358991893>";
    public static final String sc_4_3 = "<:sc_4_3:1164316662510661715>";
    public static final String sc_4_4 = "<:sc_4_4:1164316663857021039>";
    public static final String sc_4_5 = "<:sc_4_5:1164335083440836708>";
    public static final String sc_4_6 = "<:sc_4_6:1164335085902909440>";
    public static final String sc_4_7 = "<:sc_4_7:1164316057448742995>";
    public static final String SC4Mention = sc_4_1 + sc_4_2 + sc_4_3 + sc_4_4 + sc_4_5 + sc_4_6 + sc_4_7;
    public static final String sc_5_1 = "<:sc_5_1:1164335087068926122>";
    public static final String sc_5_2 = "<:sc_5_2:1164335088348168232>";
    public static final String sc_5_3 = "<:sc_5_3:1164335090021716060>";
    public static final String sc_5_4 = "<:sc_5_4:1164335091682648074>";
    public static final String SC5Mention = sc_5_1 + sc_5_2 + sc_5_3 + sc_5_4;
    public static final String sc_6_1 = "<:sc_6_1:1164335092739604632>";
    public static final String sc_6_2 = "<:sc_6_2:1164335095352655882>";
    public static final String sc_6_3 = "<:sc_6_3:1164335097307201617>";
    public static final String sc_6_4 = "<:sc_6_4:1164335101111451648>";
    public static final String sc_6_5 = "<:sc_6_5:1164335103191818300>";
    public static final String SC6Mention = sc_6_1 + sc_6_2 + sc_6_3 + sc_6_4 + sc_6_5;
    public static final String sc_7_1 = "<:sc_7_1:1164335106073296956>";
    public static final String sc_7_2 = "<:sc_7_2:1164335107922989086>";
    public static final String sc_7_3 = "<:sc_7_3:1164335364824113164>";
    public static final String sc_7_4 = "<:sc_7_4:1164335111832096889>";
    public static final String sc_7_5 = "<:sc_7_5:1164335367500071062>";
    public static final String sc_7_6 = "<:sc_7_6:1164335116970098759>";
    public static final String SC7Mention = sc_7_1 + sc_7_2 + sc_7_3 + sc_7_4 + sc_7_5 + sc_7_6;
    public static final String sc_8_1 = "<:sc_8_1:1164335119046299748>";
    public static final String sc_8_2 = "<:sc_8_2:1164335243688423454>";
    public static final String sc_8_3 = "<:sc_8_3:1164335245210947644>";
    public static final String sc_8_4 = "<:sc_8_4:1164335122355597402>";
    public static final String sc_8_5 = "<:sc_8_5:1164335247429730365>";
    public static final String SC8Mention = sc_8_1 + sc_8_2 + sc_8_3 + sc_8_4 + sc_8_5;
    // END EMOJI FARM 9

    // EMOJI FARM 10 - COLOUR UNITS
    public static final String black = "<:black:1165031264743600250>";
    public static final String bloodred = "<:bloodred:1165031267532804277>";
    public static final String blue = "<:blue:1165031268950482974>";
    public static final String brown = "<:brown:1165031271253164182>";
    public static final String chocolate = "<:chocolate:1165031272515645490>";
    public static final String chrome = "<:chrome:1165031273715212318>";
    public static final String rainbow = "<:clr_rainbow:1165031518738055298>";
    public static final String rose = "<:clr_rose:1165031523045621870>";
    public static final String emerald = "<:emerald:1165031276126937150>";
    public static final String ethereal = "<:ethereal:1165036998814355497>";
    public static final String forest = "<:forest:1165031277917909033>";
    public static final String gold = "<:gold:1165031280577085450>";
    public static final String gray = "<:gray:1165031281755693136>";
    public static final String green = "<:green:1165031343227404438>";
    public static final String lavender = "<:lavender:1165031345479753770>";
    public static final String lightgray = "<:lightgray:1165031347107143680>";
    public static final String lime = "<:lime:1165031349493706872>";
    public static final String navy = "<:navy:1165031350731018311>";
    public static final String orange = "<:orange:1165031351628611678>";
    public static final String orca = "<:orca:1165031354430406726>";
    public static final String petrol = "<:petrol:1165031356074569768>";
    public static final String pink = "<:pink:1165031358226239570>";
    public static final String purple = "<:purple:1165031359933321366>";
    public static final String red = "<:red:1165031520969437224>";
    public static final String spring = "<:spring:1165031606227050616>";
    public static final String sunset = "<:sunset:1165031608538120242>";
    public static final String tan = "<:tan:1165031610308120706>";
    public static final String teal = "<:teal:1165031612933746800>";
    public static final String turquoise = "<:turquoise:1165031614779232286>";
    public static final String yellow = "<:yellow:1165036996557807676>";
    public static final String splitbloodred = "<:splitbloodred:1165031525927104512>";
    public static final String splitblue = "<:splitblue:1165031527353168083>";
    public static final String splitchocolate = "<:splitchocolate:1165031529827807262>";
    public static final String splitemerald = "<:splitemerald:1165031531346145330>";
    public static final String splitgold = "<:splitgold:1165031534525419592>";
    public static final String splitgreen = "<:splitgreen:1165031536526110750>";
    public static final String splitlime = "<:splitlime:1165031538761674814>";
    public static final String splitnavy = "<:splitnavy:1165031600795439154>";
    public static final String splitorange = "<:splitorange:1165031603307819008>";
    public static final String splitpetrol = "<:splitpetrol:1165037000127160341>";
    public static final String splitpink = "<:splitpink:1165031604784205935>";
    public static final String splitpurple = "<:splitpurple:1165037001465155795>";
    public static final String splitrainbow = "<:splitrainbow:1165037003671351356>";
    public static final String splitred = "<:splitred:1165037005479096370>";
    public static final String splittan = "<:splittan:1165037008666775572>";
    public static final String splitteal = "<:splitteal:1165037010910728242>";
    public static final String splittorquoise = "<:splittorquoise:1165037013486022726>";
    public static final String splityellow = "<:splityellow:1165037014995963965>";

    // END EMOJI FARM 10

    // ANOMOLIES
    public static final String Supernova = "<:supernova:1137029705946640445>";
    public static final String Asteroid = "<:asteroidbelt:1137029604050210846>";
    public static final String GravityRift = "<:grift:1136836649003782254>";
    public static final String Nebula = "<:nebula:1137029690004090900>";

    // TECHNOLOGY
    public static final String PropulsionTech = "<:Propulsiontech:947250608145068074>";
    public static final String PropulsionDisabled = "<:propulsionDisabled:1120031664458960896>";
    public static final String Propulsion2 = "<:propulsion2:1120031821304959098>";
    public static final String Propulsion3 = "<:propulsion3:1120031823569895584>";
    public static final String BioticTech = "<:Biotictech:947250608107315210>";
    public static final String BioticDisabled = "<:bioticDisabled:1120031652299681963>";
    public static final String Biotic2 = "<:biotic2:1120031649732771841>";
    public static final String Biotic3 = "<:biotic3:1120031651167227944>";
    public static final String CyberneticTech = "<:Cybernetictech:947250608149245972>";
    public static final String CyberneticDisabled = "<:cyberneticDisabled:1120031658582737047>";
    public static final String Cybernetic2 = "<:cybernetic2:1120031655374102718>";
    public static final String Cybernetic3 = "<:cybernetic3:1120031656766619658>";
    public static final String WarfareTech = "<:Warfaretech:947250607855644743>";
    public static final String WarfareDisabled = "<:warfareDisabled:1120031828384956446>";
    public static final String Warfare2 = "<:warfare2:1120031824891093113>";
    public static final String Warfare3 = "<:warfare3:1120031825851584523>";
    public static final String UnitUpgradeTech = "<:UnitUpgradeTech:1085495745018331146>";
    public static final String UnitTechSkip = "<:UnitTechSkip:1151926553488412702>";
    public static final String NonUnitTechSkip = "<:NonUnitTechSkip:1151926572278874162>";

    // GOOD DOGS
    public static final String GoodDog = "<:GoodDog:1068567308819255316>";
    public static final String Ozzie = "<:Ozzie:1069446695689134140>";
    public static final String Summer = "<:Summer:1070283656037412884>";
    public static final String Charlie = "<:Charlie:1096774713352650812>";
    public static final String Scout = "<:scout_face_2:1071965098639360081>";

    // LIST OF GOOD DOGS
    public static final List<String> GoodDogs = Arrays.asList(GoodDog, Ozzie, Summer, Charlie, Scout);

    // TOES
    public static final String NoToes = "<:NoToes:1082312430706774016>";
    public static final String OneToe = "<:OneToe:1082312540450734111>";
    public static final String TwoToes = "<:TwoToes:1082312612978634793>";
    public static final String ThreeToes = "<:ThreeToes:1082312642275852420>";
    public static final String FourToes = "<:FourToes:1082312665982054421>";
    public static final String FiveToes = "<:FiveToes:1082312691697332254>";
    public static final String SixToes = "<:SixToes:1082312718666694737>";
    public static final String SevenToes = "<:SevenToes:1082312739952808017>";
    public static final String EightToes = "<:EightToes:1082312762572685352>";
    public static final String NineToes = "<:NineToes:1082312788241817661>";

    // OTHER
    public static final String WHalpha = "<:WHalpha:1056593618250518529>";
    public static final String WHbeta = "<:WHbeta:1056593596012302366>";
    public static final String WHgamma = "<:WHgamma:1056593568766111814>";
    public static final String CreussAlpha = "<:CreussAlpha:1163507874065031313>";
    public static final String CreussBeta = "<:CreussBeta:1163507875818242209>";
    public static final String CreussGamma = "<:CreussGamma:1163507872404090960>";
    public static final String LegendaryPlanet = "<:Legendaryplanet:947250386375426108>";
    public static final String SpeakerToken = "<:Speakertoken:965363466821050389>";
    public static final String Sabotage = "<:sabotage:962784058159546388>";
    public static final String NoSabotage = "<:nosabo:962783456541171712>";
    public static final String nowhens = "<:nowhens:962921609671364658>";
    public static final String noafters = "<:noafters:962923748938362931>";
    public static final String Wash = "<a:Wash:1065334637532041298>";
    public static final String winemaking = "<:winemaking:1064244730000584754>";
    public static final String BortWindow = "<:bortwindow:1032312829585399880>";
    public static final String SpoonAbides = "<:TheSpoonAbides:1003482035953860699>";
    public static final String Absol = "<:Absol:1079473959248068701>";
    public static final String DiscordantStars = "<:DS:1081308924084506706>";
    public static final String AsyncTI4Logo = "<:asyncti4:959703535258333264>";
    public static final String TIGL = "<:TIGL:1111086048974475305>";
    public static final String RollDice = "<a:rolldice:1131416916330811392>";
    public static final String BLT = "<:BLT:1080954650339065866>";

    // HOMEBREW
    public static final String ActionDeck2 = "<:actiondeck2:1156988842331623506>"; // Symbol for Will's Action Deck 2 mod
    public static final String Eronous = "<:eronous:1157307622920290444>"; // Symbol for Eronous' stuff
    public static final String IgnisAurora = "<:ignis_aurora:1165445236957388800>"; // Symbol for Ignis Aurora's stuff

    // LIST OF SYMBOLS FOR FOG STUFF
    public static final List<String> symbols = Arrays.asList(
            warsun, spacedock, pds, mech, infantry, flagship, fighter, dreadnought, destroyer, carrier, cruiser, HFrag,
            CFrag, IFrag, UFrag, Relic, Cultural, Industrial, Hazardous, Frontier, SecretObjective, Public1, Public2,
            tg, comm, Sleeper, influence, resources, SemLord, ActionCard, Agenda, PN, NoToes, CyberneticTech,
            PropulsionTech, BioticTech, WarfareTech, WHalpha, WHbeta, WHgamma, LegendaryPlanet, SpeakerToken,
            BortWindow);

    // private static List<String> testingEmoji = Arrays.asList("🐷","🙉","💩","👺","🥵","🤯","😜","👀","🦕","🐦","🦏","🐸");

    @NotNull
    public static String getRandomizedEmoji(int value, String messageID) {
        List<String> symbols = new ArrayList<>(Emojis.symbols);
        // symbols = new ArrayList<>(testingEmoji);
        Random seed = messageID == null ? ThreadLocalRandom.current() : new Random(messageID.hashCode());
        Collections.shuffle(symbols, seed);
        value = value % symbols.size();
        return symbols.get(value);
    }

    public static String getRandomSemLore() {
        List<String> semLores = new ArrayList<>(Emojis.SemLores);
        Random seed = ThreadLocalRandom.current();
        Collections.shuffle(semLores, seed);
        return semLores.get(0);
    }

    public static String getRandomGoodDog() {
        List<String> goodDogs = new ArrayList<>(Emojis.GoodDogs);
        Random seed = ThreadLocalRandom.current();
        Collections.shuffle(goodDogs, seed);
        return goodDogs.get(0);
    }

    @NotNull
    public static String getFactionIconFromDiscord(String faction) {
        if (faction == null) {
            return getRandomizedEmoji(0, null);
        }
        return switch (faction.toLowerCase()) {
            case "arborec" -> Emojis.Arborec;
            case "argent" -> Emojis.Argent;
            case "cabal" -> Emojis.Cabal;
            case "empyrean" -> Emojis.Empyrean;
            case "ghost", "creuss" -> Emojis.Ghost;
            case "hacan" -> Emojis.Hacan;
            case "jolnar" -> Emojis.Jolnar;
            case "l1z1x" -> Emojis.L1Z1X;
            case "letnev" -> Emojis.Letnev;
            case "yssaril" -> Emojis.Yssaril;
            case "mahact" -> Emojis.Mahact;
            case "mentak" -> Emojis.Mentak;
            case "muaat" -> Emojis.Muaat;
            case "naalu" -> Emojis.Naalu;
            case "naaz" -> Emojis.Naaz;
            case "nekro" -> Emojis.Nekro;
            case "nomad" -> Emojis.Nomad;
            case "saar" -> Emojis.Saar;
            case "sardakk" -> Emojis.Sardakk;
            case "sol" -> Emojis.Sol;
            case "titans" -> Emojis.Titans;
            case "winnu" -> Emojis.Winnu;
            case "xxcha" -> Emojis.Xxcha;
            case "yin" -> Emojis.Yin;

            case "lazax" -> Emojis.Lazax;

            case "keleres", "keleresx", "keleresm", "keleresa" -> Emojis.Keleres;

            case "augers" -> Emojis.augers;
            case "axis" -> Emojis.axis;
            case "bentor" -> Emojis.bentor;
            case "blex", "kyro" -> Emojis.blex;
            case "celdauri" -> Emojis.celdauri;
            case "cheiran" -> Emojis.cheiran;
            case "cymiae" -> Emojis.cymiae;
            case "dihmohn" -> Emojis.dihmohn;
            case "edyn" -> Emojis.edyn;
            case "florzen" -> Emojis.florzen;
            case "freesystems" -> Emojis.freesystems;
            case "ghemina" -> Emojis.ghemina;
            case "ghoti" -> Emojis.ghoti;
            case "gledge" -> Emojis.gledge;
            case "khrask" -> Emojis.khrask;
            case "kjalengard" -> Emojis.kjalengard;
            case "kollecc" -> Emojis.kollecc;
            case "kolume" -> Emojis.kolume;
            case "kortali" -> Emojis.kortali;
            case "lanefir" -> Emojis.lanefir;
            case "lizho" -> Emojis.lizho;
            case "mirveda" -> Emojis.mirveda;
            case "mortheus" -> Emojis.mortheus;
            case "mykomentori" -> Emojis.mykomentori;
            case "nivyn" -> Emojis.nivyn;
            case "nokar" -> Emojis.nokar;
            case "olradin" -> Emojis.olradin;
            case "rohdhna" -> Emojis.rohdhna;
            case "tnelis" -> Emojis.tnelis;
            case "vaden" -> Emojis.vaden;
            case "vaylerian" -> Emojis.vaylerian;
            case "veldyr" -> Emojis.veldyr;
            case "zealots" -> Emojis.zealots;
            case "zelian" -> Emojis.zelian;

            case "admins" -> Emojis.AdminsFaction;
            case "qulane" -> Emojis.Qulane;

            case "franken1" -> Emojis.OneToe;
            case "franken2" -> Emojis.TwoToes;
            case "franken3" -> Emojis.ThreeToes;
            case "franken4" -> Emojis.FourToes;
            case "franken5" -> Emojis.FiveToes;
            case "franken6" -> Emojis.SixToes;
            case "franken7" -> Emojis.SevenToes;
            case "franken8" -> Emojis.EightToes;

            default -> getRandomizedEmoji(0, null);
        };
    }

    public static String getPlanetEmoji(String planet) {
        return switch (planet.toLowerCase()) {
            case "0.0.0" -> Emojis.Planet000;
            case "abaddon" -> Emojis.Abaddon;
            case "abyz" -> Emojis.Abyz;
            case "accoen" -> Emojis.Accoen;
            case "acheron" -> Emojis.Acheron;
            case "alioprima" -> Emojis.AlioPrima;
            case "ang" -> Emojis.Ang;
            case "arcprime" -> Emojis.ArcPrime;
            case "archonren", "archonrenk" -> Emojis.ArchonRen;
            case "archontau", "archontauk" -> Emojis.ArchonTau;
            case "archonvail" -> Emojis.ArchonVail;
            case "arcturus" -> Emojis.Arcturus;
            case "arinam" -> Emojis.Arinam;
            case "arnor" -> Emojis.Arnor;
            case "arretze" -> Emojis.Arretze;
            case "ashtroth" -> Emojis.Ashtroth;
            case "atlas" -> Emojis.Atlas;
            case "avar", "avark" -> Emojis.Avar;
            case "bakal" -> Emojis.Bakal;
            case "bereg" -> Emojis.Bereg;
            case "cealdri" -> Emojis.Cealdri;
            case "centauri" -> Emojis.Centauri;
            case "cormund" -> Emojis.Cormund;
            case "corneeq" -> Emojis.Corneeq;
            case "creuss" -> Emojis.Creuss;
            case "dalbootha" -> Emojis.DalBootha;
            case "darien" -> Emojis.Darien;
            case "druaa" -> Emojis.Druaa;
            case "elysium" -> Emojis.Elysium;
            case "everra" -> Emojis.Everra;
            case "fria" -> Emojis.Fria;
            case "gral" -> Emojis.Gral;
            case "hercant" -> Emojis.Hercant;
            case "hopesend" -> Emojis.HopesEnd;
            case "ixth" -> Emojis.Ixth;
            case "jeolir" -> Emojis.JeolIr;
            case "jol" -> Emojis.Jol;
            case "jord" -> Emojis.Jord;
            case "kamdorn" -> Emojis.Kamdorn;
            case "kraag" -> Emojis.Kraag;
            case "lazar" -> Emojis.Lazar;
            case "lirtaiv" -> Emojis.LirtaIV;
            case "lisis" -> Emojis.Lisis;
            case "lisisii" -> Emojis.LisisII;
            case "lodor" -> Emojis.Lodor;
            case "loki" -> Emojis.Loki;
            case "lor" -> Emojis.Lor;
            case "maaluuk" -> Emojis.Maaluuk;
            case "mallice" -> Emojis.Mallice;
            case "mr" -> Emojis.Mecatol;
            case "meer" -> Emojis.Meer;
            case "meharxull" -> Emojis.MeharXull;
            case "mellon" -> Emojis.Mellon;
            case "mollprimus", "mollprimusk" -> Emojis.MollPrimus;
            case "mordai" -> Emojis.Mordai;
            case "muaat" -> Emojis.PlanetMuaat;
            case "naazir" -> Emojis.Naazir;
            case "nar" -> Emojis.Nar;
            case "nestphar" -> Emojis.Nestphar;
            case "newalbion" -> Emojis.NewAlbion;
            case "perimeter" -> Emojis.Perimeter;
            case "primor" -> Emojis.Primor;
            case "quann" -> Emojis.Quann;
            case "qucenn" -> Emojis.Qucenn;
            case "quinarra" -> Emojis.Quinarra;
            case "rahg" -> Emojis.Rahg;
            case "rarron" -> Emojis.Rarron;
            case "resculon" -> Emojis.Resculon;
            case "retillon" -> Emojis.Retillon;
            case "rigeli" -> Emojis.RigelI;
            case "rigelii" -> Emojis.RigelII;
            case "rigeliii" -> Emojis.RigelIII;
            case "rokha" -> Emojis.Rokha;
            case "sakulag" -> Emojis.Sakulag;
            case "saudor" -> Emojis.Saudor;
            case "semlore" -> getRandomSemLore();
            case "shalloq" -> Emojis.Shalloq;
            case "siig" -> Emojis.Siig;
            case "starpoint" -> Emojis.Starpoint;
            case "tarmann" -> Emojis.Tarmann;
            case "tequran" -> Emojis.Tequran;
            case "thedark" -> Emojis.TheDark;
            case "thibah" -> Emojis.Thibah;
            case "torkan" -> Emojis.Torkan;
            case "trenlak" -> Emojis.Trenlak;
            case "valk", "valkk" -> Emojis.Valk;
            case "vefut" -> Emojis.Vefut;
            case "vegamajor" -> Emojis.VegaMajor;
            case "vegaminor" -> Emojis.VegaMinor;
            case "velnor" -> Emojis.Velnor;
            case "vorhal" -> Emojis.Vorhal;
            case "wellon" -> Emojis.Wellon;
            case "winnu" -> Emojis.PlanetWinnu;
            case "wrenterra" -> Emojis.WrenTerra;
            case "xanhact" -> Emojis.Xanhact;
            case "xxehan" -> Emojis.Xxehan;
            case "ylir", "ylirk" -> Emojis.Ylir;
            case "zohbat" -> Emojis.Zohbat;

            default -> Emojis.SemLore;
        };
    }

    public static String getColourEmojis(String colour) {
        return switch (colour) {
            case "gray" -> Emojis.gray + "**Gray**";
            case "black" -> Emojis.black + "**Black**";
            case "blue" -> Emojis.blue + "**Blue**";
            case "green" -> Emojis.green + "**Green**";
            case "orange" -> Emojis.orange + "**Orange**";
            case "pink" -> Emojis.pink + "**Pink**";
            case "purple" -> Emojis.purple + "**Purple**";
            case "red" -> Emojis.red + "**Red**";
            case "yellow" -> Emojis.yellow + "**Yellow**";
            case "petrol" -> Emojis.petrol + "**Petrol**";
            case "brown" -> Emojis.brown + "**Brown**";
            case "tan" -> Emojis.tan + "**Tan**";
            case "forest" -> Emojis.forest + "**Forest**";
            case "chrome" -> Emojis.chrome + "**Chrome**";
            case "sunset" -> Emojis.sunset + "**Sunset**";
            case "turquoise" -> Emojis.turquoise + "**Turquoise**";
            case "gold" -> Emojis.gold + "**Gold**";
            case "lightgray" -> Emojis.lightgray + "**Lightgray**";
            case "teal" -> Emojis.teal + "**Teal**";
            case "bloodred" -> Emojis.bloodred + "**Bloodred**";
            case "emerald" -> Emojis.emerald + "**Emerald**";
            case "navy" -> Emojis.navy + "**Navy**";
            case "rose" -> Emojis.rose + "**Rose**";
            case "lime" -> Emojis.lime + "**Lime**";
            case "lavender" -> Emojis.lavender + "**Lavender**";
            case "spring" -> Emojis.spring + "**Spring**";
            case "chocolate" -> Emojis.chocolate + "**Chocolate**";
            case "rainbow" -> Emojis.rainbow + "**Rainbow**";
            case "ethereal" -> Emojis.ethereal + "**Ethereal**";
            case "orca" -> Emojis.orca + "**Orca**";
            case "splitred" -> Emojis.splitred + "**Splitred**";
            case "splitblue" -> Emojis.splitblue + "**Splitblue**";
            case "splitgreen" -> Emojis.splitgreen + "**Splitgreen**";
            case "splitpurple" -> Emojis.splitpurple + "**Splitpurple**";
            case "splitorange" -> Emojis.splitorange + "**Splitorange**";
            case "splityellow" -> Emojis.splityellow + "**Splityellow**";
            case "splitpink" -> Emojis.splitpink + "**Splitpink**";
            case "splitgold" -> Emojis.splitgold + "**Splitgold**";
            case "splitlime" -> Emojis.splitlime + "**Splitlime**";
            case "splittan" -> Emojis.splittan + "**Splittan**";
            case "splitteal" -> Emojis.splitteal + "**Splitteal**";
            case "splitturquoise" -> Emojis.splittorquoise + "**Splitturquoise**";
            case "splitbloodred" -> Emojis.splitbloodred + "**Splitbloodred**";
            case "splitchocolate" -> Emojis.splitchocolate + "**Splitchocolate**";
            case "splitemerald" -> Emojis.splitemerald + "**Splitemerald**";
            case "splitnavy" -> Emojis.splitnavy + "**Splitnavy**";
            case "splitpetrol" -> Emojis.splitpetrol + "**Splitpetrol**";
            case "splitrainbow" -> Emojis.splitrainbow + "**Splitrainbow**";
            default -> colour;
        };
    }

    public static String getInfluenceEmoji(int count) {
        return switch (count) {
            case 0 -> Emojis.Influence_0;
            case 1 -> Emojis.Influence_1;
            case 2 -> Emojis.Influence_2;
            case 3 -> Emojis.Influence_3;
            case 4 -> Emojis.Influence_4;
            case 5 -> Emojis.Influence_5;
            case 6 -> Emojis.Influence_6;
            case 7 -> Emojis.Influence_7;
            case 8 -> Emojis.Influence_8;
            case 9 -> Emojis.Influence_9;
            default -> Emojis.influence + count;
        };
    }

    public static String getResourceEmoji(int count) {
        return switch (count) {
            case 0 -> Emojis.Resources_0;
            case 1 -> Emojis.Resources_1;
            case 2 -> Emojis.Resources_2;
            case 3 -> Emojis.Resources_3;
            case 4 -> Emojis.Resources_4;
            case 5 -> Emojis.Resources_5;
            case 6 -> Emojis.Resources_6;
            case 7 -> Emojis.Resources_7;
            case 8 -> Emojis.Resources_8;
            case 9 -> Emojis.Resources_9;
            default -> Emojis.resources + count;
        };
    }

    public static String getToesEmoji(int count) {
        return switch (count) {
            case 0 -> Emojis.NoToes;
            case 1 -> Emojis.OneToe;
            case 2 -> Emojis.TwoToes;
            case 3 -> Emojis.ThreeToes;
            case 4 -> Emojis.FourToes;
            case 5 -> Emojis.FiveToes;
            case 6 -> Emojis.SixToes;
            case 7 -> Emojis.SevenToes;
            case 8 -> Emojis.EightToes;
            case 9 -> Emojis.NineToes;
            default -> Emojis.NoToes + count;
        };
    }

    public static String getFactionLeaderEmoji(Leader leader) {
        return getEmojiFromDiscord(leader.getId());
    }

    /**
     * Takes an emoji's name string and returns its full name including ID.
     * 
     * @emojiName the name of the emoji as entered on the Emoji section of the server
     * @return the name of the emoji including ID
     */
    public static String getEmojiFromDiscord(String emojiName) {
        return switch (emojiName.toLowerCase()) {
            // EXPLORATION
            case "hfrag" -> Emojis.HFrag;
            case "cfrag" -> Emojis.CFrag;
            case "ifrag" -> Emojis.IFrag;
            case "ufrag" -> Emojis.UFrag;
            case "relic" -> Emojis.Relic;
            case "cultural" -> Emojis.Cultural;
            case "industrial" -> Emojis.Industrial;
            case "hazardous" -> Emojis.Hazardous;
            case "frontier" -> Emojis.Frontier;

            // CARDS
            case "sc1" -> Emojis.SC1;
            case "sc2" -> Emojis.SC2;
            case "sc3" -> Emojis.SC3;
            case "sc4" -> Emojis.SC4;
            case "sc5" -> Emojis.SC5;
            case "sc6" -> Emojis.SC6;
            case "sc7" -> Emojis.SC7;
            case "sc8" -> Emojis.SC8;
            case "sc1back" -> Emojis.SC1Back;
            case "sc2back" -> Emojis.SC2Back;
            case "sc3back" -> Emojis.SC3Back;
            case "sc4back" -> Emojis.SC4Back;
            case "sc5back" -> Emojis.SC5Back;
            case "sc6back" -> Emojis.SC6Back;
            case "sc7back" -> Emojis.SC7Back;
            case "sc8back" -> Emojis.SC8Back;
            case "actioncard" -> Emojis.ActionCard;
            case "agenda" -> Emojis.Agenda;
            case "pn" -> Emojis.PN;

            // OBJECTIVES
            case "secretobjective" -> Emojis.SecretObjective;
            case "public1" -> Emojis.Public1;
            case "public2" -> Emojis.Public2;
            case "public1alt" -> Emojis.Public1alt;
            case "public2alt" -> Emojis.Public2alt;
            case "secretobjectivealt" -> Emojis.SecretObjectiveAlt;

            // COMPONENTS
            case "tg" -> Emojis.tg;
            case "comm" -> Emojis.comm;
            case "sleeper" -> Emojis.Sleeper;
            case "sleeperb" -> Emojis.SleeperB;

            // UNITS
            case "warsun" -> Emojis.warsun;
            case "spacedock" -> Emojis.spacedock;
            case "pds" -> Emojis.pds;
            case "mech" -> Emojis.mech;
            case "infantry" -> Emojis.infantry;
            case "flagship" -> Emojis.flagship;
            case "fighter" -> Emojis.fighter;
            case "dreadnought" -> Emojis.dreadnought;
            case "destroyer" -> Emojis.destroyer;
            case "carrier" -> Emojis.carrier;
            case "cruiser" -> Emojis.cruiser;

            // LEADERS - AGENTS
            case "arborecagent" -> Emojis.ArborecAgent;
            case "argentagent" -> Emojis.ArgentAgent;
            case "cabalagent" -> Emojis.CabalAgent;
            case "ghostagent", "creussagent" -> Emojis.CreussAgent;
            case "empyreanagent" -> Emojis.EmpyreanAgent;
            case "hacanagent" -> Emojis.HacanAgent;
            case "jolnaragent" -> Emojis.JolNarAgent;
            case "keleresagent" -> Emojis.KeleresAgent;
            case "l1z1xagent" -> Emojis.L1Z1XAgent;
            case "letnevagent" -> Emojis.LetnevAgent;
            case "mahactagent" -> Emojis.MahactAgent;
            case "mentakagent" -> Emojis.MentakAgent;
            case "muaatagent" -> Emojis.MuaatAgent;
            case "naaluagent" -> Emojis.NaaluAgent;
            case "naazagent" -> Emojis.NaazAgent;
            case "nekroagent" -> Emojis.NekroAgent;
            case "nomadagentartuno" -> Emojis.NomadAgentArtuno;
            case "nomadagentmercer" -> Emojis.NomadAgentMercer;
            case "nomadagentthundarian" -> Emojis.NomadAgentThundarian;
            case "sardakkagent" -> Emojis.SardakkAgent;
            case "saaragent" -> Emojis.SaarAgent;
            case "solagent" -> Emojis.SolAgent;
            case "titansagent" -> Emojis.TitansAgent;
            case "winnuagent" -> Emojis.WinnuAgent;
            case "xxchaagent" -> Emojis.XxchaAgent;
            case "yinagent" -> Emojis.YinAgent;
            case "yssarilagent" -> Emojis.YssarilAgent;

            // LEADERS - COMMANDERS
            case "arboreccommander" -> Emojis.ArborecCommander;
            case "argentcommander" -> Emojis.ArgentCommander;
            case "cabalcommander" -> Emojis.CabalCommander;
            case "ghostcommander", "creusscommander" -> Emojis.CreussCommander;
            case "empyreancommander" -> Emojis.EmpyreanCommander;
            case "hacancommander" -> Emojis.HacanCommander;
            case "jolnarcommander" -> Emojis.JolNarCommander;
            case "kelerescommander" -> Emojis.MentakAgent;
            case "l1z1xcommander" -> Emojis.L1Z1XCommander;
            case "letnevcommander" -> Emojis.LetnevCommander;
            case "mahactcommander" -> Emojis.MahactCommander;
            case "mentakcommander" -> Emojis.MentakCommander;
            case "muaatcommander" -> Emojis.MuaatCommander;
            case "naalucommander" -> Emojis.NaaluCommander;
            case "naazcommander" -> Emojis.NaazCommander;
            case "nekrocommander" -> Emojis.NekroCommander;
            case "nomadcommander" -> Emojis.NomadCommander;
            case "sardakkcommander" -> Emojis.SardakkCommander;
            case "saarcommander" -> Emojis.SaarCommander;
            case "solcommander" -> Emojis.SolCommander;
            case "titanscommander" -> Emojis.TitansCommander;
            case "winnucommander" -> Emojis.WinnuCommander;
            case "xxchacommander" -> Emojis.XxchaCommander;
            case "yincommander" -> Emojis.YinCommander;
            case "yssarilcommander" -> Emojis.YssarilCommander;

            // LEADERS - HEROES
            case "arborechero" -> Emojis.ArborecHero;
            case "argenthero" -> Emojis.ArgentHero;
            case "cabalhero" -> Emojis.CabalHero;
            case "ghosthero", "creusshero" -> Emojis.CreussHero;
            case "empyreanhero" -> Emojis.EmpyreanHero;
            case "hacanhero" -> Emojis.HacanHero;
            case "jolnarhero" -> Emojis.JolNarHero;
            case "keleresherokuuasi" -> Emojis.KeleresHeroKuuasi;
            case "keleresheroodlynn" -> Emojis.KeleresHeroOdlynn;
            case "keleresheroharka" -> Emojis.KeleresHeroHarka;
            case "l1z1xhero" -> Emojis.L1Z1XHero;
            case "letnevhero" -> Emojis.LetnevHero;
            case "mahacthero" -> Emojis.MahactHero;
            case "mentakhero" -> Emojis.MentakHero;
            case "muaathero" -> Emojis.MuaatHero;
            case "naaluhero" -> Emojis.NaaluHero;
            case "naazhero" -> Emojis.NaazHero;
            case "nekrohero" -> Emojis.NekroHero;
            case "nomadhero" -> Emojis.NomadHero;
            case "sardakkhero" -> Emojis.SardakkHero;
            case "saarhero" -> Emojis.SaarHero;
            case "solhero" -> Emojis.SolHero;
            case "titanshero" -> Emojis.TitansHero;
            case "winnuhero" -> Emojis.WinnuHero;
            case "xxchahero" -> Emojis.XxchaHero;
            case "yinhero" -> Emojis.YinHero;
            case "yssarilhero" -> Emojis.YssarilHero;

            // DS LEADERS
            case "augersagent" -> Emojis.AugersAgent;
            case "augerscommander" -> Emojis.AugersCommander;
            case "augershero" -> Emojis.AugersHero;
            case "axisagent" -> Emojis.AxisAgent;
            case "axiscommander" -> Emojis.AxisCommander;
            case "axishero" -> Emojis.AxisHero;
            case "bentoragent" -> Emojis.BentorAgent;
            case "bentorcommander" -> Emojis.BentorCommander;
            case "bentorhero" -> Emojis.BentorHero;
            case "blexagent" -> Emojis.BlexAgent;
            case "blexcommander" -> Emojis.BlexCommander;
            case "blexhero" -> Emojis.BlexHero;
            case "celdauriagent" -> Emojis.CeldauriAgent;
            case "celdauricommander" -> Emojis.CeldauriCommander;
            case "celdaurihero" -> Emojis.CeldauriHero;
            case "cheiranagent" -> Emojis.CheiranAgent;
            case "cheirancommander" -> Emojis.CheiranCommander;
            case "cheiranhero" -> Emojis.CheiranHero;
            case "gheminaagent" -> Emojis.GheminaAgent;
            case "gheminacommander" -> Emojis.GheminaCommander;
            case "gheminaherolady" -> Emojis.GheminaHeroLady;
            case "gheminaherolord" -> Emojis.GheminaHeroLord;
            case "cymiaeagent" -> Emojis.CymiaeAgent;
            case "cymiaecommander" -> Emojis.CymiaeCommander;
            case "cymiaehero" -> Emojis.CymiaeHero;
            case "dihmohnagent" -> Emojis.DihmohnAgent;
            case "dihmohncommander" -> Emojis.DihmohnCommander;
            case "dihmohnhero" -> Emojis.DihmohnHero;
            case "edynagent" -> Emojis.EdynAgent;
            case "edyncommander" -> Emojis.EdynCommander;
            case "edynhero" -> Emojis.EdynHero;
            case "florzenagent" -> Emojis.FlorzenAgent;
            case "florzencommander" -> Emojis.FlorzenCommander;
            case "florzenhero" -> Emojis.FlorzenHero;
            case "freesystemcommander" -> Emojis.FreesystemCommander;
            case "freesystemhero" -> Emojis.FreesystemHero;
            case "freesystemsagent" -> Emojis.FreesystemsAgent;
            case "ghotiagent" -> Emojis.GhotiAgent;
            case "ghoticommander" -> Emojis.GhotiCommander;
            case "ghotihero" -> Emojis.GhotiHero;
            case "gledgeagent" -> Emojis.GledgeAgent;
            case "gledgecommander" -> Emojis.GledgeCommander;
            case "gledgehero" -> Emojis.GledgeHero;
            case "khraskagent" -> Emojis.KhraskAgent;
            case "khraskcommander" -> Emojis.KhraskCommander;
            case "khraskhero" -> Emojis.KhraskHero;
            case "kjalengardagent" -> Emojis.KjalengardAgent;
            case "kjalengardcommander" -> Emojis.KjalengardCommander;
            case "kjalengardhero" -> Emojis.KjalengardHero;

            // OTHER
            case "whalpha" -> Emojis.WHalpha;
            case "grift" -> Emojis.GravityRift;
            case "whbeta" -> Emojis.WHbeta;
            case "whgamma" -> Emojis.WHgamma;
            case "creussalpha" -> Emojis.CreussAlpha;
            case "creussbeta" -> Emojis.CreussBeta;
            case "creussgamma" -> Emojis.CreussGamma;
            case "influence" -> Emojis.influence;
            case "resources" -> Emojis.resources;
            case "legendaryplanet" -> Emojis.LegendaryPlanet;
            case "cybernetictech" -> Emojis.CyberneticTech;
            case "propulsiontech" -> Emojis.PropulsionTech;
            case "biotictech" -> Emojis.BioticTech;
            case "warfaretech" -> Emojis.WarfareTech;
            case "unitupgradetech" -> Emojis.UnitUpgradeTech;

            default -> getRandomGoodDog();
        };
    }

    public static String getSCEmojiFromInteger(Integer strategy_card) {
        String scEmojiName = "SC" + strategy_card;
        return getEmojiFromDiscord(scEmojiName);
    }

    public static String getSCBackEmojiFromInteger(Integer strategy_card) {
        String scEmojiName = "SC" + strategy_card + "Back";
        return getEmojiFromDiscord(scEmojiName);
    }

    public static String getTGorNomadCoinEmoji(Game activeGame) {
        if (activeGame == null) return Emojis.tg;
        return activeGame.getNomadCoin() ? Emojis.nomadcoin : Emojis.tg;
    }
}
