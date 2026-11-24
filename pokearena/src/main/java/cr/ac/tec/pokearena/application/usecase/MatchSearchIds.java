package cr.ac.tec.pokearena.application.usecase;

import java.util.UUID;

import cr.ac.tec.pokearena.domain.error.MatchSearchException;

/** Identificadores de búsqueda recibidos en las rutas de consulta y salida. */
final class MatchSearchIds {
    private MatchSearchIds() {
    }

    static UUID parse(String text) {
        if (text == null) {
            throw MatchSearchException.notFound();
        }
        try {
            UUID id = UUID.fromString(text);
            if (id.toString().equalsIgnoreCase(text)) {
                return id;
            }
        } catch (IllegalArgumentException invalidUuid) {
            // El identificador no corresponde a una búsqueda.
        }
        throw MatchSearchException.notFound();
    }
}
