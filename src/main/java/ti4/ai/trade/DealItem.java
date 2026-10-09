package ti4.ai.trade;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

record DealItem(String sender, String receiver, ItemType type, String detail) {

    private static final String GENERIC_NOTE = "generic";
    static final String ALIAS_UNDERSCORE = "fin9";
    private static final String SENDING = "sending";
    private static final String RECEIVING = "_receiving";
    private static final String SEPARATOR = "_";
    private static final String PARTIES = ">";
    private static final String FIELDS = ":";
    private static final int ENCODED_FIELDS = 3;
    private static final int FRAGMENT_KIND_LENGTH = 3;
    private static final int SINGLE = 1;
    private static final Pattern AMOUNT = Pattern.compile("\\d{1,6}");
    private static final Pattern FRAGMENT = Pattern.compile("(CRF|IRF|HRF|URF)\\d{1,2}");
    private static final Pattern NOTE = Pattern.compile(
            "\\d{1,6}|" + GENERIC_NOTE + "\\d|(?!" + GENERIC_NOTE + ")[A-Za-z0-9]*[A-Za-z][A-Za-z0-9]*");

    static Optional<DealItem> parse(String raw, String factionA, String factionB) {
        if (raw == null || !tradable(factionA) || !tradable(factionB)) return Optional.empty();
        return parseDirected(raw, factionA, factionB).or(() -> parseDirected(raw, factionB, factionA));
    }

    static boolean tradable(String faction) {
        return StringUtils.isNotEmpty(faction) && !faction.contains(SEPARATOR);
    }

    private static Optional<DealItem> parseDirected(String raw, String sender, String receiver) {
        String prefix = prefix(sender, receiver);
        if (!raw.startsWith(prefix)) return Optional.empty();
        String rest = raw.substring(prefix.length());
        ItemType type = ItemType.of(StringUtils.substringBefore(rest, SEPARATOR));
        String detail = rest.contains(SEPARATOR) ? StringUtils.substringAfter(rest, SEPARATOR) : "";
        if (!valid(type, detail)) return Optional.of(new DealItem(sender, receiver, ItemType.UNSUPPORTED, rest));
        return Optional.of(new DealItem(sender, receiver, type, detail));
    }

    private static String prefix(String sender, String receiver) {
        return SENDING + sender + RECEIVING + receiver + SEPARATOR;
    }

    private static boolean valid(ItemType type, String detail) {
        return switch (type) {
            case TRADE_GOODS, COMMODITIES, SEND_DEBT, CLEAR_DEBT ->
                AMOUNT.matcher(detail).matches();
            case PROMISSORY -> NOTE.matcher(detail).matches();
            case FRAGMENTS -> FRAGMENT.matcher(detail).matches();
            case UNSUPPORTED -> false;
        };
    }

    int amount() {
        return switch (type) {
            case TRADE_GOODS, COMMODITIES, SEND_DEBT, CLEAR_DEBT -> Integer.parseInt(detail);
            case PROMISSORY -> isGenericNote() ? Integer.parseInt(detail.substring(GENERIC_NOTE.length())) : SINGLE;
            case FRAGMENTS -> Integer.parseInt(detail.substring(FRAGMENT_KIND_LENGTH));
            case UNSUPPORTED -> SINGLE;
        };
    }

    boolean isGenericNote() {
        return type == ItemType.PROMISSORY && detail.startsWith(GENERIC_NOTE);
    }

    String fragmentKind() {
        return type == ItemType.FRAGMENTS ? detail.substring(0, FRAGMENT_KIND_LENGTH) : "";
    }

    boolean isFrom(String faction) {
        return sender.equals(faction);
    }

    boolean isTo(String faction) {
        return receiver.equals(faction);
    }

    String engineString() {
        String body = type == ItemType.UNSUPPORTED ? detail : type.token() + SEPARATOR + detail;
        return prefix(sender, receiver) + body;
    }

    String encode() {
        return sender
                + PARTIES
                + receiver
                + FIELDS
                + type.token()
                + FIELDS
                + URLEncoder.encode(detail, StandardCharsets.UTF_8);
    }

    static Optional<DealItem> decode(String encoded) {
        String[] fields = encoded == null ? new String[0] : encoded.split(FIELDS, ENCODED_FIELDS);
        if (fields.length != ENCODED_FIELDS) return Optional.empty();
        String sender = StringUtils.substringBefore(fields[0], PARTIES);
        String receiver = StringUtils.substringAfter(fields[0], PARTIES);
        if (!tradable(sender) || !tradable(receiver)) return Optional.empty();
        ItemType type = ItemType.of(fields[1]);
        if (type == ItemType.UNSUPPORTED && !fields[1].isEmpty()) return Optional.empty();
        String detail = URLDecoder.decode(fields[2], StandardCharsets.UTF_8);
        if (type != ItemType.UNSUPPORTED && !valid(type, detail)) return Optional.empty();
        return Optional.of(new DealItem(sender, receiver, type, detail));
    }
}
