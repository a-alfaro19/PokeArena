package cr.ac.tec.pokearena;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import java.util.Random;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Propósito: verificar con ArchUnit las reglas de dependencia entre capas (sección 4.2).
 * Responsabilidades: fallar la compilación en CI si una clase de producción rompe las reglas
 * 1 a 5. Se analizan solo las clases de producción ({@code DoNotIncludeTests}), porque las
 * pruebas sí pueden usar Spring, Random o el reloj real.
 * Referencia: sección 4.2, reglas 1 a 6, y sección 4.1 (dónde vive cada puerto).
 *
 * <p>Ninguna regla admite quedar vacía: si un paquete se renombrara y una regla dejara de encontrar
 * clases, ArchUnit falla en lugar de aprobar sin revisar nada.
 */
@AnalyzeClasses(packages = ArchitectureRulesTest.BASE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    // Paquetes de cada capa, con nombre para no repetir cadenas en cada regla.
    static final String BASE = "cr.ac.tec.pokearena";
    private static final String API = BASE + ".api..";
    private static final String APPLICATION = BASE + ".application..";
    private static final String DOMAIN = BASE + ".domain..";
    private static final String DOMAIN_REPOSITORY = BASE + ".domain.repository..";
    private static final String INFRASTRUCTURE = BASE + ".infrastructure..";
    private static final String INFRASTRUCTURE_IDENTITY = BASE + ".infrastructure.identity..";

    // Bloque de reglas 1, 2 y 4: quién puede usar a quién. Solo cuentan las dependencias
    // entre capas; las bibliotecas externas se revisan en las reglas siguientes.
    @ArchTest
    static final ArchRule layersRespectTheirAllowedDependencies = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("API").definedBy(API)
            .layer("Aplicación").definedBy(APPLICATION)
            .layer("Dominio").definedBy(DOMAIN)
            .layer("Infraestructura").definedBy(INFRASTRUCTURE)
            .whereLayer("API").mayNotBeAccessedByAnyLayer()
            .whereLayer("Aplicación").mayOnlyBeAccessedByLayers("API", "Infraestructura")
            .whereLayer("Dominio").mayOnlyBeAccessedByLayers("API", "Aplicación", "Infraestructura")
            .whereLayer("Infraestructura").mayNotBeAccessedByAnyLayer()
            .because("reglas 1, 2 y 4 de 4.2: api usa application; application usa domain; "
                    + "infrastructure implementa los puertos y nadie depende de ella");

    // Regla 1 (segunda parte): la API no accede a los repositorios, solo a los casos de uso.
    @ArchTest
    static final ArchRule apiDoesNotUseRepositories = noClasses()
            .that().resideInAPackage(API)
            .should().dependOnClassesThat().resideInAPackage(DOMAIN_REPOSITORY)
            .because("regla 1 de 4.2: la API no puede acceder a repositorios");

    // Regla 3: el dominio solo depende de java.* y de sí mismo.
    @ArchTest
    static final ArchRule domainDependsOnlyOnJava = classes()
            .that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN)
            .because("regla 3 de 4.2: domain no depende de nada salvo java.*");

    // Regla 3 (detalle): java.sql y javax.sql están bajo java.* o javax.*, así que se prohíben
    // de forma explícita junto con Spring y Jakarta.
    @ArchTest
    static final ArchRule domainDoesNotUseSpringOrJdbc = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "java.sql..", "javax.sql..")
            .because("regla 3 de 4.2: prohibido Spring, Jakarta y JDBC en domain");

    // Secciones 4.3 y 4.4: los casos de uso no usan anotaciones ni clases de Spring.
    @ArchTest
    static final ArchRule applicationDoesNotUseSpring = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..")
            .because("secciones 4.3 y 4.4: dominio y aplicación sin Spring; la transacción "
                    + "se controla con el puerto UnitOfWork");

    // Regla 5: el reloj entra por un puerto. Se prohíbe cualquier now() de java.time
    // (LocalDateTime, Instant, LocalDate, ...), no solo el de LocalDateTime.
    @ArchTest
    static final ArchRule noDirectClock = noClasses()
            .that().resideInAnyPackage(DOMAIN, APPLICATION)
            .should().callMethodWhere(callTo("now() de java.time",
                    call -> call.getTargetOwner().getPackageName().equals("java.time")
                            && call.getTarget().getName().equals("now")))
            .because("regla 5 de 4.2: el reloj entra por el puerto Clock");

    // Regla 5: la aleatoriedad entra por un puerto. Random cubre también SecureRandom y
    // ThreadLocalRandom, que heredan de él.
    @ArchTest
    static final ArchRule noDirectRandom = noClasses()
            .that().resideInAnyPackage(DOMAIN, APPLICATION)
            .should().dependOnClassesThat().areAssignableTo(Random.class)
            .because("regla 5 de 4.2: la aleatoriedad entra por el puerto RandomSource");

    // Regla 5: otras fuentes de aleatoriedad que no pasan por la clase Random.
    @ArchTest
    static final ArchRule noRandomUuidOrMathRandom = noClasses()
            .that().resideInAnyPackage(DOMAIN, APPLICATION)
            .should().callMethodWhere(callTo("UUID.randomUUID() o Math.random()",
                    call -> isCall(call, "java.util.UUID", "randomUUID")
                            || isCall(call, "java.lang.Math", "random")))
            .because("regla 5 de 4.2: identificadores y azar entran por el puerto RandomSource");

    // RES-01 y sección 3: la biblioteca JWT solo firma y verifica, y solo dentro de
    // infrastructure.identity. El resto del sistema usa los puertos TokenIssuer y TokenVerifier.
    @ArchTest
    static final ArchRule jwtLibraryOnlyInIdentity = noClasses()
            .that().resideOutsideOfPackage(INFRASTRUCTURE_IDENTITY)
            .should().dependOnClassesThat().resideInAPackage("com.nimbusds..")
            .because("RES-01: la biblioteca JWT solo ejecuta la firma y la verificación en "
                    + "infrastructure.identity; las demás capas usan TokenIssuer y TokenVerifier");

    // Sección 4.1: "todos los puertos de persistencia viven en domain.repository; los demás puertos, en
    // application.port". Un puerto de persistencia es una interfaz *Repository; su implementación JDBC
    // (Jdbc*Repository) es una clase de infrastructure.persistence y no cuenta.
    @ArchTest
    static final ArchRule persistencePortsOnlyInDomainRepository = classes()
            .that().areInterfaces().and().haveSimpleNameEndingWith("Repository")
            .should().resideInAPackage(DOMAIN_REPOSITORY)
            .because("sección 4.1: los puertos de persistencia viven solo en domain.repository");

    // Sección 4.1 (el otro lado): domain.repository solo contiene puertos, nunca implementaciones.
    @ArchTest
    static final ArchRule domainRepositoryHoldsOnlyPorts = classes()
            .that().resideInAPackage(DOMAIN_REPOSITORY).and().doNotHaveSimpleName("package-info")
            .should().beInterfaces()
            .because("sección 4.1: domain.repository solo declara puertos; infrastructure.persistence los implementa");

    private static DescribedPredicate<JavaMethodCall> callTo(
            String description, java.util.function.Predicate<JavaMethodCall> condition) {
        return DescribedPredicate.describe(description, condition);
    }

    private static boolean isCall(JavaMethodCall call, String ownerName, String methodName) {
        return call.getTargetOwner().getName().equals(ownerName) && call.getTarget().getName().equals(methodName);
    }
}
