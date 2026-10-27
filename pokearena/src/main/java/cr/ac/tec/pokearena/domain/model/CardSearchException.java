package cr.ac.tec.pokearena.domain.model;

// Expresa fallos del contrato sin introducir códigos HTTP en dominio o aplicación.
public class CardSearchException extends RuntimeException {
    public enum Reason { INVALID, UNAUTHENTICATED, FORBIDDEN, EXPIRED, UNAVAILABLE }

    private final Reason reason;
    private final String criterion;

    public CardSearchException(Reason reason, String criterion, String message) {
        super(message);
        this.reason = reason;
        this.criterion = criterion;
    }

    public Reason reason() { return reason; }
    public String criterion() { return criterion; }

    public static CardSearchException invalid(String criterion, String message) {
        return new CardSearchException(Reason.INVALID, criterion, message);
    }
}
