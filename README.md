# CatERing

Backend Java per la gestione di un servizio di catering, sviluppato per il laboratorio di **Sviluppo delle Applicazioni Software** (a.a. 2025/2026, Università di Torino).

Il progetto implementa il caso d'uso **Gestire gli eventi**, insieme a *Gestire menù* e *Gestire compiti cucina*. Non c'è interfaccia grafica: il sistema si usa dai test JUnit e dal `main`.

## Gestire gli eventi

L'Organizzatore può:

- creare eventi, anche **ricorrenti** (frequenza, data fine o numero di ripetizioni)
- definire i **servizi** di un evento (tipo, data, orari, luogo)
- assegnare lo **chef** all'evento e il **cuoco** ai servizi
- assegnare o prenotare il **personale** e gestirne i ruoli
- **approvare i menù** (evento e servizio passano a "In corso") e proporre modifiche
- **chiudere** l'evento con note e documentazione finale
- modificare, eliminare o annullare eventi, servizi e istanze ricorrenti

La logica è in `EventManager`; le operazioni corrispondono agli SSD e ai contratti dell'allegato tecnico.

## Requisiti

- JDK 11+
- Maven

## Avvio

```bash
mvn compile        # compila
mvn test           # esegue i test
mvn exec:java      # avvia CatERing.main
```

Il database SQLite è in `database/catering.db` e viene rigenerato da `database/catering_init_sqlite.sql` solo se manca.

## Architettura

- `businesslogic/` – entità di dominio e controller dei casi d'uso (`EventManager`, `MenuManager`, `KitchenTaskManager`, `RecipeManager`, `ShiftManager`, `UserManager`). `CatERing` è il Singleton che li crea e li collega.
- `persistence/` – accesso al database con JDBC. `EventPersistence`, `MenuPersistence` e `KitchenTaskPersistence` sono observer: ricevono le notifiche dei manager e salvano le modifiche.

Se un'operazione viola le pre-condizioni, i manager lanciano `UseCaseLogicException`.

```
src/main/java/catering/
  businesslogic/   CatERing, event, menu, kitchen, recipe, shift, user
  persistence/     PersistenceManager + observer
  util/            LogManager
src/test/java/     test JUnit per caso d'uso
database/          schema SQL e database SQLite
```

## Test

I test JUnit 5 sono in `src/test/java/catering/` e coprono eventi, menù e compiti cucina.
