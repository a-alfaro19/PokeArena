package cr.ac.tec.pokearena.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.model.OwnedCard;
import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestCards;
import cr.ac.tec.pokearena.support.TestPlayers;

/**
 * Propósito: verificar {@code PlayerCardRepository.findOwnedCards} sobre PostgreSQL real (historia 3.1.3).
 * Responsabilidades: comprobar que devuelve solo las cartas poseídas, con su ficha y sus copias, que ignora
 * códigos no poseídos o inexistentes y cartas de otros jugadores, y que su plan usa la clave de
 * {@code player_card} en lugar de leer la tabla completa.
 * Referencia: sección 7.1, paso 4 ("una sola consulta por la clave (player_id, card_code)"); Anexo A.7,
 * mensajes 19 y 20; sección 13 (rendimiento).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Repositorio de la colección: cartas poseídas (JDBC)")
class JdbcPlayerCardRepositoryIT extends PostgresIntegrationTestBase {

    /** Jugadores y cartas por jugador para que el planificador vea una tabla player_card realista. */
    private static final int PLAN_PLAYERS = 400;
    private static final int PLAN_CARDS_PER_PLAYER = 15;
    private static final String PLAN_MARKER = "jugador-plan-player-card";

    @Autowired
    private PlayerCardRepository playerCards;

    @Autowired
    private PlayerAccountService accounts;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> players = new ArrayList<>();
    private List<String> catalog;

    @BeforeEach
    void seedCatalog() {
        TestCards cards = new TestCards(jdbc);
        cards.reset();
        catalog = new ArrayList<>();
        for (int i = 0; i < PLAN_CARDS_PER_PLAYER + 5; i++) {
            catalog.add(cards.card("COMMON", true, true));
        }
    }

    @AfterEach
    void cleanUp() {
        TestPlayers.delete(jdbc, players);
    }

    private UUID newPlayer() {
        UUID player = TestPlayers.create(accounts).id();
        players.add(player);
        return player;
    }

    private void give(UUID player, List<String> codes) {
        unitOfWork.execute(() -> {
            playerCards.addOneCopyOfEach(player, codes);
            return null;
        });
    }

    @Test
    @DisplayName("Devuelve solo las cartas poseídas, ordenadas por código, con su ficha y sus copias")
    void ownedWithCopies() {
        UUID player = newPlayer();
        String twice = catalog.get(0);
        String once = catalog.get(1);
        String notOwned = catalog.get(2);
        give(player, List.of(twice, once));
        give(player, List.of(twice));

        List<OwnedCard> owned = playerCards.findOwnedCards(player, List.of(notOwned, once, twice, "PKA-FUE-9999"));

        assertThat(owned).extracting(card -> card.stored().card().code().value())
                .containsExactlyElementsOf(List.of(once, twice).stream().sorted().toList());
        assertThat(owned).filteredOn(card -> card.stored().card().code().value().equals(twice))
                .singleElement().extracting(OwnedCard::copies).isEqualTo(2);
        assertThat(owned.getFirst().stored().card().fields().name()).isNotBlank();
    }

    @Test
    @DisplayName("Cartas no poseídas o inexistentes no aparecen: sin cartas propias, la lista es vacía")
    void nothingOwned() {
        UUID player = newPlayer();

        assertThat(playerCards.findOwnedCards(player, List.of(catalog.get(0), "PKA-AGU-9999"))).isEmpty();
        assertThat(playerCards.findOwnedCards(player, List.of())).isEmpty();
    }

    @Test
    @DisplayName("Las cartas de otro jugador no cuentan como poseídas")
    void otherPlayersCardsDoNotCount() {
        UUID owner = newPlayer();
        UUID stranger = newPlayer();
        give(owner, List.of(catalog.get(0)));

        assertThat(playerCards.findOwnedCards(stranger, List.of(catalog.get(0)))).isEmpty();
    }

    @Test
    @DisplayName("13: el plan (EXPLAIN) usa la clave pk_player_card y no lee player_card completa")
    void planUsesPlayerCardKey() {
        // Una tabla player_card con miles de filas de muchos jugadores, en una transacción que se revierte al
        // final: así el planificador decide con estadísticas realistas y no queda nada en la base.
        List<String> codes = catalog.subList(0, PLAN_CARDS_PER_PLAYER);
        UUID player = newPlayer();
        give(player, codes);

        assertThatThrownBy(() -> unitOfWork.execute(() -> {
            jdbc.update("""
                    INSERT INTO player (display_name, role, cube_balance, created_at)
                    SELECT ?, 'PLAYER', 0, now() FROM generate_series(1, ?)
                    """, PLAN_MARKER, PLAN_PLAYERS);
            jdbc.update("""
                    INSERT INTO player_card (player_id, card_code, copies)
                    SELECT p.id, c.code, 1 FROM player p CROSS JOIN card c WHERE p.display_name = ?
                    """, PLAN_MARKER);
            jdbc.execute("ANALYZE player_card");

            String plan = String.join("\n", jdbc.queryForList(
                    "EXPLAIN " + JdbcPlayerCardRepository.ownedCardsSql(codes.size()), String.class,
                    JdbcPlayerCardRepository.ownedCardsArguments(player, codes)));
            System.out.println("Plan de findOwnedCards:\n" + plan);

            assertThat(plan).contains("pk_player_card").doesNotContain("Seq Scan on player_card");
            throw new PlanChecked();
        })).isInstanceOf(PlanChecked.class);
    }

    /** Señal para revertir la transacción de la prueba del plan después de comprobarlo. */
    private static final class PlanChecked extends RuntimeException {
    }
}
