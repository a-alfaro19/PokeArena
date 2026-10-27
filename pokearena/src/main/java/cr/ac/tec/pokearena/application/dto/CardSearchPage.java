package cr.ac.tec.pokearena.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// El cursor representa la siguiente página de un conjunto inmutable y temporal.
public record CardSearchPage(List<CardSearchItem> items, int total, Instant expiresAt, UUID nextCursor) {
}
