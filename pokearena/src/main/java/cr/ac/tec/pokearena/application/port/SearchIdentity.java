package cr.ac.tec.pokearena.application.port;

import java.util.Optional;

// Permite sustituir la identidad de pruebas por un proveedor real sin acoplar HTTP al caso de uso.
public interface SearchIdentity {
    Optional<Identity> authenticate(String authorization);

    record Identity(String subject, boolean administrator) {
    }
}
