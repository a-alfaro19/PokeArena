# Estructura del proyecto

Árbol de carpetas y archivos del repositorio. Se excluye únicamente `.git` (metadatos internos de Git).

```text
PokeArena
├── .vscode
│   └── settings.json
├── pokearena
│   ├── .mvn
│   │   └── wrapper
│   │       └── maven-wrapper.properties
│   ├── docs
│   │   ├── diagrams
│   │   │   ├── us39-initial-search.mmd
│   │   │   ├── us39-next-page.mmd
│   │   │   └── us39-no-matches.mmd
│   │   ├── us39-buscar-carta.md
│   │   ├── us39-diagramas.md
│   │   ├── us39-openapi.yaml
│   │   ├── us39-solicitud.txt
│   │   └── us39-verificacion.md
│   ├── scripts
│   │   └── test-us39.ps1
│   ├── src
│   │   ├── main
│   │   │   ├── java
│   │   │   │   └── cr
│   │   │   │       └── ac
│   │   │   │           └── tec
│   │   │   │               └── pokearena
│   │   │   │                   ├── application
│   │   │   │                   │   ├── dto
│   │   │   │                   │   │   ├── CardSearchItem.java
│   │   │   │                   │   │   ├── CardSearchPage.java
│   │   │   │                   │   │   ├── CardSearchRequest.java
│   │   │   │                   │   │   ├── CreateCardCommand.java
│   │   │   │                   │   │   └── CreateLocationCommand.java
│   │   │   │                   │   ├── port
│   │   │   │                   │   │   ├── CardSearchQuery.java
│   │   │   │                   │   │   └── SearchIdentity.java
│   │   │   │                   │   └── usecase
│   │   │   │                   │       ├── CreateCardUseCase.java
│   │   │   │                   │       ├── CreateLocationUseCase.java
│   │   │   │                   │       └── SearchCardsUseCase.java
│   │   │   │                   ├── domain
│   │   │   │                   │   ├── model
│   │   │   │                   │   │   ├── Card.java
│   │   │   │                   │   │   ├── CardRarity.java
│   │   │   │                   │   │   ├── CardSearchCriteria.java
│   │   │   │                   │   │   ├── CardSearchException.java
│   │   │   │                   │   │   ├── CardType.java
│   │   │   │                   │   │   └── Location.java
│   │   │   │                   │   └── repository
│   │   │   │                   │       ├── CardRepository.java
│   │   │   │                   │       └── LocationRepository.java
│   │   │   │                   ├── infrastructure
│   │   │   │                   │   ├── config
│   │   │   │                   │   │   ├── BeanConfiguration.java
│   │   │   │                   │   │   └── CardSearchConfiguration.java
│   │   │   │                   │   ├── persistence
│   │   │   │                   │   │   ├── CardRepositoryImpl.java
│   │   │   │                   │   │   ├── JdbcCardSearchQuery.java
│   │   │   │                   │   │   ├── LocationRepositoryImpl.java
│   │   │   │                   │   │   └── SearchSchemaMigration.java
│   │   │   │                   │   ├── storage
│   │   │   │                   │   │   └── ImageService.java
│   │   │   │                   │   └── web
│   │   │   │                   │       ├── CardController.java
│   │   │   │                   │       ├── CardSearchController.java
│   │   │   │                   │       ├── CardSearchErrorHandler.java
│   │   │   │                   │       └── LocationController.java
│   │   │   │                   └── PokearenaApplication.java
│   │   │   └── resources
│   │   │       ├── db
│   │   │       │   └── migration
│   │   │       │       └── V039__card_search.sql
│   │   │       ├── application.properties
│   │   │       └── schema.sql
│   │   └── test
│   │       └── java
│   │           └── cr
│   │               └── ac
│   │                   └── tec
│   │                       └── pokearena
│   │                           ├── CardSearchArchitectureTests.java
│   │                           ├── CardSearchPostgresTests.java
│   │                           ├── CardSearchValidationTests.java
│   │                           ├── DisposableDatabaseInitializer.java
│   │                           ├── DisposableDatabaseSafetyTests.java
│   │                           └── PokearenaApplicationTests.java
│   ├── target
│   │   ├── classes
│   │   │   ├── cr
│   │   │   │   └── ac
│   │   │   │       └── tec
│   │   │   │           └── pokearena
│   │   │   │               ├── application
│   │   │   │               │   ├── dto
│   │   │   │               │   │   ├── CardSearchItem$AbilityDetails.class
│   │   │   │               │   │   ├── CardSearchItem.class
│   │   │   │               │   │   ├── CardSearchPage.class
│   │   │   │               │   │   ├── CardSearchRequest.class
│   │   │   │               │   │   ├── CreateCardCommand.class
│   │   │   │               │   │   └── CreateLocationCommand.class
│   │   │   │               │   ├── port
│   │   │   │               │   │   ├── CardSearchQuery.class
│   │   │   │               │   │   ├── SearchIdentity$Identity.class
│   │   │   │               │   │   └── SearchIdentity.class
│   │   │   │               │   └── usecase
│   │   │   │               │       ├── CreateCardUseCase.class
│   │   │   │               │       ├── CreateLocationUseCase.class
│   │   │   │               │       └── SearchCardsUseCase.class
│   │   │   │               ├── domain
│   │   │   │               │   ├── model
│   │   │   │               │   │   ├── Card.class
│   │   │   │               │   │   ├── CardRarity.class
│   │   │   │               │   │   ├── CardSearchCriteria.class
│   │   │   │               │   │   ├── CardSearchException$Reason.class
│   │   │   │               │   │   ├── CardSearchException.class
│   │   │   │               │   │   ├── CardType.class
│   │   │   │               │   │   └── Location.class
│   │   │   │               │   └── repository
│   │   │   │               │       ├── CardRepository.class
│   │   │   │               │       └── LocationRepository.class
│   │   │   │               ├── infrastructure
│   │   │   │               │   ├── config
│   │   │   │               │   │   ├── BeanConfiguration.class
│   │   │   │               │   │   └── CardSearchConfiguration.class
│   │   │   │               │   ├── persistence
│   │   │   │               │   │   ├── CardRepositoryImpl.class
│   │   │   │               │   │   ├── JdbcCardSearchQuery$CursorPosition.class
│   │   │   │               │   │   ├── JdbcCardSearchQuery$Snapshot.class
│   │   │   │               │   │   ├── JdbcCardSearchQuery.class
│   │   │   │               │   │   ├── LocationRepositoryImpl.class
│   │   │   │               │   │   └── SearchSchemaMigration.class
│   │   │   │               │   ├── storage
│   │   │   │               │   │   └── ImageService.class
│   │   │   │               │   └── web
│   │   │   │               │       ├── CardController.class
│   │   │   │               │       ├── CardSearchController.class
│   │   │   │               │       ├── CardSearchErrorHandler$SearchError.class
│   │   │   │               │       ├── CardSearchErrorHandler.class
│   │   │   │               │       └── LocationController.class
│   │   │   │               └── PokearenaApplication.class
│   │   │   ├── db
│   │   │   │   └── migration
│   │   │   │       └── V039__card_search.sql
│   │   │   ├── application.properties
│   │   │   └── schema.sql
│   │   ├── generated-sources
│   │   │   └── annotations
│   │   ├── generated-test-sources
│   │   │   └── test-annotations
│   │   ├── maven-archiver
│   │   │   └── pom.properties
│   │   ├── maven-status
│   │   │   └── maven-compiler-plugin
│   │   │       ├── compile
│   │   │       │   └── default-compile
│   │   │       │       ├── createdFiles.lst
│   │   │       │       └── inputFiles.lst
│   │   │       └── testCompile
│   │   │           └── default-testCompile
│   │   │               ├── createdFiles.lst
│   │   │               └── inputFiles.lst
│   │   ├── surefire-reports
│   │   │   ├── cr.ac.tec.pokearena.CardSearchArchitectureTests.txt
│   │   │   ├── cr.ac.tec.pokearena.CardSearchPostgresTests.txt
│   │   │   ├── cr.ac.tec.pokearena.CardSearchValidationTests.txt
│   │   │   ├── cr.ac.tec.pokearena.DisposableDatabaseSafetyTests.txt
│   │   │   ├── cr.ac.tec.pokearena.PokearenaApplicationTests.txt
│   │   │   ├── TEST-cr.ac.tec.pokearena.CardSearchArchitectureTests.xml
│   │   │   ├── TEST-cr.ac.tec.pokearena.CardSearchPostgresTests.xml
│   │   │   ├── TEST-cr.ac.tec.pokearena.CardSearchValidationTests.xml
│   │   │   ├── TEST-cr.ac.tec.pokearena.DisposableDatabaseSafetyTests.xml
│   │   │   └── TEST-cr.ac.tec.pokearena.PokearenaApplicationTests.xml
│   │   ├── test-classes
│   │   │   └── cr
│   │   │       └── ac
│   │   │           └── tec
│   │   │               └── pokearena
│   │   │                   ├── CardSearchArchitectureTests.class
│   │   │                   ├── CardSearchPostgresTests.class
│   │   │                   ├── CardSearchValidationTests.class
│   │   │                   ├── DisposableDatabaseInitializer$1.class
│   │   │                   ├── DisposableDatabaseInitializer$GuardedDataSource.class
│   │   │                   ├── DisposableDatabaseInitializer.class
│   │   │                   ├── DisposableDatabaseSafetyTests.class
│   │   │                   └── PokearenaApplicationTests.class
│   │   ├── pokearena-0.0.1-SNAPSHOT.jar
│   │   └── pokearena-0.0.1-SNAPSHOT.jar.original
│   ├── .gitattributes
│   ├── .gitignore
│   ├── mvnw
│   ├── mvnw.cmd
│   └── pom.xml
├── ESTRUCTURA.md
└── README.md
```
