package ti4.helpers;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.utils.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.message.BotLogger;

public class Emojis {
    // APPLICATION EMOJIS
    public static final Map<String, EmojiUnion> emojis = new HashMap<>();

    public static void initApplicationEmojis() {
        List<EmojiUnion> all = getAllApplicationEmojis();
        all.forEach(e -> emojis.put(e.getName(), e));
        reloadAllApplicationEmojis();
    }

    private static void reloadAllApplicationEmojis() {
        Map<String, Boolean> emojiExists = new HashMap<>();
        emojis.keySet().forEach(k -> emojiExists.put(k, false));

        List<File> emojiFiles = enumerateEmojiFilesRecursive(Storage.getAppEmojiDirectory());
        List<File> toUpload = new ArrayList<>();
        List<String> toDelete = new ArrayList<>();
        for (File emoji : emojiFiles) {
            EmojiUnion existingEmoji = getApplicationEmoji(emoji.getName());
            if (existingEmoji == null) {
                toUpload.add(emoji);
            } else {
                LocalDateTime lastModified = LocalDateTime.ofInstant(Instant.ofEpochMilli(emoji.lastModified()), ZoneId.systemDefault());
                OffsetDateTime emojiUploadTime = existingEmoji.asCustom().getTimeCreated();
                OffsetDateTime fileModTime = lastModified.atOffset(emojiUploadTime.getOffset());
                if (emojiUploadTime.isBefore(fileModTime)) {
                    toUpload.add(emoji);
                    toDelete.add(emoji.getName());
                }
            }
            emojiExists.put(emoji.getName(), true);
        }

        // Remove unused emojis from the application
        for (String key : emojis.keySet()) {
            if (!emojiExists.get(key)) {
                toDelete.add(key);
            }
        }

        // Delete emojis that have been removed and also out-of-date emojis
        if (!toDelete.isEmpty()) {
            BotLogger.logWithTimestamp("Deleting `" + toDelete.size() + "` emojis.");
            toDelete.parallelStream().forEach(Emojis::deleteApplicationEmoji);
            toDelete.forEach(emojis::remove);
        }

        // (Re-)Upload emojis
        if (!toUpload.isEmpty()) {
            BotLogger.logWithTimestamp("Uploading `" + toUpload.size() + "` emojis.");
            Map<String, EmojiUnion> uploaded = toUpload.parallelStream()
                .map(Emojis::createApplicationEmoji)
                .collect(Collectors.toConcurrentMap(e -> e.getName(), e -> e));
            emojis.putAll(uploaded);
        }

        if (!toDelete.isEmpty() || !toUpload.isEmpty()) {
            BotLogger.logWithTimestamp("Done updating emojis.");
        }
    }

    public static List<File> enumerateEmojiFilesRecursive(File folder) {
        List<File> filesAndDirectories = Arrays.asList(folder.listFiles());
        return filesAndDirectories.stream().flatMap(fileOrDir -> {
            if (fileOrDir == null) return null;
            if (isValidEmojiFile(fileOrDir)) return Stream.of(fileOrDir);
            if (fileOrDir.isDirectory() && !isIgnoredDirectory(fileOrDir))
                return enumerateEmojiFilesRecursive(fileOrDir).stream();
            return null;
        }).toList();
    }

    private static boolean isValidEmojiFile(File file) {
        return file.isFile() && (file.getName().endsWith(".png") || file.getName().endsWith(".jpg"));
    }

    private static boolean isIgnoredDirectory(File file) {
        List<String> names = List.of("New Server Pack");
        return names.contains(file.getName());
    }

    private static EmojiUnion getApplicationEmoji(String name) {
        if (emojis.containsKey(name))
            return emojis.get(name);
        return null;
    }

    private static List<EmojiUnion> getAllApplicationEmojis() {
        //AsyncTI4DiscordBot.jda.create
        return new ArrayList<>(); // TODO (Jazz): fill this in when we get the JDA update
    }

    private static EmojiUnion createApplicationEmoji(File emoji) {
        return null; // TODO (Jazz): fill this in when we get the JDA update
    }

    private static void deleteApplicationEmoji(String name) {
        // TODO (Jazz): fill this in when we get the JDA update
    }

    // src/main/resources/emojis
    public enum TI4Emoji {
        // ./factions
        Arborec, Ghost, Hacan, Jolnar, L1Z1X, Letnev, Mentak, Muaat, Naalu, Nekro, Saar, Sardakk, Sol, Winnu, Xxcha, Yin, Yssaril, // ./base
        Argent, Cabal, Empyrean, Mahact, Naaz, Nomad, Titans, // ./pok
        augers, axis, bentor, blex, kyro, celdauri, cheiran, cymiae, dihmohn, edyn, florzen, freesystems, ghemina, ghoti, gledge, khrask, kjalengard, kollecc, // ./ds
        kolume, kortali, lanefir, lizho, mirveda, mortheus, mykomentori, nivyn, nokar, olradin, rohdhna, tnelis, vaden, vaylerian, veldyr, zealots, zelian, // ./ds
        Lazax, Neutral, RandomFaction, AdminsFaction, Qulane, echoes, enclave, raven, syndicate, terminator, // ./other

        // Exploration / Traits
        HFrag, CFrag, IFrag, UFrag, Relic, Cultural, Industrial, Hazardous, Frontier,

        // Normal Strategy Cards
        SC1, SC1Back, SC2, SC2Back, SC3, SC3Back, SC4, SC4Back, SC5, SC5Back, SC6, SC6Back, SC7, SC7Back, SC8, SC8Back,

        // Strat Card Pings, [EMOJI FARM 9]
        sc_1_1, sc_1_2, sc_1_3, sc_1_4, sc_1_5, sc_1_6, //
        sc_2_1, sc_2_2, sc_2_3, sc_2_4, sc_2_5, sc_2_6, //
        sc_3_1, sc_3_2, sc_3_3, sc_3_4, sc_3_5, //
        sc_4_1, sc_4_2, sc_4_3, sc_4_4, sc_4_5, sc_4_6, sc_4_7, //
        sc_5_1, sc_5_2, sc_5_3, sc_5_4, //
        sc_6_1, sc_6_2, sc_6_3, sc_6_4, sc_6_5, //
        sc_7_1, sc_7_2, sc_7_3, sc_7_4, sc_7_5, sc_7_6, //
        sc_8_1, sc_8_2, sc_8_3, sc_8_4, sc_8_5, //

        // Other Cards
        ActionCard, ActionCardAlt, Agenda, AgendaAlt, AgendaWhite, AgendaBlack, PN, PNALt, RelicCard, CulturalCard, HazardousCard, IndustrialCard, FrontierCard, EventCard,

        // Colors
        black, bloodred, blue, brown, chocolate, chrome, rainbow, rose, emerald, ethereal, forest, gold, gray, green, lavender, //
        lightgray, lime, navy, orange, orca, petrol, pink, purple, red, spring, sunset, tan, teal, turquoise, yellow, //
        splitbloodred, splitblue, splitchocolate, splitemerald, splitgold, splitgreen, splitlime, splitnavy, splitorange, //
        splitpetrol, splitpink, splitpurple, splitrainbow, splitred, splittan, splitteal, splitturquoise, splityellow;

        public Emoji asEmoji() {
            String mention = toString();
            if (mention.isBlank()) return null;
            return Emoji.fromFormatted(mention);
        }

        public String toString() {
            String emoji = getAppEmoji(name());
            return emoji == null ? "" : emoji;
        }
    }

    public static String getAppEmoji(String emojiName) {
        // Check the application emojis first
        if (emojis.containsKey(emojiName) && emojis.get(emojiName) instanceof CustomEmoji emoji) {
            return emoji.getAsMention();
        }

        // Find Emoji in JDA
        List<RichCustomEmoji> candidates = AsyncTI4DiscordBot.jda.getEmojisByName(emojiName, true);
        for (RichCustomEmoji emoji : candidates) {
            if (emoji == null) continue;
            if (!emoji.isAvailable()) continue;
            return emoji.getAsMention();
        }
        return getEmojiFromDiscord(emojiName);
    }

    // FACTIONS
    public static final String Arborec = "<:Arborec:1156670455856513175>";
    public static final String Argent = "<:Argent:1156670457123192873>";
    public static final String Cabal = "<:Cabal:1156670460638015600>";
    public static final String Empyrean = "<:Empyrean:1156670516623577268>";
    public static final String Ghost = "<:Creuss:1156670489771651324>";
    public static final String Hacan = "<:Hacan:1156670539688054794>";
    public static final String Jolnar = "<:JolNar:1156670564342181908>";
    public static final String L1Z1X = "<:L1Z1X:1156670567198507208>";
    public static final String Letnev = "<:Letnev:1156670569471803422>";
    public static final String Yssaril = "<:Yssaril:1156670725495726150>";
    public static final String Mahact = "<:Mahact:1156670571552190484>";
    public static final String Mentak = "<:Mentak:1156670601851846757>";
    public static final String Muaat = "<:Muaat:1156670603110129704>";
    public static final String Naalu = "<:Naalu:1156670604393590784>";
    public static final String Naaz = "<:Naaz:1156670606532677782>";
    public static final String Nekro = "<:Nekro:1156670630700257310>";
    public static final String Nomad = "<:Nomad:1156670632705130526>";
    public static final String Saar = "<:Saar:1156670637226590228>";
    public static final String Sardakk = "<:Sardakk:1156670656570740827>";
    public static final String Sol = "<:Sol:1156670659804532736>";
    public static final String Titans = "<:Titans:1156670697515532350>";
    public static final String Winnu = "<:Winnu:1156670722039611524>";
    public static final String Xxcha = "<:Xxcha:1156670723541180547>";
    public static final String Yin = "<:Yin:1156670724438769754>";
    public static final String Lazax = "<:Lazax:946891797639073884>";
    public static final String Neutral = "<:neutral:1269693830639390720>";
    public static final String Keleres = "<:Keleres:1156670565793398875>";
    public static final String RandomFaction = "<a:factions:1193971011633291284>";

    // FACTIONS - DISCORDANT STARS
    public static final String augers = "<:augurs:1082705489722363904>";
    public static final String axis = "<:axis:1082705549092737044>";
    public static final String bentor = "<:bentor:1082705559897264199>";
    public static final String blex = "<:blex:1082705569351204995>";
    public static final String kyro = "<:blex:1082705569351204995>";
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
    public static final String Qulane = "<:qulane:1165445638096420895>";

    // EXPLORATION
    public static final String HFrag = "<:HFrag:1156670541382553733>";
    public static final String CFrag = "<:CFrag:1156670486823055400>";
    public static final String IFrag = "<:IFrag:1156670542733115512>";
    public static final String UFrag = "<:UFrag:1156670699314880584>";
    public static final String Relic = "<:Relic:1054075788711964784>";
    public static final String Cultural = "<:Cultural:1159118849698963466>";
    public static final String Industrial = "<:Industrial:1159118817029533706>";
    public static final String Hazardous = "<:Hazardous:1159118854987976734>";
    public static final String Frontier = "<:Frontier:1156670537699971082>";

    public static String getFragEmoji(String frag) {
        frag = frag.toLowerCase();
        return switch (frag) {
            case "crf" -> CFrag;
            case "irf" -> IFrag;
            case "hrf" -> HFrag;
            default -> UFrag;
        };
    }

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
    public static final String ActionCard = "<:ActionCard:1156670454354939924>";
    public static final String ActionCardAlt = "<:ActionCardAlt:1064838264520986655>";
    public static final String Agenda = "<:Agenda:1054660476874792990>";
    public static final String AgendaAlt = "<:AgendaAlt:1064838239690698812>";
    public static final String AgendaWhite = "<:Agendawhite:1060073913423495258>";
    public static final String AgendaBlack = "<:Agendablack:1060073912442036336>";
    public static final String PN = "<:PN:1159118823446806538>";
    public static final String PNALt = "<:PNALt:1064838292467613766>";
    public static final String RelicCard = "<:RelicCard:1147194759903989912>";
    public static final String CulturalCard = "<:CulturalCard:1147194826647928932>";
    public static final String HazardousCard = "<:HazardousCard:1147194829479100557>";
    public static final String IndustrialCard = "<:IndustrialCard:1147194830762545183>";
    public static final String FrontierCard = "<:FrontierCard:1147194828417929397>";
    public static final String EventCard = "";

    // OBJECTIVES 
    public static final String Custodians = "<:Custodians:1244158363449692231>";
    public static final String CustodiansVP = "<:CustodiansVP:1244158364381085697>";
    public static final String SecretObjective = "<:Secretobjective:1159118787572940891>";
    public static final String Public1 = "<:Public1:1159118826026303528>";
    public static final String Public2 = "<:Public2:1159118827544662106>";
    public static final String Public1alt = "<:Public1Alt:1058978029243728022>";
    public static final String Public2alt = "<:Public2Alt:1058977929725493398>";
    public static final String SecretObjectiveAlt = "<:SecretobjectiveAlt:1058977803728584734>";

    // COMPONENTS
    public static final String tg = "<:tg:1156670696332726353>";
    public static final String nomadcoin = "<:nomadcoin:1107100093791879178>";
    public static final String comm = "<:comm:1156670488232345620>";
    public static final String Sleeper = "<:Sleeper:1047871121451663371>";
    public static final String SleeperB = "<:SleeperB:1047871220831506484>";

    // UNITS
    public static final String warsun = "<:warsun:1156670701042942103>";
    public static final String spacedock = "<:spacedock:1156670661062836364>";
    public static final String pds = "<:pds:1156670635393695815>";
    public static final String mech = "<:mech:1156670600329314395>";
    public static final String infantry = "<:infantry:1156670544658309121>";
    public static final String flagship = "<:flagship:1156670518754300044>";
    public static final String fighter = "<:fighter:1156670517542146189>";
    public static final String dreadnought = "<:dreadnought:1156670515214291055>";
    public static final String destroyer = "<:destroyer:1156670514077634601>";
    public static final String carrier = "<:carrier:1156670484788805633>";
    public static final String cruiser = "<:cruiser:1156670491159973888>";
    public static final String TyrantsLament = "<:TyrantsLament:1303447974701170738>";
    public static final String PlenaryOrbital = "<:PlenaryOrbital:1303447973761388606>";
    public static final String Monument = "<:Monument:1303448130749988874>";

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
    // public static final String KolleccAgent = "";
    public static final String KolleccCommander = "<:KolleccCommander:1287842294371975261>";
    public static final String KolleccHero = "<:KolleccHero:1287842295797776480>";
    public static final String KolumeAgent = "<:KolumeAgent:1287842177098977381>";
    public static final String KolumeCommander = "<:KolumeCommander:1287842178386759702>";
    public static final String KolumeHero = "<:KolumeHero:1287842180341305464>";
    public static final String KortaliAgent = "<:KortaliAgent:1287842241821544550>";
    public static final String KortaliCommander = "<:KortaliCommander:1287842243201204406>";
    public static final String KortaliHero = "<:KortaliHero:1287842244531060828>";
    public static final String LanefirAgent = "<:LanefirAgent:1287842323190775920>";
    public static final String LanefirCommander = "<:LanefirCommander:1287842324218384405>";
    // public static final String LanefirHero = "";

    public static final String Agent = "<:Agent:1235272542030270614>";
    public static final String Commander = "<:Commander:1235272679838453801>";
    public static final String Hero = "<:Hero:1235272815511601353>";
    public static final String Envoy = "<:Envoy:1235272315357495339>";
    // END OF EMOJI FARM 6

    // RESOURCE AND INFLUENCE SYMBOLS
    public static final String influence = "<:influence:959575421337358336>";
    public static final String resources = "<:resources:959575421274451998>";
    public static final String ResInf = "<:resinf:1236835261833543762>";
    public static final String Resources_0 = "<:R0:1244032628462457023>";
    public static final String Resources_1 = "<:R1:1244032629548781568>";
    public static final String Resources_2 = "<:R2:1244032630513729587>";
    public static final String Resources_3 = "<:R3:1244032631646191681>";
    public static final String Resources_4 = "<:R4:1244032689338716230>";
    public static final String Resources_5 = "<:R5:1244032690580099134>";
    public static final String Resources_6 = "<:R6:1244032691565756527>";
    public static final String Resources_7 = "<:R7:1244032692626915389>";
    public static final String Resources_8 = "<:R8:1244032693688336435>";
    public static final String Resources_9 = "<:R9:1244032695080714360>";
    public static final String Influence_0 = "<:I0:1244032564620951592>";
    public static final String Influence_1 = "<:I1:1244032565304885259>";
    public static final String Influence_2 = "<:I2:1244032566919692359>";
    public static final String Influence_3 = "<:I3:1244032567972200570>";
    public static final String Influence_4 = "<:I4:1244032569482416189>";
    public static final String Influence_5 = "<:I5:1244032571055018025>";
    public static final String Influence_6 = "<:I6:1244032572108050554>";
    public static final String Influence_7 = "<:I7:1244032572900769833>";
    public static final String Influence_8 = "<:I8:1244032625946001498>";
    public static final String Influence_9 = "<:I9:1244032627225268254>";

    // PLANETS
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
    public static final String Mirage = "<:Mirage:1262599635676041357>";
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
    public static final String Retillion = "<:Retillon:1159513666232787045>";
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

    // EMOJI FARM 11 - DS PLANETS
    public static final String Derbrae = "<:Derbrae:1171623488746958890>";
    public static final String Detic = "<:Detic:1171623490516963360>";
    public static final String Domna = "<:Domna:1171623492106596424>";
    public static final String Dorvok = "<:Dorvok:1171623493381669004>";
    public static final String Echo = "<:Echo:1171623494577041438>";
    public static final String EtirV = "<:EtirV:1171623515506622545>";
    public static final String Fakrenn = "<:Fakrenn:1171623517507289149>";
    public static final String Gwiyun = "<:Gwiyun:1171623518442627083>";
    public static final String Inan = "<:Inan:1171623519289888828>";
    public static final String Larred = "<:Larred:1171623520422346782>";
    public static final String Lliot = "<:Lliot:1171623538067779585>";
    public static final String Lodran = "<:Lodran:1171623539380584458>";
    public static final String Mandle = "<:Mandle:1171623540370460813>";
    public static final String Moln = "<:Moln:1171623541330952333>";
    public static final String Nairb = "<:Nairb:1171623542194974901>";
    public static final String Prism = "<:Prism:1171623558225608856>";
    public static final String Qaak = "<:Qaak:1171623559647465532>";
    public static final String Regnem = "<:Regnem:1171623560477937826>";
    public static final String Rysaa = "<:Rysaa:1171623561086128179>";
    public static final String Salin = "<:Salin:1171623562633814037>";
    public static final String Sanvit = "<:Sanvit:1171623578614120489>";
    public static final String Sierpen = "<:Sierpen:1171623579541049419>";
    public static final String Silence = "<:Silence:1171623580946141226>";
    public static final String Swog = "<:Swog:1171623581667557407>";
    public static final String Tarrock = "<:Tarrock:1171623583479509104>";
    public static final String Troac = "<:Troac:1171623597387812876>";
    public static final String Vioss = "<:Vioss:1171623598650302566>";
    // END OF EMOJI FARM 11

    // EMOJI FARM 15 THRU 17 - ERONOUS' PLANETS
    private static final String Adoriah = "<:Adoriah:1220419395315040276>";
    private static final String Adrian = "<:Adrian:1220419409047326761>";
    private static final String Akhassi = "<:Akhassi:1220419427749728306>";
    private static final String Ako = "<:Ako:1220419580434841600>";
    private static final String Aranndan = "<:Aranndan:1220419732599996596>";
    private static final String Argenum = "<:Argenum:1220420429085409390>";
    private static final String Behjan = "<:Behjan:1220420559704297612>";
    private static final String Breakpoint = "<:Breakpoint:1220420578331070554>";
    private static final String Brilenci = "<:Brilenci:1220420702369353799>";
    private static final String Cahgaris = "<:Cahgaris:1220420818824073396>";
    private static final String Cantris = "<:Cantris:1220420951632515112>";
    private static final String Casibann = "<:Casibann:1220421042984452126>";
    private static final String Cerberus = "<:Cerberus:1220421139021565974>";
    private static final String Char = "<:Char:1220421248316608572>";
    private static final String DeathsGate = "<:DeathsGate:1220421341484945438>";
    private static final String Dognui = "<:Dognui:1220421438272704632>";
    private static final String Dwuuit = "<:Dwuuit:1220421525690126498>";
    private static final String ElansRest = "<:ElansRest:1220421597828087940>";
    private static final String ElokNu = "<:ElokNu:1220421773967753378>";
    private static final String ElokPhi = "<:ElokPhi:1220421790808150026>";
    private static final String Erissiha = "<:Erissiha:1220421865336475799>";
    private static final String Erodius = "<:Erodius:1220421953886752789>";
    private static final String Eshonia = "<:Eshonia:1220422032005529620>";
    private static final String Ferrust = "<:Ferrust:1220422117976440942>";
    private static final String Fyrain = "<:Fyrain:1220422219641917471>";
    private static final String Ghanis = "<:Ghanis:1220422468292972544>";
    private static final String Grishinu = "<:Grishinu:1220422533740892251>";
    private static final String Gryenorn = "<:Gryenorn:1220422682223317102>";
    private static final String Grywon = "<:Grywon:1220422748476801095>";
    private static final String HellsMaw = "<:HellsMaw:1220422788960092160>";
    private static final String Hersey = "<:Hersey:1220422883306635367>";
    private static final String Heska = "<:Heska:1220422900541165619>";
    private static final String Hevahold = "<:Hevahold:1220422911370723388>";
    private static final String HranCus = "<:HranCus:1220422924452757614>";
    private static final String Hurigati = "<:Hurigati:1220434041250119721>";
    private static final String IkrusIII = "<:IkrusIII:1221853611261427752>";
    private static final String IlVoshu = "<:IlVoshu:1221852964176527433>";
    private static final String KanHis = "<:KanHis:1221852966189793290>";
    private static final String Kelgate = "<:Kelgate:1221852967892815922>";
    private static final String Khjan = "<:Khjan:1221852969427796008>";
    private static final String KkitaUlIn = "<:KkitaUlIn:1221853735681261639>";
    private static final String Kris = "<:Kris:1221853827456696472>";
    private static final String Kytos = "<:Kytos:1221853894804770897>";
    private static final String Leonelli = "<:Leonelli:1221853912223453276>";
    private static final String Limbo = "<:Limbo:1221853924013772810>";
    private static final String Lunerus = "<:Lunerus:1221853937754177697>";
    private static final String Lust = "<:Lust:1221853949942960168>";
    private static final String Lynntani = "<:Lynntani:1221853961615704225>";
    private static final String Malbolge = "<:Malbolge:1221853971497615511>";
    private static final String MaonLor = "<:MaonLor:1221854015915167754>";
    private static final String Mayris = "<:Mayris:1221854325962444800>";
    private static final String Mecantor = "<:Mecantor:1221854326553710744>";
    private static final String MekoII = "<:MekoII:1221854328273375242>";
    private static final String Meranna = "<:Meranna:1221854329527599185>";
    private static final String Merjae = "<:Merjae:1221854331003863120>";
    private static final String Migyro = "<:Migyro:1221854332048244857>";
    private static final String MorRock = "<:MorRock:1221854334283939942>";
    private static final String Mornn = "<:Mornn:1221854332740173978>";
    private static final String Myrwater = "<:Myrwater:1221854361647317092>";
    private static final String Nix = "<:Nix:1221854404802646126>";
    private static final String Nokrurn = "<:Nokrurn:1221854422804594828>";
    private static final String Norrk = "<:Norrk:1221854424054366305>";
    private static final String Orchard = "<:Orchard:1221854425157603441>";
    private static final String Perpetual = "<:Perpetual:1221854426399248485>";
    private static final String Phylo = "<:Phylo:1221854427938553886>";
    private static final String Plutus = "<:Plutus:1221854449526378567>";
    private static final String Prymis = "<:Prymis:1221854451036455013>";
    private static final String Quwon = "<:Quwon:1221854452126978228>";
    private static final String RayonV = "<:RayonV:1221854454241034301>";
    private static final String Renhult = "<:Renhult:1221854455549530132>";
    private static final String Rhyah = "<:Rhyah:1221854473115402300>";
    private static final String RialArchon = "<:RialArchon:1221854474646192139>";
    private static final String RylFang = "<:RylFang:1221854475661086740>";
    private static final String Sehnn = "<:Sehnn:1221854476810453154>";
    private static final String Selen = "<:Selen:1221854478005833859>";
    private static final String Sentuim = "<:Sentuim:1221854502651695184>";
    private static final String Shigonas = "<:Shigonas:1221854503691751445>";
    private static final String Shul = "<:Shul:1221854505143111760>";
    private static final String Sigilus = "<:Sigilus:1221854506187231292>";
    private static final String Sokaris = "<:Sokaris:1221854507689050132>";
    private static final String Solin = "<:Solin:1221854534666551438>";
    private static final String Station309 = "<:Station309:1221854535623114802>";
    private static final String Stygain = "<:Stygain:1221854536738803873>";
    private static final String SuPrima = "<:SuPrima:1221854538214932630>";
    private static final String Syvian = "<:Syvian:1221854539389599754>";
    private static final String TaalDorn = "<:TaalDorn:1221854567788969985>";
    private static final String Telahas = "<:Telahas:1221854568896401518>";
    private static final String TethnSekus = "<:TethnSekus:1221854570821451916>";
    private static final String TethnTirs = "<:TethnTirs:1221854572520411267>";
    private static final String Thenphase = "<:Thenphase:1221854573887619183>";
    private static final String Tir = "<:Tir:1221854600395751585>";
    private static final String Uhott = "<:Uhott:1221854601750511636>";
    private static final String UlonGamma = "<:UlonGamma:1221854603302277224>";
    private static final String UlonRho = "<:UlonRho:1221854605382516737>";
    private static final String Ultimur = "<:Ultimur:1221854606712115211>";
    private static final String Venhalo = "<:Venhalo:1221854627511930920>";
    private static final String Vent = "<:Vent:1221854628870623323>";
    private static final String Verdis = "<:Verdis:1221854630687019038>";
    private static final String Vernium = "<:Vernium:1221854631873871983>";
    private static final String Veyhrune = "<:Veyhrune:1221854633111195699>";
    private static final String Viliguard = "<:Viliguard:1221854727495618600>";
    private static final String Violence = "<:Violence:1221854728695054466>";
    private static final String Volgan = "<:Volgan:1221854729722921000>";
    private static final String Volra = "<:Volra:1221854730859315303>";
    private static final String VygarII = "<:VygarII:1221854853639442543>";
    private static final String Vylanua = "<:Vylanua:1221854869380665365>";
    private static final String Xyon = "<:Xyon:1221854870685089823>";
    private static final String Yncranti = "<:Yncranti:1221854871951638549>";
    private static final String Ynnis = "<:Ynnis:1221854873700663426>";
    private static final String Zhgen = "<:Zhgen:1221854874866679818>";
    // END OF EMOJI FARM 15 THRU 17

    // LIST OF SEM-LORES
    public static final List<String> SemLores = Arrays.asList(SemLor, SemLord, SemiLor, SemLore);

    // EMOJI FARM 9 - SC COLORS
    public static String SC1Mention() {
        return TI4Emoji.sc_1_1.toString() + TI4Emoji.sc_1_2 + TI4Emoji.sc_1_3 + TI4Emoji.sc_1_4 + TI4Emoji.sc_1_5 + TI4Emoji.sc_1_6;
    }

    public static String SC2Mention() {
        return TI4Emoji.sc_2_1.toString() + TI4Emoji.sc_2_2 + TI4Emoji.sc_2_3 + TI4Emoji.sc_2_4 + TI4Emoji.sc_2_5 + TI4Emoji.sc_2_6;
    }

    public static String SC3Mention() {
        return TI4Emoji.sc_3_1.toString() + TI4Emoji.sc_3_2 + TI4Emoji.sc_3_3 + TI4Emoji.sc_3_4 + TI4Emoji.sc_3_5;
    }

    public static String SC4Mention() {
        return TI4Emoji.sc_4_1.toString() + TI4Emoji.sc_4_2 + TI4Emoji.sc_4_3 + TI4Emoji.sc_4_4 + TI4Emoji.sc_4_5 + TI4Emoji.sc_4_6 + TI4Emoji.sc_4_7;
    }

    public static String SC5Mention() {
        return TI4Emoji.sc_5_1.toString() + TI4Emoji.sc_5_2 + TI4Emoji.sc_5_3 + TI4Emoji.sc_5_4;
    }

    public static String SC6Mention() {
        return TI4Emoji.sc_6_1.toString() + TI4Emoji.sc_6_2 + TI4Emoji.sc_6_3 + TI4Emoji.sc_6_4 + TI4Emoji.sc_6_5;
    }

    public static String SC7Mention() {
        return TI4Emoji.sc_7_1.toString() + TI4Emoji.sc_7_2 + TI4Emoji.sc_7_3 + TI4Emoji.sc_7_4 + TI4Emoji.sc_7_5 + TI4Emoji.sc_7_6;
    }

    public static String SC8Mention() {
        return TI4Emoji.sc_8_1.toString() + TI4Emoji.sc_8_2 + TI4Emoji.sc_8_3 + TI4Emoji.sc_8_4 + TI4Emoji.sc_8_5;
    }
    // END EMOJI FARM 9

    // EMOJI FARM 10 - COLOR UNITS
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
    public static final String splitturquoise = "<:splittorquoise:1165037013486022726>";
    public static final String splityellow = "<:splityellow:1165037014995963965>";
    public static final String riftset = "<:riftset:1281263062715990057>";
    // END EMOJI FARM 10

    // ANOMOLIES
    public static final String Supernova = "<:supernova:1137029705946640445>";
    public static final String Asteroid = "<:asteroidbelt:1137029604050210846>";
    public static final String GravityRift = "<:grift:1136836649003782254>";
    public static final String Nebula = "<:nebula:1137029690004090900>";

    // TECHNOLOGY
    public static final String PropulsionTech = "<:propulsion:1120031660025577533>";
    public static final String PropulsionDisabled = "<:propulsionDisabled:1120031664458960896>";
    public static final String Propulsion2 = "<:propulsion2:1120031821304959098>";
    public static final String Propulsion3 = "<:propulsion3:1120031823569895584>";
    public static final String BioticTech = "<:biotic:1120031648151523369>";
    public static final String BioticDisabled = "<:bioticDisabled:1120031652299681963>";
    public static final String Biotic2 = "<:biotic2:1120031649732771841>";
    public static final String Biotic3 = "<:biotic3:1120031651167227944>";
    public static final String CyberneticTech = "<:cybernetic:1120031653432139896>";
    public static final String CyberneticDisabled = "<:cyberneticDisabled:1120031658582737047>";
    public static final String Cybernetic2 = "<:cybernetic2:1120031655374102718>";
    public static final String Cybernetic3 = "<:cybernetic3:1120031656766619658>";
    public static final String WarfareTech = "<:warfare:1120031666241536082>";
    public static final String WarfareDisabled = "<:warfareDisabled:1120031828384956446>";
    public static final String Warfare2 = "<:warfare2:1120031824891093113>";
    public static final String Warfare3 = "<:warfare3:1120031825851584523>";
    public static final String UnitUpgradeTech = "<:UnitUpgradeTech:1159118790966116362>";
    public static final String UnitTechSkip = "<:UnitTechSkip:1151926553488412702>";
    public static final String NonUnitTechSkip = "<:NonUnitTechSkip:1151926572278874162>";

    // GOOD DOGS
    public static final String GoodDog = "<:GoodDog:1068567308819255316>";
    public static final String Ozzie = "<:Ozzie:1069446695689134140>";
    public static final String Summer = "<:Summer:1070283656037412884>";
    public static final String Charlie = "<:Charlie:1096774713352650812>";
    public static final String Scout = "<:scout_face_2:1071965098639360081>";
    public static final String scoutSpinner = "<a:spinner:1090392441477136434>";

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

    // FRANKEN
    public static final String Franken1 = "<:Franken1:1180167512965533726>";
    public static final String Franken2 = "<:Franken2:1180167515276582992>";
    public static final String Franken3 = "<:Franken3:1180167516392276048>";
    public static final String Franken4 = "<:Franken4:1180167517608615936>";
    public static final String Franken5 = "<:Franken5:1180167519114383460>";
    public static final String Franken6 = "<:Franken6:1180167520079065239>";
    public static final String Franken7 = "<:Franken7:1180167520821452931>";
    public static final String Franken8 = "<:Franken8:1180167522176213062>";
    public static final String Franken9 = "<:Franken9:1222165989278617620>";
    public static final String Franken10 = "<:Franken10:1222165990297702471>";
    public static final String Franken11 = "<:Franken11:1222165991459393658>";
    public static final String Franken12 = "<:Franken12:1222165992571146341>";
    public static final String Franken13 = "<:Franken13:1222165993753804870>";
    public static final String Franken14 = "<:Franken14:1222165994806448261>";
    public static final String Franken15 = "<:Franken15:1222165995733385287>";
    public static final String Franken16 = "<:Franken16:1222165988238430238>";

    // EMOJI FARM 18 - MISC FACTION EMBLEMS
    public static final String franken_aurilian_vanguard = "<:franken_aurilian_vanguard:1243245354439282761>";
    public static final String franken_aelorian_clans = "<:franken_aelorian_clans:1243245353411809341>";
    public static final String franken_dakari_hegemony = "<:franken_dakari_hegemony:1243245355395715202>";
    public static final String franken_durethian_shard = "<:franken_durethian_shard:1243245356364468304>";
    public static final String franken_elyndor_consortium = "<:franken_elyndor_consortium:1243245357480284190>";
    public static final String franken_fal_kesh_covenant = "<:franken_fal_kesh_covenant:1243245358600028210>";
    public static final String franken_ghaldir_union = "<:franken_ghaldir_union:1243245359812182126>";
    public static final String franken_helian_imperium = "<:franken_helian_imperium:1243245360835727461>";
    public static final String franken_jhoran_brotherhood = "<:franken_jhoran_brotherhood:1243245493736308958>";
    public static final String franken_kyrenic_republic = "<:franken_kyrenic_republic:1243245364228788244>";
    public static final String franken_lysarian_order = "<:franken_lysarian_order:1243245527261253634>";
    public static final String franken_mydran_assembly = "<:franken_mydran_assembly:1243245368099995840>";
    public static final String franken_nyridian_coalition = "<:franken_nyridian_coalition:1243245546454646974>";
    public static final String franken_olthax_collective = "<:franken_olthax_collective:1243245371405111378>";
    public static final String franken_qalorian_federation = "<:franken_qalorian_federation:1243245374815076372>";
    public static final String franken_prayers_of_trudval = "<:franken_prayers_of_trudval:1243245571741843598>";
    public static final String franken_rak_thul_tribes = "<:franken_rak_thul_tribes:1243245697613037588>";
    public static final String franken_sol_tari_dynasty = "<:franken_sol_tari_dynasty:1243245378451668992>";
    public static final String franken_syrketh_conclave = "<:franken_syrketh_conclave:1243245384781004961>";
    public static final String franken_thalassian_guild = "<:franken_thalassian_guild:1243245385934307409>";
    public static final String franken_thraxian_imperium = "<:franken_thraxian_imperium:1243245718936883230>";
    public static final String franken_thymarian_league = "<:franken_thymarian_league:1243245388648157204>";
    public static final String franken_valxian_pact = "<:franken_valxian_pact:1243245733142859816>";
    public static final String franken_var_sul_syndicate = "<:franken_var_sul_syndicate:1243245392158527528>";
    public static final String franken_veridian_empire = "<:franken_veridian_empire:1243245757285269514>";
    public static final String franken_zel_tharr_dominion = "<:franken_zel_tharr_dominion:1243245395685933197>";
    public static final String franken_zircon_ascendancy = "<:franken_zircon_ascendancy:1243245769826500628>";
    public static final String franken_zor_thul_matriarchate = "<:franken_zor_thul_matriarchate:1243245399129722940>";

    // PBD2000 FACTIONS
    public static final String echoes = "<:echoes:1189668215413026968>";
    public static final String enclave = "<:enclave:1189668216478367744>";
    public static final String raven = "<:raven:1189668203580887121>";
    public static final String syndicate = "<:syndicate:1189668205355073667>";
    public static final String terminator = "<:terminator:1189668214125363231>";

    // MEMEPHILOSPHER
    public static final String netharii = "<:netharii:1303204852532383804>";

    // DICE
    public static final String d10green_0 = "<:d10green_0:1180170565819039916>";
    public static final String d10green_1 = "<:d10green_1:1180170567337386026>";
    public static final String d10green_2 = "<:d10green_2:1180170568381763605>";
    public static final String d10green_3 = "<:d10green_3:1180170569526825092>";
    public static final String d10green_4 = "<:d10green_4:1180170570533441576>";
    public static final String d10green_5 = "<:d10green_5:1180170571896606770>";
    public static final String d10green_6 = "<:d10green_6:1189667939222290434>";
    public static final String d10green_7 = "<:d10green_7:1189667941407543378>";
    public static final String d10green_8 = "<:d10green_8:1189667942326079618>";
    public static final String d10green_9 = "<:d10green_9:1189667944125435924>";
    public static final String d10red_0 = "<:d10red_0:1189667965600280676>";
    public static final String d10red_1 = "<:d10red_1:1189667967349301358>";
    public static final String d10red_2 = "<:d10red_2:1189667968427249754>";
    public static final String d10red_3 = "<:d10red_3:1189667969597452369>";
    public static final String d10red_4 = "<:d10red_4:1189667971270979796>";
    public static final String d10red_5 = "<:d10red_5:1189667992557076661>";
    public static final String d10red_6 = "<:d10red_6:1189667994025066686>";
    public static final String d10red_7 = "<:d10red_7:1189667994977181796>";
    public static final String d10red_8 = "<:d10red_8:1189667995883143279>";
    public static final String d10red_9 = "<:d10red_9:1189667996852039800>";
    public static final String d10blue_0 = "<:d10blue_0:1290145967592574976>";
    public static final String d10blue_1 = "<:d10blue_1:1290145968414785559>";
    public static final String d10blue_2 = "<:d10blue_2:1290145969542926336>";
    public static final String d10blue_3 = "<:d10blue_3:1290145970486775869>";
    public static final String d10blue_4 = "<:d10blue_4:1290145994142515300>";
    public static final String d10blue_5 = "<:d10blue_5:1290145994989768867>";
    public static final String d10blue_6 = "<:d10blue_6:1290145996017373194>";
    public static final String d10blue_7 = "<:d10blue_7:1290145996969476147>";
    public static final String d10blue_8 = "<:d10blue_8:1290146014832885864>";
    public static final String d10blue_9 = "<:d10blue_9:1290146015877529693>";
    public static final String d10grey_0 = "<:d10grey_0:1290146062396297346>";
    public static final String d10grey_1 = "<:d10grey_1:1290146035594694658>";
    public static final String d10grey_2 = "<:d10grey_2:1290146036827820184>";
    public static final String d10grey_3 = "<:d10grey_3:1290146037520138252>";
    public static final String d10grey_4 = "<:d10grey_4:1290146038614724628>";
    public static final String d10grey_5 = "<:d10grey_5:1290146080385798225>";
    public static final String d10grey_6 = "<:d10grey_6:1290146081379713108>";
    public static final String d10grey_7 = "<:d10grey_7:1290146082843656212>";
    public static final String d10grey_8 = "<:d10grey_8:1290146097129324668>";
    public static final String d10grey_9 = "<:d10grey_9:1290146098576490496>";

    // MILTY DRAFT
    public static final String sliceUnpicked = "<:sliceUnpicked:1225188657703682250>";
    public static final String sliceA = "<:sliceA:1223132315476037773>";
    public static final String sliceB = "<:sliceB:1223132318311387146>";
    public static final String sliceC = "<:sliceC:1223132319947423787>";
    public static final String sliceD = "<:sliceD:1223132322245640314>";
    public static final String sliceE = "<:sliceE:1223132324175151174>";
    public static final String sliceF = "<:sliceF:1223132325932699689>";
    public static final String sliceG = "<:sliceG:1223132327744634982>";
    public static final String sliceH = "<:sliceH:1223132330000912434>";
    public static final String sliceI = "<:sliceI:1223132332547117086>";
    public static final String sliceJ = "<:sliceJ:1227099602260463757>";
    public static final String sliceK = "<:sliceK:1227099604244500604>";
    public static final String sliceL = "<:sliceL:1227099605968097312>";
    public static final String sliceM = "<:sliceM:1227099608774217788>";
    public static final String sliceN = "<:sliceN:1227099610279837768>";
    public static final String sliceO = "<:sliceO:1227099612645687368>";
    public static final String sliceP = "<:sliceP:1227099614885314582>";
    public static final String sliceQ = "<:sliceQ:1227099616823218306>";
    public static final String sliceR = "<:sliceR:1227099618718908489>";
    public static final String sliceS = "<:sliceS:1227099621453463573>";
    public static final String sliceT = "<:sliceT:1227099623915524126>";
    public static final String sliceU = "<:sliceU:1227099625610023004>";
    public static final String sliceV = "<:sliceV:1227099631691763742>";
    public static final String sliceW = "<:sliceW:1227099633789042709>";
    public static final String sliceX = "<:sliceX:1227099636628721685>";
    public static final String sliceY = "<:sliceY:1227099638616559686>";
    public static final String sliceZ = "<:sliceZ:1227099640667701278>";

    // SPEAKER TOKENS
    public static final String positionUnpicked = "<:positionUnpicked:1227093640313180160>";
    public static final String speakerPick1 = "<:position1:1222754925105381416>";
    public static final String speakerPick2 = "<:position2:1222754926174666843>";
    public static final String speakerPick3 = "<:position3:1222754927294550076>";
    public static final String speakerPick4 = "<:position4:1222754928368422993>";
    public static final String speakerPick5 = "<:position5:1222754929219993601>";
    public static final String speakerPick6 = "<:position6:1222754930092146780>";
    public static final String speakerPick7 = "<:position7:1222754930922754099>";
    public static final String speakerPick8 = "<:position8:1222754932503875604>";
    public static final String speakerPick9 = "<:position9:1227093802963964025>";
    public static final String speakerPick10 = "<:position10:1227093804595544106>";
    public static final String speakerPick11 = "<:position11:1227093805963022398>";
    public static final String speakerPick12 = "<:position12:1227093807372308550>";

    // TILES
    public static final String Anomaly = "<:Anomaly:1303437791740432384>";
    public static final String EmptySystem = "<:EmptySystem:1303437779417698366>";

    // OTHER
    public static final String WHalpha = "<:WHalpha:1159118794334146570>";
    public static final String WHbeta = "<:WHbeta:1159118795508547625>";
    public static final String WHgamma = "<:WHgamma:1159118797765103686>";
    public static final String CreussAlpha = "<:CreussAlpha:1163507874065031313>";
    public static final String CreussBeta = "<:CreussBeta:1163507875818242209>";
    public static final String CreussGamma = "<:CreussGamma:1163507872404090960>";
    public static final String LegendaryPlanet = "<:Legendaryplanet:1159118819554500738>";
    public static final String SpeakerToken = "<:Speakertoken:965363466821050389>";
    public static final String Sabotage = "<:sabotage:962784058159546388>";
    public static final String NoSabotage = "<:nosabo:962783456541171712>";
    public static final String nowhens = "<:nowhens:962921609671364658>";
    public static final String noafters = "<:noafters:962923748938362931>";
    public static final String Wash = "<a:Wash:1065334637532041298>";
    public static final String winemaking = "<:winemaking:1064244730000584754>";
    public static final String BortWindow = "<:bortwindow:1032312829585399880>";
    public static final String SpoonAbides = "<:TheSpoonAbides:1003482035953860699>";
    public static final String AsyncTI4Logo = "<:asyncti4:959703535258333264>";
    public static final String TIGL = "<:TIGL:1111086048974475305>";
    public static final String RollDice = "<a:rolldice:1131416916330811392>";
    public static final String BLT = "<:BLT:1080954650339065866>";
    public static final String PinkHeart = "<:PinkHeart:1197584926359425145>";
    public static final String CheckMark = "<:PinkHeart:1197584926359425145>";

    // SOURCE ICONS
    public static final String TI4BaseGame = "<:TI4BaseGame:1181341816688222378>";
    public static final String TI4PoK = "<:TI4PoK:1181341818676334683>";
    public static final String Absol = "<:Absol:1180154956372783177>"; // Symbol for Absol's stuff https://discord.com/channels/743629929484386395/1023681580989939712
    public static final String Flagshipping = "<:Flagshipping:1261527033834373203>";
    public static final String PromisesPromises = "<:PromisesPromises:1261526966499283054>";
    public static final String DiscordantStars = "<:DS:1180154970381754409>"; // Symbol for Discordant Stars https://discord.com/channels/743629929484386395/990061481238364160
    public static final String UnchartedSpace = "<:UnchartedSpace:1250241051755544657>";
    public static final String ActionDeck2 = "<:ActionDeck2:1180154984743063572>"; // Symbol for Will's Action Deck 2 mod https://discord.com/channels/743629929484386395/1111799687184396338
    public static final String Eronous = "<:eronous:1180154997359509504>"; // Symbol for Eronous' stuff https://discord.com/channels/743629929484386395/1096820095470272582
    public static final String IgnisAurora = "<:IgnisAurora:1180155010206683218>"; // Symbol for Ignis Aurora's stuff
    public static final String KeleresPlus = "<:KeleresPlus:1180158340295299192>"; // Symbol for Keleres Plus https://discord.com/channels/743629929484386395/1027385712821149706
    public static final String ProjectPi = "<:ProjectPie:1128504084811481219>";
    public static final String MiltyMod = "<:MiltyMod:1181981333694722178>"; // Symbol for Milty's mod https://discord.com/channels/743629929484386395/1087435266249207869
    public static final String StrategicAlliance = "<:StrategicAlliance:1225473614946500680>"; // Symbol for Holytispoon's Strategic Alliance
    public static final String Monuments = "<:Monuments:1303420074434236537>"; // Monuments+ https://discord.com/channels/743629929484386395/1205395696950321172

    // LIST OF SYMBOLS FOR FOG STUFF
    public static final List<String> symbols = new ArrayList<>(Arrays.asList(
        warsun, spacedock, pds, mech, infantry, flagship, fighter, dreadnought, destroyer, carrier, cruiser, HFrag,
        CFrag, IFrag, UFrag, Relic, Cultural, Industrial, Hazardous, Frontier, SecretObjective, Public1, Public2,
        tg, comm, Sleeper, influence, resources, SemLord, ActionCard, Agenda, PN, CyberneticTech,
        PropulsionTech, BioticTech, WarfareTech, WHalpha, WHbeta, WHgamma, LegendaryPlanet, SpeakerToken,
        BortWindow));

    // private static List<String> testingEmoji =
    // Arrays.asList("🐷","🙉","💩","👺","🥵","🤯","😜","👀","🦕","🐦","🦏","🐸");

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
        List<String> semLores = new ArrayList<>(SemLores);
        Random seed = ThreadLocalRandom.current();
        Collections.shuffle(semLores, seed);
        return semLores.getFirst();
    }

    public static String getRandomGoodDog() {
        List<String> goodDogs = new ArrayList<>(GoodDogs);
        Random seed = ThreadLocalRandom.current();
        Collections.shuffle(goodDogs, seed);
        return goodDogs.getFirst();
    }

    public static String getRandomGoodDog(String randomSeed) {
        List<String> goodDogs = new ArrayList<>(GoodDogs);
        Random seed = new Random(randomSeed.hashCode());
        Collections.shuffle(goodDogs, seed);
        return goodDogs.getFirst();
    }

    @NotNull
    public static String getFactionIconFromDiscord(String faction) {
        if (faction == null) {
            return getRandomizedEmoji(0, null);
        }
        return switch (faction.toLowerCase()) {
            case "arborec" -> Arborec;
            case "argent" -> Argent;
            case "cabal" -> Cabal;
            case "empyrean" -> Empyrean;
            case "ghost" -> Ghost;
            case "hacan" -> Hacan;
            case "jolnar" -> Jolnar;
            case "l1z1x" -> L1Z1X;
            case "barony", "letnev" -> Letnev;
            case "yssaril" -> Yssaril;
            case "mahact" -> Mahact;
            case "mentak" -> Mentak;
            case "muaat" -> Muaat;
            case "naalu" -> Naalu;
            case "naaz" -> Naaz;
            case "nekro" -> Nekro;
            case "nomad" -> Nomad;
            case "saar" -> Saar;
            case "sardakk" -> Sardakk;
            case "sol" -> Sol;
            case "titans" -> Titans;
            case "winnu" -> Winnu;
            case "xxcha" -> Xxcha;
            case "yin" -> Yin;

            case "lazax" -> Lazax;
            case "neutral" -> Neutral;

            case "keleres", "keleresx", "keleresm", "keleresa" -> Keleres;

            case "augers" -> augers;
            case "axis" -> axis;
            case "bentor" -> bentor;
            case "blex", "kyro" -> blex;
            case "celdauri" -> celdauri;
            case "cheiran" -> cheiran;
            case "cymiae" -> cymiae;
            case "dihmohn" -> dihmohn;
            case "edyn" -> edyn;
            case "florzen" -> florzen;
            case "freesystems" -> freesystems;
            case "ghemina" -> ghemina;
            case "ghoti" -> ghoti;
            case "gledge" -> gledge;
            case "khrask" -> khrask;
            case "kjalengard" -> kjalengard;
            case "kollecc" -> kollecc;
            case "kolume" -> kolume;
            case "kortali" -> kortali;
            case "lanefir" -> lanefir;
            case "lizho" -> lizho;
            case "mirveda" -> mirveda;
            case "mortheus" -> mortheus;
            case "mykomentori" -> mykomentori;
            case "nivyn" -> nivyn;
            case "nokar" -> nokar;
            case "olradin" -> olradin;
            case "rohdhna" -> rohdhna;
            case "tnelis" -> tnelis;
            case "vaden" -> vaden;
            case "vaylerian" -> vaylerian;
            case "veldyr" -> veldyr;
            case "zealots" -> zealots;
            case "zelian" -> zelian;

            case "admins" -> AdminsFaction;
            case "qulane" -> Qulane;

            case "franken1" -> Franken1;
            case "franken2" -> Franken2;
            case "franken3" -> Franken3;
            case "franken4" -> Franken4;
            case "franken5" -> Franken5;
            case "franken6" -> Franken6;
            case "franken7" -> Franken7;
            case "franken8" -> Franken8;
            case "franken9" -> Franken9;
            case "franken10" -> Franken10;
            case "franken11" -> Franken11;
            case "franken12" -> Franken12;
            case "franken13" -> Franken13;
            case "franken14" -> Franken14;
            case "franken15" -> Franken15;
            case "franken16" -> Franken16;

            case "echoes" -> echoes;
            case "enclave" -> enclave;
            case "raven" -> raven;
            case "syndicate" -> syndicate;
            case "terminator" -> terminator;

            case "netharii" -> netharii;

            default -> getRandomizedEmoji(0, null);
        };
    }

    public static String getPlanetEmoji(String planet) {
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
            case "archonvail" -> ArchonVail;
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
            case "creuss", "hexcreuss" -> Creuss;
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
            case "aranndan", "aranndanb" -> Aranndan;
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
            case "grywon", "grywonb" -> Grywon;
            case "hellsmaw" -> HellsMaw;
            case "hersey" -> Hersey;
            case "heska" -> Heska;
            case "hevahold", "hevaholdb" -> Hevahold;
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
            case "ultimur", "ultimurb" -> Ultimur;
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

            default -> SemLore;
        };
    }

    public static String getColorEmojiWithName(String color) {
        return switch (color) {
            case "gry", "gray" -> gray + "**Gray**";
            case "blk", "black" -> black + "**Black**";
            case "blu", "blue" -> blue + "**Blue**";
            case "grn", "green" -> green + "**Green**";
            case "org", "orange" -> orange + "**Orange**";
            case "pnk", "pink" -> pink + "**Pink**";
            case "ppl", "purple" -> purple + "**Purple**";
            case "red" -> red + "**Red**";
            case "ylw", "yellow" -> yellow + "**Yellow**";
            case "ptr", "petrol" -> petrol + "**Petrol**";
            case "bwn", "brown" -> brown + "**Brown**";
            case "tan" -> tan + "**Tan**";
            case "frs", "forest" -> forest + "**Forest**";
            case "crm", "chrome" -> chrome + "**Chrome**";
            case "sns", "sunset" -> sunset + "**Sunset**";
            case "tqs", "turquoise" -> turquoise + "**Turquoise**";
            case "gld", "gold" -> gold + "**Gold**";
            case "lgy", "lightgray" -> lightgray + "**LightGray**";
            case "tea", "teal" -> teal + "**Teal**";
            case "bld", "bloodred" -> bloodred + "**BloodRed**";
            case "eme", "emerald" -> emerald + "**Emerald**";
            case "nvy", "navy" -> navy + "**Navy**";
            case "rse", "rose" -> rose + "**Rose**";
            case "lme", "lime" -> lime + "**Lime**";
            case "lvn", "lavender" -> lavender + "**Lavender**";
            case "spr", "spring" -> spring + "**Spring**";
            case "chk", "chocolate" -> chocolate + "**Chocolate**";
            case "rbw", "rainbow" -> rainbow + "**Rainbow**";
            case "eth", "ethereal" -> ethereal + "**Ethereal**";
            case "orca" -> orca + "**Orca**";
            case "splitred" -> splitred + "**SplitRed**";
            case "splitblu", "splitblue" -> splitblue + "**SplitBlue**";
            case "splitgrn", "splitgreen" -> splitgreen + "**SplitGreen**";
            case "splitppl", "splitpurple" -> splitpurple + "**SplitPurple**";
            case "splitorg", "splitorange" -> splitorange + "**SplitOrange**";
            case "splitylw", "splityellow" -> splityellow + "**SplitYellow**";
            case "splitpnk", "splitpink" -> splitpink + "**SplitPink**";
            case "splitgld", "splitgold" -> splitgold + "**SplitGold**";
            case "splitlme", "splitlime" -> splitlime + "**SplitLime**";
            case "splittan" -> splittan + "**SplitTan**";
            case "splittea", "splitteal" -> splitteal + "**SplitTeal**";
            case "splittqs", "splitturquoise" -> splitturquoise + "**SplitTurquoise**";
            case "splitbld", "splitbloodred" -> splitbloodred + "**SplitBloodRed**";
            case "splitchk", "splitchocolate" -> splitchocolate + "**SplitChocolate**";
            case "spliteme", "splitemerald" -> splitemerald + "**SplitEmerald**";
            case "splitnvy", "splitnavy" -> splitnavy + "**SplitNavy**";
            case "splitptr", "splitpetrol" -> splitpetrol + "**SplitPetrol**";
            case "splitrbw", "splitrainbow" -> splitrainbow + "**SplitRainbow**";
            case "ero", "riftset" -> riftset + "**RiftSet**";
            default -> color;
        };
    }

    public static String getColorEmoji(String color) {
        return switch (color) {
            case "gry", "gray" -> gray;
            case "blk", "black" -> black;
            case "blu", "blue" -> blue;
            case "grn", "green" -> green;
            case "org", "orange" -> orange;
            case "pnk", "pink" -> pink;
            case "ppl", "purple" -> purple;
            case "red" -> red;
            case "ylw", "yellow" -> yellow;
            case "ptr", "petrol" -> petrol;
            case "bwn", "brown" -> brown;
            case "tan" -> tan;
            case "frs", "forest" -> forest;
            case "crm", "chrome" -> chrome;
            case "sns", "sunset" -> sunset;
            case "tqs", "turquoise" -> turquoise;
            case "gld", "gold" -> gold;
            case "lgy", "lightgray" -> lightgray;
            case "tea", "teal" -> teal;
            case "bld", "bloodred" -> bloodred;
            case "eme", "emerald" -> emerald;
            case "nvy", "navy" -> navy;
            case "rse", "rose" -> rose;
            case "lme", "lime" -> lime;
            case "lvn", "lavender" -> lavender;
            case "spr", "spring" -> spring;
            case "chk", "chocolate" -> chocolate;
            case "rbw", "rainbow" -> rainbow;
            case "eth", "ethereal" -> ethereal;
            case "orca" -> orca;
            case "splitred" -> splitred;
            case "splitblu", "splitblue" -> splitblue;
            case "splitgrn", "splitgreen" -> splitgreen;
            case "splitppl", "splitpurple" -> splitpurple;
            case "splitorg", "splitorange" -> splitorange;
            case "splitylw", "splityellow" -> splityellow;
            case "splitpnk", "splitpink" -> splitpink;
            case "splitgld", "splitgold" -> splitgold;
            case "splitlme", "splitlime" -> splitlime;
            case "splittan" -> splittan;
            case "splittea", "splitteal" -> splitteal;
            case "splittqs", "splitturquoise" -> splitturquoise;
            case "splitbld", "splitbloodred" -> splitbloodred;
            case "splitchk", "splitchocolate" -> splitchocolate;
            case "spliteme", "splitemerald" -> splitemerald;
            case "splitnvy", "splitnavy" -> splitnavy;
            case "splitptr", "splitpetrol" -> splitpetrol;
            case "splitrbw", "splitrainbow" -> splitrainbow;
            case "ero", "riftset" -> riftset;

            default -> getRandomGoodDog();
        };
    }

    public static String getInfluenceEmoji(int count) {
        return switch (count) {
            case 0 -> Influence_0;
            case 1 -> Influence_1;
            case 2 -> Influence_2;
            case 3 -> Influence_3;
            case 4 -> Influence_4;
            case 5 -> Influence_5;
            case 6 -> Influence_6;
            case 7 -> Influence_7;
            case 8 -> Influence_8;
            case 9 -> Influence_9;
            default -> influence + count;
        };
    }

    public static String getResourceEmoji(int count) {
        return switch (count) {
            case 0 -> Resources_0;
            case 1 -> Resources_1;
            case 2 -> Resources_2;
            case 3 -> Resources_3;
            case 4 -> Resources_4;
            case 5 -> Resources_5;
            case 6 -> Resources_6;
            case 7 -> Resources_7;
            case 8 -> Resources_8;
            case 9 -> Resources_9;
            default -> resources + count;
        };
    }

    public static String getToesEmoji(int count) {
        return switch (count) {
            case 0 -> NoToes;
            case 1 -> OneToe;
            case 2 -> TwoToes;
            case 3 -> ThreeToes;
            case 4 -> FourToes;
            case 5 -> FiveToes;
            case 6 -> SixToes;
            case 7 -> SevenToes;
            case 8 -> EightToes;
            case 9 -> NineToes;
            default -> NoToes + count;
        };
    }

    public static String getMiltyDraftEmoji(int ord) {
        return switch (ord) {
            case 1 -> sliceA;
            case 2 -> sliceB;
            case 3 -> sliceC;
            case 4 -> sliceD;
            case 5 -> sliceE;
            case 6 -> sliceF;
            case 7 -> sliceG;
            case 8 -> sliceH;
            case 9 -> sliceI;
            case 10 -> sliceJ;
            case 11 -> sliceK;
            case 12 -> sliceL;
            case 13 -> sliceM;
            case 14 -> sliceN;
            case 15 -> sliceO;
            case 16 -> sliceP;
            case 17 -> sliceQ;
            case 18 -> sliceR;
            case 19 -> sliceS;
            case 20 -> sliceT;
            case 21 -> sliceU;
            case 22 -> sliceV;
            case 23 -> sliceW;
            case 24 -> sliceX;
            case 25 -> sliceY;
            case 26 -> sliceZ;
            default -> Integer.toString(ord);
        };
    }

    public static String getMiltyDraftEmoji(String ord) {
        return switch (ord.toLowerCase()) {
            case "1", "a" -> sliceA;
            case "2", "b" -> sliceB;
            case "3", "c" -> sliceC;
            case "4", "d" -> sliceD;
            case "5", "e" -> sliceE;
            case "6", "f" -> sliceF;
            case "7", "g" -> sliceG;
            case "8", "h" -> sliceH;
            case "9", "i" -> sliceI;
            case "10", "j" -> sliceJ;
            case "11", "k" -> sliceK;
            case "12", "l" -> sliceL;
            case "13", "m" -> sliceM;
            case "14", "n" -> sliceN;
            case "15", "o" -> sliceO;
            case "16", "p" -> sliceP;
            case "17", "q" -> sliceQ;
            case "18", "r" -> sliceR;
            case "19", "s" -> sliceS;
            case "20", "t" -> sliceT;
            case "21", "u" -> sliceU;
            case "22", "v" -> sliceV;
            case "23", "w" -> sliceW;
            case "24", "x" -> sliceX;
            case "25", "y" -> sliceY;
            case "26", "z" -> sliceZ;
            default -> ord;
        };
    }

    public static String getSpeakerPickEmoji(int ord) {
        return switch (ord) {
            case 1 -> speakerPick1;
            case 2 -> speakerPick2;
            case 3 -> speakerPick3;
            case 4 -> speakerPick4;
            case 5 -> speakerPick5;
            case 6 -> speakerPick6;
            case 7 -> speakerPick7;
            case 8 -> speakerPick8;
            case 9 -> speakerPick9;
            case 10 -> speakerPick10;
            case 11 -> speakerPick11;
            case 12 -> speakerPick12;
            default -> getToesEmoji(ord);
        };
    }

    public static String getFactionLeaderEmoji(Leader leader) {
        return getEmojiFromDiscord(leader.getId());
    }

    /**
     * Takes an emoji's name string and returns its full name including ID.
     * 
     * @emojiName the name of the emoji as entered on the Emoji section of the
     *            server
     * @return the name of the emoji including ID
     */
    public static String getEmojiFromDiscord(String emojiName) {
        return switch (emojiName.toLowerCase()) {
            // EXPLORATION
            case "hfrag" -> HFrag;
            case "cfrag" -> CFrag;
            case "ifrag" -> IFrag;
            case "ufrag" -> UFrag;
            case "relic" -> Relic;
            case "cultural" -> Cultural;
            case "industrial" -> Industrial;
            case "hazardous" -> Hazardous;
            case "frontier" -> Frontier;

            // CARDS
            case "sc1" -> SC1;
            case "sc2" -> SC2;
            case "sc3" -> SC3;
            case "sc4" -> SC4;
            case "sc5" -> SC5;
            case "sc6" -> SC6;
            case "sc7" -> SC7;
            case "sc8" -> SC8;
            case "sc1back" -> SC1Back;
            case "sc2back" -> SC2Back;
            case "sc3back" -> SC3Back;
            case "sc4back" -> SC4Back;
            case "sc5back" -> SC5Back;
            case "sc6back" -> SC6Back;
            case "sc7back" -> SC7Back;
            case "sc8back" -> SC8Back;
            case "actioncard" -> ActionCard;
            case "agenda" -> Agenda;
            case "pn" -> PN;

            // OBJECTIVES
            case "secretobjective" -> SecretObjective;
            case "public1" -> Public1;
            case "public2" -> Public2;
            case "public1alt" -> Public1alt;
            case "public2alt" -> Public2alt;
            case "secretobjectivealt" -> SecretObjectiveAlt;

            // COMPONENTS
            case "tg" -> tg;
            case "comm" -> comm;
            case "sleeper" -> Sleeper;
            case "sleeperb" -> SleeperB;

            // UNITS
            case "warsun", "ws" -> warsun;
            case "spacedock", "sd" -> spacedock;
            case "pds", "pd" -> pds;
            case "mech", "mf" -> mech;
            case "infantry", "gf" -> infantry;
            case "flagship", "lady", "cavalry", "fs" -> flagship;
            case "fighter", "ff" -> fighter;
            case "dreadnought", "dn" -> dreadnought;
            case "destroyer", "dd" -> destroyer;
            case "carrier", "cv" -> carrier;
            case "cruiser", "ca" -> cruiser;
            case "tyrantslament" -> TyrantsLament;
            case "plenaryorbital" -> PlenaryOrbital;
            case "monument" -> Monument;

            // LEADERS - AGENTS
            case "arborecagent" -> ArborecAgent;
            case "argentagent" -> ArgentAgent;
            case "cabalagent" -> CabalAgent;
            case "ghostagent", "creussagent" -> CreussAgent;
            case "empyreanagent" -> EmpyreanAgent;
            case "hacanagent" -> HacanAgent;
            case "jolnaragent" -> JolNarAgent;
            case "keleresagent" -> KeleresAgent;
            case "l1z1xagent" -> L1Z1XAgent;
            case "letnevagent" -> LetnevAgent;
            case "mahactagent" -> MahactAgent;
            case "mentakagent", "kelerescommander" -> MentakAgent;
            case "muaatagent" -> MuaatAgent;
            case "naaluagent" -> NaaluAgent;
            case "naazagent" -> NaazAgent;
            case "nekroagent" -> NekroAgent;
            case "nomadagentartuno" -> NomadAgentArtuno;
            case "nomadagentmercer" -> NomadAgentMercer;
            case "nomadagentthundarian" -> NomadAgentThundarian;
            case "sardakkagent" -> SardakkAgent;
            case "saaragent" -> SaarAgent;
            case "solagent" -> SolAgent;
            case "titansagent" -> TitansAgent;
            case "winnuagent" -> WinnuAgent;
            case "xxchaagent" -> XxchaAgent;
            case "yinagent" -> YinAgent;
            case "yssarilagent" -> YssarilAgent;

            // LEADERS - COMMANDERS
            case "arboreccommander" -> ArborecCommander;
            case "argentcommander" -> ArgentCommander;
            case "cabalcommander" -> CabalCommander;
            case "ghostcommander", "creusscommander" -> CreussCommander;
            case "empyreancommander" -> EmpyreanCommander;
            case "hacancommander" -> HacanCommander;
            case "jolnarcommander" -> JolNarCommander;
            case "l1z1xcommander" -> L1Z1XCommander;
            case "letnevcommander" -> LetnevCommander;
            case "mahactcommander" -> MahactCommander;
            case "mentakcommander" -> MentakCommander;
            case "muaatcommander" -> MuaatCommander;
            case "naalucommander" -> NaaluCommander;
            case "naazcommander" -> NaazCommander;
            case "nekrocommander" -> NekroCommander;
            case "nomadcommander" -> NomadCommander;
            case "sardakkcommander" -> SardakkCommander;
            case "saarcommander" -> SaarCommander;
            case "solcommander" -> SolCommander;
            case "titanscommander" -> TitansCommander;
            case "winnucommander" -> WinnuCommander;
            case "xxchacommander" -> XxchaCommander;
            case "yincommander" -> YinCommander;
            case "yssarilcommander" -> YssarilCommander;

            // LEADERS - HEROES
            case "arborechero" -> ArborecHero;
            case "argenthero" -> ArgentHero;
            case "cabalhero" -> CabalHero;
            case "ghosthero", "creusshero" -> CreussHero;
            case "empyreanhero" -> EmpyreanHero;
            case "hacanhero" -> HacanHero;
            case "jolnarhero" -> JolNarHero;
            case "keleresherokuuasi", "keleresahero" -> KeleresHeroKuuasi;
            case "keleresheroodlynn", "keleresxhero" -> KeleresHeroOdlynn;
            case "keleresheroharka", "keleresmhero" -> KeleresHeroHarka;
            case "l1z1xhero" -> L1Z1XHero;
            case "letnevhero" -> LetnevHero;
            case "mahacthero" -> MahactHero;
            case "mentakhero" -> MentakHero;
            case "muaathero" -> MuaatHero;
            case "naaluhero" -> NaaluHero;
            case "naazhero" -> NaazHero;
            case "nekrohero" -> NekroHero;
            case "nomadhero" -> NomadHero;
            case "sardakkhero" -> SardakkHero;
            case "saarhero" -> SaarHero;
            case "solhero" -> SolHero;
            case "titanshero" -> TitansHero;
            case "winnuhero" -> WinnuHero;
            case "xxchahero" -> XxchaHero;
            case "yinhero" -> YinHero;
            case "yssarilhero" -> YssarilHero;

            // DS LEADERS
            case "augersagent" -> AugersAgent;
            case "augerscommander" -> AugersCommander;
            case "augershero" -> AugersHero;
            case "axisagent" -> AxisAgent;
            case "axiscommander" -> AxisCommander;
            case "axishero" -> AxisHero;
            case "bentoragent" -> BentorAgent;
            case "bentorcommander" -> BentorCommander;
            case "bentorhero" -> BentorHero;
            case "blexagent" -> BlexAgent;
            case "blexcommander" -> BlexCommander;
            case "blexhero" -> BlexHero;
            case "celdauriagent" -> CeldauriAgent;
            case "celdauricommander" -> CeldauriCommander;
            case "celdaurihero" -> CeldauriHero;
            case "cheiranagent" -> CheiranAgent;
            case "cheirancommander" -> CheiranCommander;
            case "cheiranhero" -> CheiranHero;
            case "gheminaagent" -> GheminaAgent;
            case "gheminacommander" -> GheminaCommander;
            case "gheminaherolady" -> GheminaHeroLady;
            case "gheminaherolord" -> GheminaHeroLord;
            case "cymiaeagent" -> CymiaeAgent;
            case "cymiaecommander" -> CymiaeCommander;
            case "cymiaehero" -> CymiaeHero;
            case "dihmohnagent" -> DihmohnAgent;
            case "dihmohncommander" -> DihmohnCommander;
            case "dihmohnhero" -> DihmohnHero;
            case "edynagent" -> EdynAgent;
            case "edyncommander" -> EdynCommander;
            case "edynhero" -> EdynHero;
            case "florzenagent" -> FlorzenAgent;
            case "florzencommander" -> FlorzenCommander;
            case "florzenhero" -> FlorzenHero;
            case "freesystemcommander" -> FreesystemCommander;
            case "freesystemhero" -> FreesystemHero;
            case "freesystemsagent" -> FreesystemsAgent;
            case "ghotiagent" -> GhotiAgent;
            case "ghoticommander" -> GhotiCommander;
            case "ghotihero" -> GhotiHero;
            case "gledgeagent" -> GledgeAgent;
            case "gledgecommander" -> GledgeCommander;
            case "gledgehero" -> GledgeHero;
            case "khraskagent" -> KhraskAgent;
            case "khraskcommander" -> KhraskCommander;
            case "khraskhero" -> KhraskHero;
            case "kjalengardagent" -> KjalengardAgent;
            case "kjalengardcommander" -> KjalengardCommander;
            case "kjalengardhero" -> KjalengardHero;
            // case "kollecagent" -> "";
            case "kollecccommander" -> KolleccCommander;
            case "kollecchero" -> KolleccHero;
            case "kolumeagent" -> KolumeAgent;
            case "kolumecommander" -> KolumeCommander;
            case "kolumehero" -> KolumeHero;
            case "kortaliagent" -> KortaliAgent;
            case "kortalicommander" -> KortaliCommander;
            case "kortalihero" -> KortaliHero;
            case "lanefiragent" -> LanefirAgent;
            case "lanefircommander" -> LanefirCommander;
            // case "lanefirhero" -> "";

            // TILES
            case "emptysystem", "empty_nonanomaly" -> EmptySystem;
            case "anomaly" -> Anomaly;

            // OTHER
            case "whalpha" -> WHalpha;
            case "grift" -> GravityRift;
            case "whbeta" -> WHbeta;
            case "whgamma" -> WHgamma;
            case "creussalpha" -> CreussAlpha;
            case "creussbeta" -> CreussBeta;
            case "creussgamma" -> CreussGamma;
            case "influence" -> influence;
            case "resources" -> resources;
            case "legendaryplanet", "legendary" -> LegendaryPlanet;
            case "mecatol_rex" -> Mecatol;

            // TECH
            case "cybernetictech" -> CyberneticTech;
            case "propulsiontech" -> PropulsionTech;
            case "biotictech" -> BioticTech;
            case "warfaretech" -> WarfareTech;
            case "unitupgradetech" -> UnitUpgradeTech;
            case "tech_specialty" -> PropulsionTech + CyberneticTech + BioticTech + WarfareTech; // just for Monument Embed's Description field (will break if need for full emoji)

            default -> getRandomGoodDog(emojiName);
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

    public static String getTGorNomadCoinEmoji(Game game) {
        if (game == null)
            return tg;
        return game.isNomadCoin() ? nomadcoin : tg;
    }

    public static String getLeaderTypeEmoji(String type) {
        type = type.toLowerCase();
        return switch (type) {
            case "agent" -> Agent;
            case "commander" -> Commander;
            case "hero" -> Hero;
            case "envoy" -> Envoy;
            default -> getRandomGoodDog(type);
        };
    }

    public static String tg(int count) {
        return StringUtils.repeat(Emojis.tg, count);
    }

    public static String comm(int count) {
        return StringUtils.repeat(Emojis.comm, count);
    }

    public static String getRedDieEmoji(int value) {
        return switch (value) {
            case 0, 10 -> Emojis.d10red_0;
            case 1 -> Emojis.d10red_1;
            case 2 -> Emojis.d10red_2;
            case 3 -> Emojis.d10red_3;
            case 4 -> Emojis.d10red_4;
            case 5 -> Emojis.d10red_5;
            case 6 -> Emojis.d10red_6;
            case 7 -> Emojis.d10red_7;
            case 8 -> Emojis.d10red_8;
            case 9 -> Emojis.d10red_9;
            default -> String.valueOf(value);
        };
    }

    public static String getGreenDieEmoji(int value) {
        return switch (value) {
            case 0, 10 -> Emojis.d10green_0;
            case 1 -> Emojis.d10green_1;
            case 2 -> Emojis.d10green_2;
            case 3 -> Emojis.d10green_3;
            case 4 -> Emojis.d10green_4;
            case 5 -> Emojis.d10green_5;
            case 6 -> Emojis.d10green_6;
            case 7 -> Emojis.d10green_7;
            case 8 -> Emojis.d10green_8;
            case 9 -> Emojis.d10green_9;
            default -> String.valueOf(value);
        };
    }

    public static String getBlueDieEmoji(int value) {
        return switch (value) {
            case 0, 10 -> Emojis.d10blue_0;
            case 1 -> Emojis.d10blue_1;
            case 2 -> Emojis.d10blue_2;
            case 3 -> Emojis.d10blue_3;
            case 4 -> Emojis.d10blue_4;
            case 5 -> Emojis.d10blue_5;
            case 6 -> Emojis.d10blue_6;
            case 7 -> Emojis.d10blue_7;
            case 8 -> Emojis.d10blue_8;
            case 9 -> Emojis.d10blue_9;
            default -> String.valueOf(value);
        };
    }

    public static String getGrayDieEmoji(int value) {
        return switch (value) {
            case 0, 10 -> Emojis.d10grey_0;
            case 1 -> Emojis.d10grey_1;
            case 2 -> Emojis.d10grey_2;
            case 3 -> Emojis.d10grey_3;
            case 4 -> Emojis.d10grey_4;
            case 5 -> Emojis.d10grey_5;
            case 6 -> Emojis.d10grey_6;
            case 7 -> Emojis.d10grey_7;
            case 8 -> Emojis.d10grey_8;
            case 9 -> Emojis.d10grey_9;
            default -> String.valueOf(value);
        };
    }
}
