package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: energía extra por turno que entrega una ubicación (criterio C.2; sección 7.2, paso 5).
 * Responsabilidades: combinar la modalidad con su monto y proteger el rango de cada modalidad:
 * sin energía (monto 0), fija (0 a 3 por turno) o por cartas (0 a 2 por cada carta propia). Cada
 * modalidad lleva además su nombre para el usuario, que la interfaz muestra en el formulario.
 * Referencia: sección 6 (columnas extra_energy_mode y extra_energy_amount) y criterio C.2.
 *
 * @param mode   modalidad
 * @param amount monto, dentro del rango de la modalidad
 */
public record ExtraEnergy(Mode mode, int amount) {

    /** Monto mínimo de cualquier modalidad. */
    public static final int MIN_AMOUNT = 0;

    /**
     * Modalidad que la interfaz muestra seleccionada por defecto (sección 7.2): así el usuario no tiene que
     * elegirla, y la API sigue recibiendo siempre una modalidad explícita.
     */
    public static final Mode DEFAULT_MODE = Mode.NONE;

    /** Modalidad de la energía extra, con el monto máximo de cada una. */
    public enum Mode {
        /** Sin energía extra: el monto es siempre 0. */
        NONE("Sin energía extra", Mode.NO_ENERGY),
        /** Fija: cada jugador recibe el monto al inicio de cada turno (0 a 3). */
        FIXED("Fija por turno", Mode.MAX_FIXED_AMOUNT),
        /** Por cartas: cada jugador recibe el monto por cada carta propia en la ubicación (0 a 2). */
        PER_CARD("Por carta propia", Mode.MAX_PER_CARD_AMOUNT);

        private static final int NO_ENERGY = 0;
        private static final int MAX_FIXED_AMOUNT = 3;
        private static final int MAX_PER_CARD_AMOUNT = 2;

        private final String label;
        private final int maxAmount;

        Mode(String label, int maxAmount) {
            this.label = label;
            this.maxAmount = maxAmount;
        }

        /** Nombre de la modalidad para el usuario. */
        public String label() {
            return label;
        }

        public int maxAmount() {
            return maxAmount;
        }
    }

    public ExtraEnergy {
        Objects.requireNonNull(mode, "La modalidad es obligatoria");
        // Segunda barrera: LocationFieldRules ya validó con errores tipados.
        if (amount < MIN_AMOUNT || amount > mode.maxAmount()) {
            throw new IllegalArgumentException("Monto " + amount + " fuera del rango de " + mode);
        }
    }

    /** Sin energía extra. */
    public static ExtraEnergy none() {
        return new ExtraEnergy(Mode.NONE, MIN_AMOUNT);
    }
}
