package ti4.service.transaction;

import java.util.List;

public record TransactionItem(String sender, String receiver, String type, String detail) {

    private static final List<String> PRIVATE_CARD_TYPES = List.of("ACs", "PNs", "SOs");

    public static TransactionItem parse(String item) {
        String[] parts = item.split("_", 4);
        if (parts.length < 4 || !parts[0].startsWith("sending") || !parts[1].startsWith("receiving")) {
            return null;
        }
        return new TransactionItem(
                parts[0].substring("sending".length()), parts[1].substring("receiving".length()), parts[2], parts[3]);
    }

    public static TransactionItem of(String sender, String receiver, String type, Object detail) {
        return new TransactionItem(sender, receiver, type, String.valueOf(detail));
    }

    public String serialize() {
        return "sending" + sender + "_receiving" + receiver + "_" + type + "_" + detail;
    }

    public boolean isSentBy(String faction) {
        return sender.equals(faction);
    }

    public boolean isSentFromTo(String senderFaction, String receiverFaction) {
        return sender.equals(senderFaction) && receiver.equals(receiverFaction);
    }

    public boolean involves(String faction) {
        return sender.equals(faction) || receiver.equals(faction);
    }

    public boolean isPrivateCard() {
        return PRIVATE_CARD_TYPES.contains(type);
    }

    public TransactionItem hidePrivateCardIdForPublicEvent() {
        if (isPrivateCard() && !detail.startsWith("generic")) {
            return new TransactionItem(sender, receiver, type, "generic1");
        }
        return this;
    }

    public boolean hasQuantitySuffix() {
        return "frags".equalsIgnoreCase(type) || (isPrivateCard() && detail.contains("generic"));
    }

    public int quantity() {
        if (!hasQuantitySuffix()) {
            return 1;
        }
        return Integer.parseInt(detail.substring(detail.length() - 1));
    }

    public String detailWithoutQuantity() {
        if (!hasQuantitySuffix()) {
            return detail;
        }
        return detail.substring(0, detail.length() - 1);
    }
}
