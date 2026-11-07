package cr.ac.tec.pokearena.support;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.model.InitialCollectionState;
import cr.ac.tec.pokearena.domain.model.NewPlayer;
import cr.ac.tec.pokearena.domain.model.Player;
import cr.ac.tec.pokearena.domain.model.PlayerAccount;
import cr.ac.tec.pokearena.domain.model.ProviderIdentity;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.PlayerRepository;

/**
 * Propósito: repositorio de jugadores en memoria para las pruebas unitarias (sin Spring ni base).
 * Responsabilidades: una cuenta por identidad, como la restricción única de {@code player_identity}, y
 * registrar qué se creó y cuántas veces se escribió el rol, para que las pruebas lo comprueben.
 * Referencia: sección 8.1, paso 5.
 */
public final class InMemoryPlayerRepository implements PlayerRepository {

    private final Map<ProviderIdentity, NewPlayer> created = new HashMap<>();
    private final Map<ProviderIdentity, Player> accounts = new HashMap<>();
    // Escrituras de rol, para comprobar que no se escribe cuando el rol no cambió.
    private int roleUpdates;
    private final Set<UUID> delivered = new HashSet<>();

    /** Datos con los que se creó cada cuenta, por identidad. */
    public Map<ProviderIdentity, NewPlayer> created() {
        return created;
    }

    /** Cuántas veces se cambió un rol. */
    public int roleUpdates() {
        return roleUpdates;
    }

    @Override
    public Optional<Player> findByIdentity(ProviderIdentity identity) {
        return Optional.ofNullable(accounts.get(identity));
    }

    @Override
    public synchronized PlayerAccount findOrCreate(ProviderIdentity identity, NewPlayer candidate) {
        Player existing = accounts.get(identity);
        if (existing != null) {
            return new PlayerAccount(existing, false);
        }
        Player player = new Player(UUID.randomUUID(), candidate.displayName(), candidate.email(), candidate.role());
        accounts.put(identity, player);
        created.put(identity, candidate);
        return new PlayerAccount(player, true);
    }

    @Override
    public Optional<InitialCollectionState> lockInitialCollectionState(UUID playerId) {
        if (accounts.values().stream().noneMatch(player -> player.id().equals(playerId))) {
            return Optional.empty();
        }
        return Optional.of(delivered.contains(playerId) ? InitialCollectionState.DELIVERED
                : InitialCollectionState.PENDING);
    }

    @Override
    public void markInitialCollectionDelivered(UUID playerId) {
        delivered.add(playerId);
    }

    /** Jugadores con la colección inicial marcada como entregada. */
    public Set<UUID> delivered() {
        return delivered;
    }

    @Override
    public void updateRole(UUID playerId, Role role) {
        roleUpdates++;
        accounts.replaceAll((identity, player) -> player.id().equals(playerId)
                ? new Player(player.id(), player.displayName(), player.email(), role)
                : player);
    }
}
