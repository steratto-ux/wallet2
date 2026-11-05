# Compilare l'app con GitHub

1. Crea un account su https://github.com e poi un nuovo repository (pulsante "New"), per esempio `finanze-casa`.
   Può essere privato: le Actions funzionano anche sui repository privati.
2. Decomprimi `FinanzeCasa.zip` sul computer. Nel repository appena creato scegli "uploading an existing file"
   e trascina TUTTO il contenuto della cartella `FinanzeCasa` (comprese le cartelle nascoste `.github`
   e il file `.gitignore`). Poi premi "Commit changes".
   Controlla che nella pagina principale del repository ci siano `app/`, `build.gradle.kts`, `settings.gradle.kts`
   e `.github/workflows/build.yml`.
3. Apri la scheda **Actions**. La compilazione "Build APK" parte da sola a ogni caricamento
   (oppure: Build APK -> Run workflow).
4. Al termine (3-6 minuti) apri l'esecuzione: se è verde, in fondo alla pagina, sezione **Artifacts**,
   scarica `FinanzeCasa-debug-apk` (è uno zip che contiene `app-debug.apk`).
5. Copia `app-debug.apk` sul telefono e aprilo; Android chiederà di consentire l'installazione da questa fonte.
6. Se l'esecuzione è rossa: apri il passaggio fallito, copia le righe che iniziano con `e:` (errori Kotlin)
   e incollale in chat, così correggo il codice.

Per aggiornare l'app: sostituisci i file nel repository (Add file -> Upload files) e rifai dal punto 3.
L'APK di debug si aggiorna sopra quello già installato senza perdere i dati.
