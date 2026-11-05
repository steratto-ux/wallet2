# Finanze di casa (Android, Kotlin + Compose + Room)

Apri la cartella in Android Studio (Ladybug o successivo): la sincronizzazione Gradle scarica tutto.

Contenuto della versione 0.1
- `data/`      schema Room: account, categorie (con sotto-categorie), transazioni, abbonamenti, prestiti, estratti carta
- `domain/`    `CreditCardCycle` (periodi carta) e `DueItemsProcessor` (addebiti carta, abbonamenti, rate prestiti)
- `importer/`  parser CSV Cashew (formato modello e formato completo) e salvataggio nel database
- `app/src/test/` test unitari su periodi carta e importatore (JUnit, girano senza dispositivo)
- `ui/`       schermata principale: account con saldo, spese/entrate del mese, patrimonio netto, ultime transazioni
  (il saldo di un account è la somma delle sue transazioni, come in Cashew; i trasferimenti non contano come spese/entrate)

Nota: il codice non è stato compilato in questo ambiente. Alla prima sincronizzazione potrebbe servire qualche piccolo ritocco.

- Nuova transazione: tasto + nella schermata principale (uscita, entrata, trasferimento; titolo, categoria, account, data e ora).
  Le transazioni con data/ora future vengono conteggiate solo quando arriva quel momento.

- Account: creazione e modifica (tocca un account nella home) con valore iniziale che entra nel saldo e nel patrimonio netto;
  per le carte: inizio periodo, giorno di addebito e conto collegato (attiva l'addebito automatico).
- Categorie (con sotto-categorie), abbonamenti (mensili/annuali) e prestiti (rata, tasso, rate rimanenti, debito residuo stimato).
- Compilazione online: vedi GITHUB.md.
