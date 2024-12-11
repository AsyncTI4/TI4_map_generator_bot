package ti4.service.emoji;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import ti4.image.TileHelper;
import ti4.model.TileModel;

public enum TileEmojis implements TI4Emoji {

    // tile backs
    TileGreenBack, TileRedBack, TileBlueBack,

    // base
    Jord_01, MollPrimus_02, Darien_03, Muaat_04, Nestphar_05, L1_000_06, Winnu_07, MordaiII_08, //
    Maaluuk_09, ArcPime_10, LisisII_11, Nar_12, Trenlak_13, ArchonRen_14, Retillion_15, Arretze_16, //
    DeltaWH_17, MR_18, Wellon_19, VefutII_20, Thibah_21, Tarmann_22, Saudor_23, MeharXull_24, //
    Quann_25, Lodor_26, NewAlbion_27, Tequran_28, Qucenn_29, Mellon_30, Lazar_31, DalBootha_32, //
    Corneeq_33, Centauri_34, Bereg_35, Arnor_36, Arinam_37, Abyz_38, AlphaWH_39, BetaWH_40, //
    GravityRift_41, Nebula_42, Supernova_43, Asteroids_44, Asteroids_45, Void_46, Void_47, Void_48, //
    Void_49, Void_50, Creuss_51,

    // pok
    Ixth_52, Arcturus_53, Acheron_54, Elysium_55, TheDark_56, NaazirRokha_57, YlirAvarValk_58, //
    ArchonVail_59, Perimeter_60, Ang_61, SemLore_62, Vorhal_63, Atlas_64, Primor_65, HopesEnd_66, //
    Cormund_67, Everra_68, AccoenJeolIr_69, KraagSiig_70, BakalAlioPrima_71, LisisVelnor_72, //
    CealdriXanhact_73, Vegas_74, Devils_75, Rigels_76, Void_77, Void_78, AsteroidsAlphaWH_79, //
    Supernova_80, NovaSeed_81, MalliceLocked_82a, MalliceUnlocked_82b,

    // codex 3
    ArchonRenTauKeleres_92, YlirAvarValkKeleres_93, MollPrimusKeleres_94,

    // discordant stars
    D01_Rhune, D02_Kjalengard, D03_Ekko, D04_Zarr, D05_AysisRest, D06_Alesna, D07_Benc, //
    D08_Arche, D09_LastStop, D10_Avicenna, D11_Void, D12_Gen, D13_Kroll, D14_Pax, D15_Poh, //
    D16_Louk, D17_Biaheo, D18_Delmor, D19_Demis, D20_Drah, D21_Discordia, D22_Sanctuary, D23_Aldra, //
    D24_Ogdun, D25_Ellas, D26_Vadarian, D27_Axis, D28_Vaylar, D29_Abyssus, D30_Cymiae, D31_Prind, //
    D32_ShiHalaum, D33_BohlDhur, D34_Susuros, D35a_Asteroids, D35b_Supernova, D36_Asteroids,

    // uncharted space
    d100, d101, d102, d103, d104, d105, d106, d107, d108, d109, d110, d111, d112, d113, d114, d115, //
    d116, d117, d118, d119, d120, d121, d122, d123,

    // other
    ;

    @Override
    public String toString() {
        return emojiString();
    }

    @Nullable
    public static TI4Emoji getTileEmojiFromTileID(String tileID) {
        return switch (tileID) {
            case "01" -> Jord_01;
            case "02" -> MollPrimus_02;
            case "03" -> Darien_03;
            case "04" -> Muaat_04;
            case "05" -> Nestphar_05;
            case "06" -> L1_000_06;
            case "07" -> Winnu_07;
            case "08" -> MordaiII_08;
            case "09" -> Maaluuk_09;
            case "10" -> ArcPime_10;
            case "11" -> LisisII_11;
            case "12" -> Nar_12;
            case "13" -> Trenlak_13;
            case "14" -> ArchonRen_14;
            case "15" -> Retillion_15;
            case "16" -> Arretze_16;
            case "17" -> DeltaWH_17;
            case "18" -> MR_18;
            case "19" -> Wellon_19;
            case "20" -> VefutII_20;
            case "21" -> Thibah_21;
            case "22" -> Tarmann_22;
            case "23" -> Saudor_23;
            case "24" -> MeharXull_24;
            case "25" -> Quann_25;
            case "26" -> Lodor_26;
            case "27" -> NewAlbion_27;
            case "28" -> Tequran_28;
            case "29" -> Qucenn_29;
            case "30" -> Mellon_30;
            case "31" -> Lazar_31;
            case "32" -> DalBootha_32;
            case "33" -> Corneeq_33;
            case "34" -> Centauri_34;
            case "35" -> Bereg_35;
            case "36" -> Arnor_36;
            case "37" -> Arinam_37;
            case "38" -> Abyz_38;
            case "39" -> AlphaWH_39;
            case "40" -> BetaWH_40;
            case "41" -> GravityRift_41;
            case "42" -> Nebula_42;
            case "43" -> Supernova_43;
            case "44" -> Asteroids_44;
            case "45" -> Asteroids_45;
            case "46" -> Void_46;
            case "47" -> Void_47;
            case "48" -> Void_48;
            case "49" -> Void_49;
            case "50" -> Void_50;
            case "51" -> Creuss_51;

            // PoK
            case "52" -> Ixth_52;
            case "53" -> Arcturus_53;
            case "54" -> Acheron_54;
            case "55" -> Elysium_55;
            case "56" -> TheDark_56;
            case "57" -> NaazirRokha_57;
            case "58" -> YlirAvarValk_58;
            case "59" -> ArchonVail_59;
            case "60" -> Perimeter_60;
            case "61" -> Ang_61;
            case "62" -> SemLore_62;
            case "63" -> Vorhal_63;
            case "64" -> Atlas_64;
            case "65" -> Primor_65;
            case "66" -> HopesEnd_66;
            case "67" -> Cormund_67;
            case "68" -> Everra_68;
            case "69" -> AccoenJeolIr_69;
            case "70" -> KraagSiig_70;
            case "71" -> BakalAlioPrima_71;
            case "72" -> LisisVelnor_72;
            case "73" -> CealdriXanhact_73;
            case "74" -> Vegas_74;
            case "75" -> Devils_75;
            case "76" -> Rigels_76;
            case "77" -> Void_77;
            case "78" -> Void_78;
            case "79" -> AsteroidsAlphaWH_79;
            case "80" -> Supernova_80;
            case "81" -> NovaSeed_81;
            case "82a", "82ah" -> MalliceLocked_82a;
            case "82b", "82bh" -> MalliceUnlocked_82b;

            // Codex 3
            case "92", "92new" -> ArchonRenTauKeleres_92;
            case "93", "93new" -> YlirAvarValkKeleres_93;
            case "94", "94new" -> MollPrimusKeleres_94;

            // DS
            case "D01" -> D01_Rhune;
            case "D02" -> D02_Kjalengard;
            case "D03" -> D03_Ekko;
            case "D04" -> D04_Zarr;
            case "D05" -> D05_AysisRest;
            case "D06" -> D06_Alesna;
            case "D07" -> D07_Benc;
            case "D08" -> D08_Arche;
            case "D09" -> D09_LastStop;
            case "D10" -> D10_Avicenna;
            case "D11" -> D11_Void;
            case "D12" -> D12_Gen;
            case "D13" -> D13_Kroll;
            case "D14" -> D14_Pax;
            case "D15" -> D15_Poh;
            case "D16" -> D16_Louk;
            case "D17" -> D17_Biaheo;
            case "D18" -> D18_Delmor;
            case "D19" -> D19_Demis;
            case "D20" -> D20_Drah;
            case "D21" -> D21_Discordia;
            case "D22" -> D22_Sanctuary;
            case "D23" -> D23_Aldra;
            case "D24" -> D24_Ogdun;
            case "D25" -> D25_Ellas;
            case "D26" -> D26_Vadarian;
            case "D27" -> D27_Axis;
            case "D28" -> D28_Vaylar;
            case "D29" -> D29_Abyssus;
            case "D30" -> D30_Cymiae;
            case "D31" -> D31_Prind;
            case "D32" -> D32_ShiHalaum;
            case "D33" -> D33_BohlDhur;
            case "D34" -> D34_Susuros;
            case "D35a" -> D35a_Asteroids;
            case "D35b" -> D35b_Supernova;
            case "D36" -> D36_Asteroids;

            // Uncharted Space
            case "d100" -> d100;
            case "d101" -> d101;
            case "d102" -> d102;
            case "d103" -> d103;
            case "d104" -> d104;
            case "d105" -> d105;
            case "d106" -> d106;
            case "d107" -> d107;
            case "d108" -> d108;
            case "d109" -> d109;
            case "d110" -> d110;
            case "d111" -> d111;
            case "d112" -> d112;
            case "d113" -> d113;
            case "d114" -> d114;
            case "d115" -> d115;
            case "d116" -> d116;
            case "d117" -> d117;
            case "d118" -> d118;
            case "d119" -> d119;
            case "d120" -> d120;
            case "d121" -> d121;
            case "d122" -> d122;
            case "d123" -> d123;
            default -> getTileBackEmojiFromTileID(tileID);
        };
    }

    @Nullable
    public static TI4Emoji getTileBackEmojiFromTileID(String tileID) {
        if (!TileHelper.isValidTile(tileID)) return null;
        TileModel tileModel = TileHelper.getTileById(tileID);
        if (tileModel.getTileBackOption().isEmpty()) return null;

        return switch (tileModel.getTileBack()) {
            case "green" -> TileGreenBack;
            case "blue" -> TileBlueBack;
            case "red" -> TileRedBack;
            default -> null;
        };
    }
}
