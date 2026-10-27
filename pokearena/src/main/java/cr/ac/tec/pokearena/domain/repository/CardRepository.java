package cr.ac.tec.pokearena.domain.repository;

import java.util.Optional;

import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.CardType;

public interface CardRepository {
    // Busca si ya existe una solicitud previa con la misma clave de idempotencia
    Optional<Card> findByIdempotencyKey(String idempotencyKey);

    // Valida si existe una habilidad activa por su código (ej: HAB-000)
    boolean existsAbilityByCode(String abilityCode);

    // Genera de forma atómica el siguiente consecutivo para el tipo de carta (ej:
    // PKA-FUE-001)
    String generateNextCodeForType(CardType type);

    // Guarda la imagen en el almacenamiento (local/cloud) y retorna la URL o
    // referencia
    String saveImage(byte[] imageBytes, String versionType);

    // Guarda la carta y la clave de idempotencia en una misma transacción atómica
    Card saveInTransaction(Card card, String idempotencyKey);
}
