package com.example.finanzecasa.ui

/** Un blocco di testo della guida. */
sealed interface GuideBlock {
    data class Par(val text: String) : GuideBlock
    /** Elenco puntato; se una voce inizia con "Termine: spiegazione", il termine va in grassetto. */
    data class Bullets(val items: List<String>) : GuideBlock
    data class Example(val text: String) : GuideBlock
    data class Tip(val text: String) : GuideBlock
}

data class GuideSection(val title: String, val blocks: List<GuideBlock>)

private fun par(t: String) = GuideBlock.Par(t)
private fun bullets(vararg t: String) = GuideBlock.Bullets(t.toList())
private fun example(t: String) = GuideBlock.Example(t)
private fun tip(t: String) = GuideBlock.Tip(t)

val guideSections: List<GuideSection> = listOf(
    GuideSection(
        "A cosa serve Il mio wallet",
        listOf(
            par(
                "Il mio wallet non serve solo a segnare quanto spendi: serve a farti capire dove vanno i soldi " +
                    "e a spingerti, con piccoli passi, a risparmiare di più. Funziona così: tu registri entrate, uscite " +
                    "e trasferimenti; l'app li ordina per categoria, ti avvisa quando stai per superare un limite " +
                    "e, nella parte ANALISI ECONOMICA, ti dice cosa succederà a fine mese, quanta parte delle tue spese " +
                    "è inevitabile e quanto stai davvero mettendo da parte."
            ),
            par(
                "Più i dati sono completi e più le analisi sono affidabili. Il consiglio è registrare ogni movimento " +
                    "entro la sera stessa: con l'autocompletamento del titolo bastano pochi secondi."
            ),
            tip("Tocca il titolo di una sezione per aprirla o chiuderla. Le sezioni seguono l'ordine in cui useresti l'app.")
        )
    ),

    GuideSection(
        "La schermata principale",
        listOf(
            par("Dall'alto verso il basso la schermata principale contiene:"),
            bullets(
                "Saluto: cambia con l'ora del giorno (Buongiorno, Buon pomeriggio, Buonasera) e usa il nome che scrivi in Configurazione. A destra c'è l'ingranaggio della Configurazione.",
                "CALENDARIO: il pulsante a tutta larghezza sotto il saluto apre la vista calendario (vedi la sezione dedicata più sotto).",
                "Pulsanti rapidi: tre righe di icone che aprono Categorie, Budget, Abbonamenti, Prestiti, Ricorrenti, Spese irregolari, Obiettivi, Report e Transazioni.",
                "ANALISI ECONOMICA: il pulsante con il bordo dorato apre le analisi (previsione, regola 50/30/20, spese fisse e variabili, simulatore, piccole spese).",
                "Account: i riquadri che scorrono in orizzontale. Ognuno ha colore e icona scelti da te, il nome e il saldo (in rosso se è negativo). Toccandolo lo modifichi; l'ultimo riquadro, Nuovo account, ne crea uno.",
                "Entrate del mese (a sinistra) e Spese del mese (a destra): il totale dal giorno 1 a oggi. I trasferimenti tra i tuoi account non contano, perché i soldi non escono dal tuo patrimonio.",
                "Budget: una scheda per ogni budget, affiancate e scorrevoli in orizzontale come gli account, con la barra che si riempie man mano che spendi. La percentuale è al centro, l'indicatore Oggi mostra a che punto del mese sei: se la barra è più avanti di Oggi stai spendendo più in fretta del previsto. Sotto vedi quanto puoi ancora spendere al giorno oppure di quanto hai sforato.",
                "Spese per categoria: la ciambella del mese. Ogni colore è una categoria principale, con importo e percentuale sul totale. Il titolo sta su una riga e sotto, al centro, c'è il mese con le frecce per andare indietro e avanti; toccando una categoria vedi le sue sotto-categorie.",
                "Patrimonio netto: la somma dei saldi di tutti gli account (carte di credito comprese, che di solito sono negative). Il grafico mostra l'andamento; con 1 mese, 3 mesi, 6 mesi o 1 anno scegli quanto indietro guardare. Se in Configurazione attivi Includi i debiti dei prestiti, il capitale ancora da restituire viene sottratto.",
                "Ultime transazioni: le ultime 10 registrate. Vedi tutte apre l'elenco completo con la ricerca.",
                "Pulsante +: in basso a destra, crea una nuova transazione."
            ),
            par("Come si legge una riga di transazione:"),
            bullets(
                "Icona: quella della categoria.",
                "Titolo a sinistra e data a destra, sulla stessa riga.",
                "Riga sotto: account, categoria, eventuali tag (#vacanza) e la parola scontrino se hai allegato la foto.",
                "Importo: rosso per le uscite, verde con il segno + per le entrate, grigio per i trasferimenti (che mostrano da quale account a quale).",
                "programmata: compare se la data è nel futuro."
            )
        )
    ),


    GuideSection(
        "Il calendario",
        listOf(
            par("Dal pulsante CALENDARIO della schermata principale vedi il mese come una griglia, da lunedì a domenica."),
            bullets(
                "Mesi: in alto i nomi del mese precedente, di quello mostrato (in grassetto) e del successivo. Scorri la griglia a destra e a sinistra, oppure tocca i nomi laterali. Oggi riporta al mese e al giorno corrente.",
                "Totali del mese: sotto i nomi trovi le uscite (freccia rossa in giù), le entrate (freccia verde in su) e il saldo del mese dopo il segno =. Comprendono anche le transazioni con data futura dello stesso mese; i trasferimenti non contano.",
                "Ogni giorno mostra dei puntini e un importo: rosso se ci sono uscite, verde se ci sono entrate, grigio per i trasferimenti, azzurro per le cose ancora da venire (transazioni con data futura, abbonamenti, rate e ricorrenti previste). L'importo è il saldo di quel giorno, entrate meno uscite; 0 € se non c'è nulla. I giorni con movimenti hanno un bordo, oggi è evidenziato.",
                "Tocca un giorno per selezionarlo: in basso compare l'elenco delle sue transazioni (toccane una per modificarla) e, sotto Previste, le scadenze programmate di quel giorno.",
                "Il + sul giorno selezionato apre una nuova transazione già con quella data: dopo il salvataggio torni al calendario."
            )
        )
    ),
    GuideSection(
        "Registrare una transazione",
        listOf(
            par("Tocca + e scegli il tipo: Uscita, Entrata o Trasferimento. L'ordine dei campi è pensato per essere veloce:"),
            bullets(
                "Importo: sempre il primo campo, con la tastiera già aperta.",
                "Titolo: mentre scrivi l'app propone i titoli già usati. Se scrivi gu e hai già inserito Gulliver, lo vedi subito tra i suggerimenti. Scegliendolo, categoria e account vengono compilati con quelli dell'ultima volta; puoi comunque cambiarli.",
                "Account: da dove escono o dove entrano i soldi.",
                "Categoria: puoi crearne una nuova al volo.",
                "Data e ora: di default adesso. Una data nel futuro crea una transazione programmata, che entra nel saldo solo quando arriva il giorno.",
                "Note, tag e foto dello scontrino: facoltativi. I tag (ad esempio #vacanza2026) servono per cercare spese di tipo diverso dalle categorie."
            ),
            par("Il Trasferimento sposta soldi da un account a un altro (ad esempio dal conto al libretto di risparmio). Non è una spesa e non modifica il patrimonio netto. Ha tre tipi: Normale, Risparmio e Accantonamento."),
            example(
                "Per segnare i soldi messi da parte, fai un Trasferimento dal conto corrente al conto Risparmi e scegli il tipo Risparmio. " +
                    "Solo i trasferimenti di questo tipo contano nel 20% della regola 50/30/20."
            ),
            example(
                "Per mettere da parte i soldi dell'assicurazione che pagherai a giugno, fai un Trasferimento verso un conto dedicato e scegli il tipo Accantonamento. " +
                    "Non conta come risparmio: è denaro che hai già deciso di spendere. Lo ritrovi in Spese irregolari."
            ),
            tip("Negli elenchi degli account, quando registri una transazione, compaiono solo gli account non nascosti."),
            tip("Per correggere una transazione sbagliata, toccala nell'elenco: puoi cambiare tutto o eliminarla.")
        )
    ),

    GuideSection(
        "Account, carte di credito e saldo",
        listOf(
            bullets(
                "Conto: il conto corrente o la carta prepagata.",
                "Contanti: il portafoglio.",
                "Carta: una carta di credito. Le spese fatte con la carta riducono il saldo della carta; poi, in una data fissa, il conto di addebito la salda con un trasferimento automatico.",
                "Valore iniziale: il saldo da cui parti quando crei l'account. Può essere negativo.",
                "Correggi saldo: se il saldo dell'app non coincide con quello reale, scrivi quello corretto. Non crea nessuna transazione, aggiusta solo il punto di partenza.",
                "Investimento: un conto (fondi, azioni, ETF) di cui aggiorni il valore a mano con Aggiorna valore. La differenza cambia il patrimonio ma non conta come entrata o spesa, così non falsa le analisi. I versamenti si registrano come trasferimenti verso il conto.",
                "Ordina e nascondi account: in Configurazione, per cambiare l'ordine o togliere dalla vista quelli che non usi più. Gli account nascosti non si possono scegliere quando registri una transazione."
            ),
            example(
                "Carta di credito con inizio periodo 12 e giorno di addebito 24: il 24 di ogni mese l'app crea un trasferimento dal conto alla carta " +
                    "con le spese del periodo già chiuso (dal 12 del mese prima all'11). Così il tuo conto scende nel giorno in cui la banca ti addebita davvero."
            )
        )
    ),

    GuideSection(
        "Categorie e sotto-categorie",
        listOf(
            par(
                "Le categorie sono la base di tutte le analisi. Una categoria principale (ad esempio Casa) può avere sotto-categorie " +
                    "(Affitto, Bollette, Manutenzione). Nei report e nei budget scegliere la principale comprende anche le sue sotto-categorie."
            ),
            bullets(
                "Icona: scelta automatica dal nome, oppure la scegli tu.",
                "Nella regola 50/30/20: per ogni categoria, principale o sotto-categoria, puoi decidere se è una Necessità o un Desiderio. Con Auto la decide l'app dal nome (affitto, bollette, spesa, trasporti, salute sono necessità); per una sotto-categoria Come principale significa che segue la categoria principale."
            ),
            example(
                "La categoria Casa è una necessità. La sotto-categoria Arredamento, però, è più un desiderio: aprila e scegli Desiderio. " +
                    "Le altre sotto-categorie di Casa restano necessità."
            )
        )
    ),

    GuideSection(
        "Budget",
        listOf(
            par(
                "Un budget è un limite mensile su una o più categorie. Scegli un nome, l'importo del mese e le categorie incluse. " +
                    "Nella schermata principale ogni budget mostra la barra, la percentuale e quanto puoi spendere al giorno per i giorni che restano."
            ),
            bullets(
                "Avvisi: se attivi le notifiche, l'app ti avvisa quando un budget arriva all'80% e al 100%.",
                "Storico: dalla scheda del budget vedi i mesi passati, la media dei mesi chiusi e quante volte hai superato il limite."
            ),
            tip("Un budget è utile quando è realistico: parti dalla media dei mesi scorsi (la trovi nello storico) e riducila di poco.")
        )
    ),

    GuideSection(
        "Abbonamenti, prestiti e ricorrenti",
        listOf(
            par("Sono i movimenti che si ripetono. L'app crea da sola la transazione alla scadenza, così non devi ricordartene."),
            bullets(
                "Abbonamenti: Netflix, palestra, telefono. Vedi il costo mensile e annuale di tutti quelli attivi e il costo annuo di ciascuno. Con il tasto Da rivedere segni quelli di cui non sei sicuro: in alto vedi quanto risparmieresti in un anno disdicendoli e, se le notifiche sono attive, ricevi un avviso nei 3 giorni prima del rinnovo.",
                "Prestiti: importo della rata, giorno di scadenza e numero di rate rimaste. Vedi il debito residuo stimato.",
                "Ricorrenti: qualsiasi entrata, uscita o trasferimento periodico, settimanale, mensile o annuale: stipendio, affitto, bollette, paghetta o spesa settimanale.",
                "Trasferimento ricorrente (facoltativo): serve a mettere da parte il risparmio o l'accantonamento in automatico, ad esempio il giorno dello stipendio. Scegli i due account e il tipo (Risparmio o Accantonamento): con Risparmio l'app ti ricorda il 20% delle tue entrate ricorrenti. Se non lo crei, niente cambia: puoi sempre fare i trasferimenti a mano."
            ),
            par("Registra ora (nelle ricorrenti): serve quando il movimento arriva in anticipo."),
            example(
                "Lo stipendio è previsto il 30, ma viene accreditato il 28. Il 28 apri Ricorrenti, tocca Registra ora sotto lo stipendio e conferma: " +
                    "la transazione viene creata con la data di oggi e la prossima scadenza passa al 30 del mese dopo."
            ),
            tip("Se elimini una ricorrente, un abbonamento o un prestito, l'app smette di includerlo anche nella previsione di fine mese. Le transazioni già registrate restano nello storico.")
        )
    ),

    GuideSection(
        "Spese irregolari e accantonamento",
        listOf(
            par(
                "Alcune spese non arrivano ogni mese: assicurazione, bollo, vacanze, regali, manutenzione dell'auto. Pesano molto nel mese in cui cadono. " +
                    "Spese irregolari serve a distribuirle su tutto l'anno."
            ),
            bullets(
                "Aggiungi una spesa con un nome, l'importo che prevedi (anche approssimativo) e la data prevista. Se si ripete ogni anno, attiva Si ripete ogni anno.",
                "Riepilogo: il totale previsto, quanto hai già accantonato, quanto manca e, in grande, quanto mettere da parte ogni mese per tutte le spese insieme.",
                "Ogni riga mostra la barra di copertura, quanto è già coperto, quanto manca e la quota mensile (quello che manca diviso per i mesi che restano).",
                "Accantonare: fai un Trasferimento con il tipo Accantonamento verso il conto dove tieni i soldi messi da parte, anche come trasferimento ricorrente. Il totale accantonato compare nel riepilogo.",
                "Pagare: tocca Segna come pagata. Scrivi l'importo reale, la data, l'account con cui paghi e, se c'è un accantonamento, quanto ne usi: tutto, solo una parte o niente. Se i soldi accantonati stanno su un altro account, l'app sposta da lì la parte usata, così i saldi restano giusti."
            ),
            example(
                "Assicurazione auto prevista a giugno, 600 €, e ne mancano 6 mesi: l'app propone di mettere da parte 100 € al mese. " +
                    "Se hai già accantonato 150 €, ne mancano 450 e la quota scende a 75 € al mese. " +
                    "A giugno la paghi 620 €: usi tutti i 600 € accantonati e i restanti 20 € escono dal conto."
            ),
            tip("Le spese sono coperte in ordine di data: l'accantonamento va prima a quelle che scadono prima. Quando le stime cambiano, modificale: il piano si aggiorna da solo.")
        )
    ),

    GuideSection(
        "Obiettivi e fondo di emergenza",
        listOf(
            par(
                "In cima a Obiettivi c'è il fondo di emergenza: una riserva per gli imprevisti, di solito pari a 3 o 6 mesi di spese. " +
                    "L'app calcola la tua spesa media mensile sui mesi già chiusi, la moltiplica per 3 o 6 e mostra a che punto sei. " +
                    "Scegli tu in quali account tieni la riserva: il loro saldo è quanto hai messo da parte."
            ),
            example(
                "Spendi in media 1.800 € al mese: il fondo da 3 mesi è di 5.400 €. Se nel conto Risparmi ne hai 2.700, sei al 50% e copri 1,5 mesi di spese."
            ),
            par("Sotto il fondo ci sono i tuoi obiettivi: una somma che vuoi raggiungere, come una vacanza o un'auto. Hai tre modi per far avanzare la barra:"),
            bullets(
                "A mano: scrivi tu quanto hai già messo da parte e lo aggiorni quando versi altri soldi.",
                "Account: l'avanzamento è il saldo di un account di risparmio.",
                "Categoria: l'avanzamento è il totale netto (entrate meno uscite) delle transazioni di una categoria, dalla creazione dell'obiettivo."
            ),
            tip("Collegare l'obiettivo all'account dove trasferisci i risparmi fa aggiornare la barra da sola, insieme al 20% della regola 50/30/20.")
        )
    ),

    GuideSection(
        "Cercare le transazioni",
        listOf(
            par("Dal pulsante Transazioni (o da Vedi tutte) puoi filtrare l'elenco con questi strumenti, usabili insieme:"),
            bullets(
                "Testo: cerca nel titolo, nelle note, nella categoria e nei tag.",
                "Tipo: Tutte, Uscite, Entrate oppure Trasferimenti.",
                "Categoria: scegliendo una categoria principale vedi anche tutte le sue sotto-categorie.",
                "Account.",
                "Date: dal… al…, con gli estremi compresi.",
                "Totale: in alto, a destra, la somma dell'elenco che stai guardando (entrate meno uscite). I trasferimenti non cambiano il patrimonio, quindi non contano, a meno che non cerchi solo i trasferimenti: allora vedi quanto hai spostato.",
                "Scadenze future: l'interruttore Mostra le scadenze future aggiunge in alto la sezione In arrivo."
            ),
            par(
                "In arrivo mostra solo la prossima scadenza di ogni ricorrente, abbonamento e rata, non quelle successive. " +
                    "Toccando una scadenza apri la sua scheda per modificarla. Le scadenze rispettano i filtri attivi."
            ),
            example(
                "Vuoi sapere quanto hai speso in ristoranti a ottobre: tipo Uscite, categoria Ristoranti, date dal 1 al 31 ottobre. " +
                    "Poi, per vedere cosa sta per essere addebitato, lascia attivo Mostra le scadenze future."
            )
        )
    ),

    GuideSection(
        "Report",
        listOf(
            par("I report confrontano il periodo scelto (un mese o un anno) con quello precedente."),
            bullets(
                "Entrate, Spese e Risparmio del periodo, con la variazione percentuale rispetto al periodo prima.",
                "Spese in crescita: le categorie in cui spendi più del periodo precedente.",
                "Spese per categoria: importo, quota sul totale e variazione di ogni categoria. Toccando una categoria vedi le sotto-categorie.",
                "Entrate per categoria."
            ),
            par(
                "Periodo in corso: il confronto è fatto sugli stessi giorni. Quando il mese non è finito, i giorni trascorsi vengono confrontati con gli stessi giorni del mese prima; " +
                    "a mese chiuso il confronto è con tutto il mese precedente."
            ),
            example(
                "Oggi è il 12 agosto: le spese dal 1 al 12 agosto sono confrontate con quelle dal 1 al 12 luglio, non con tutto luglio. " +
                    "Così non sembra di aver speso poco solo perché il mese è appena iniziato."
            )
        )
    ),

    GuideSection(
        "ANALISI ECONOMICA: come è organizzata",
        listOf(
            par(
                "L'analisi lavora sui dati che hai già inserito, negli ultimi mesi, e non ti chiede nulla in più. Più mesi registri, più diventa precisa. " +
                    "Contiene cinque strumenti, dall'alto verso il basso:"
            ),
            bullets(
                "Previsione di fine mese: quanto avrai sui conti l'ultimo giorno.",
                "Regola 50/30/20: quanto del tuo reddito va in necessità, desideri e risparmio.",
                "Spese fisse e variabili: quanto è inevitabile e quanto dipende da te.",
                "E se risparmiassi di più?: un simulatore con il tuo obiettivo di risparmio.",
                "Piccole spese che si ripetono: i caffè e gli acquisti piccoli che sommati pesano."
            ),
            tip("Un buon giro di controllo è una volta a settimana: guarda la previsione, poi la regola 50/30/20 del mese e, se qualcosa non va, il simulatore.")
        )
    ),

    GuideSection(
        "Analisi: previsione di fine mese",
        listOf(
            par("Risponde alla domanda: arrivo alla fine del mese in positivo? Parte dal saldo di oggi e aggiunge o toglie:"),
            bullets(
                "Saldo attuale: la somma di tutti i tuoi account.",
                "Ancora in arrivo: le scadenze di abbonamenti, rate e ricorrenti che cadono da oggi a fine mese, più le spese che tornano ogni mese (come il condominio) e non sono ancora comparse. Le voci stimate dallo storico hanno il tasto Non contare.",
                "Spese di tutti i giorni: la tua spesa media giornaliera moltiplicata per i giorni che mancano. All'inizio del mese pesa di più la media dei mesi scorsi, perché pochi giorni non bastano a capire come spendi."
            ),
            example(
                "Saldo 1.500 €. In arrivo: affitto −700 € (il 5), abbonamento −13 €, stipendio +1.800 € (il 27). Spendi in media 25 € al giorno e mancano 10 giorni: −250 €. " +
                    "Saldo previsto = 1.500 − 700 − 13 + 1.800 − 250 = 2.337 €."
            ),
            par("Attenzione a due cose:"),
            bullets(
                "Gli stipendi e le altre entrate contano solo se li hai inseriti tra i ricorrenti o con data futura. Altrimenti la previsione risulta più bassa del reale.",
                "Se una voce stimata dallo storico non esiste più (per esempio hai cancellato un abbonamento), tocca Non contare. Con Ripristina le voci escluse le fai tornare."
            )
        )
    ),

    GuideSection(
        "Analisi: la regola 50/30/20",
        listOf(
            par(
                "È un metodo semplice per dividere il reddito netto mensile (le entrate del mese) in tre parti:"
            ),
            bullets(
                "50% Necessità (blu): ciò che non puoi evitare, come affitto o mutuo, bollette, spesa alimentare, trasporti, cure mediche.",
                "30% Desideri (arancione): ciò che rende la vita più piacevole ma non è indispensabile, come ristoranti, shopping, hobby, viaggi, streaming.",
                "20% Risparmio (verde): i soldi che metti da parte, per il fondo emergenze, gli investimenti o per estinguere i debiti."
            ),
            par("Il punto di forza è che non devi tenere un budget per ogni categoria: ti basta controllare tre numeri."),
            par("Come si imposta (una volta sola):"),
            bullets(
                "1. Entrate: registra lo stipendio e le altre entrate, meglio come ricorrente così non lo dimentichi. Il 100% del grafico è il reddito del mese.",
                "2. Categorie: apri Categorie e controlla per ogni categoria (e sotto-categoria) se è Necessità o Desiderio. Con Auto decide l'app dal nome: se non sei d'accordo, scegli a mano.",
                "3. Risparmio: quando metti da parte dei soldi, fai un Trasferimento verso il conto di risparmio e spunta la casella Risparmio. Solo questi trasferimenti contano nel 20%."
            ),
            par("Come si legge il grafico:"),
            bullets(
                "Ciambella: la torta è il tuo reddito del mese. Blu = necessità, arancione = desideri, verde = risparmio, grigio = non assegnato, cioè soldi non spesi né risparmiati (restano sul conto). Al centro c'è il reddito.",
                "Tre barre: per ognuna vedi la percentuale raggiunta sul reddito, l'importo e, sotto, l'obiettivo (50%, 30%, 20%). La barra si riempie fino all'obiettivo. Per necessità e desideri vuol dire entro il limite: se la barra diventa rossa lo hai superato e viene indicato di quanto. Per il risparmio è il contrario: ti dice quanto manca per arrivare al 20%.",
                "Elenchi Necessità e Desideri: le prime categorie in cui hai speso, per capire dove intervenire.",
                "Ultimi mesi: una colonna per mese con i tre colori impilati. Le linee tratteggiate sono il 50% e l'80%: se il blu arriva alla prima linea e l'arancione alla seconda, quel mese rispetta la regola. Tocca una colonna per vedere quel mese.",
                "Frecce accanto al mese: per spostarti tra i mesi."
            ),
            example(
                "Entrate 2.000 €: gli obiettivi sono 1.000 € necessità, 600 € desideri, 400 € risparmio.\n" +
                    "Questo mese hai speso 1.200 € in necessità (affitto 700, spesa 300, bollette 120, trasporti 80), 375 € in desideri (ristoranti 150, streaming 25, shopping 200) e hai trasferito 300 € ai risparmi.\n" +
                    "Risultato: necessità 60% (oltre di 200 €), desideri 19% (entro il limite), risparmio 15% (mancano 100 €), non assegnato 6% (125 €)."
            ),
            par("Cosa farne:"),
            bullets(
                "Il non assegnato è il margine più facile: i 125 € avanzati sul conto, trasferiti ai risparmi con la casella Risparmio, portano il risparmio a 425 € (21%) e l'obiettivo è raggiunto.",
                "Se le necessità sono sempre sopra il 50%, la regola non è sbagliata: è un segnale. Controlla se puoi ridurre le voci grandi (bollette, assicurazioni, abbonamenti, rate) e, nel frattempo, adatta l'obiettivo, ad esempio 60/20/20, sapendo che il risparmio è la parte da difendere.",
                "Se i desideri sono rossi, guarda l'elenco: di solito pochi capitoli (ristoranti, shopping) spiegano quasi tutto. Il simulatore ti dice quanto tagliare."
            ),
            par("Limiti da tenere presenti:"),
            bullets(
                "Le percentuali sono sul reddito registrato nel mese: se lo stipendio non è ancora arrivato, il mese in corso sembra peggiore di quello che è.",
                "Un trasferimento senza la casella Risparmio non conta come risparmio, anche se il conto di destinazione è un libretto.",
                "Il risparmio mensile può essere inferiore al 20%: è normale, la barra serve a vedere di quanto manca.",
                "Una categoria è tutta una necessità oppure tutta un desiderio (salvo sotto-categorie con una scelta diversa). Se una categoria mescola le due cose, dividila in sotto-categorie."
            ),
            tip("Più che il singolo mese guarda la tendenza: tre mesi di fila con il risparmio al 15% valgono più di un mese al 25% seguito da uno allo 0%.")
        )
    ),

    GuideSection(
        "Analisi: spese fisse e variabili",
        listOf(
            par(
                "Le spese fisse sono quelle che tornano uguali ogni mese e su cui hai poco margine: abbonamenti, rate, ricorrenti e le spese che l'app riconosce da sola " +
                    "(compaiono ogni mese, almeno da tre mesi, con importo simile). Le variabili sono tutte le altre: spesa, ristoranti, abbigliamento, svago."
            ),
            bullets(
                "Barra: la parte verde è fissa, quella arancione variabile, con le percentuali sul totale delle spese del mese.",
                "Elenco Fisse: ogni voce con l'importo e, tra parentesi, quante volte è comparsa nel mese.",
                "Elenco Variabili: raggruppate per categoria. È qui che hai la maggiore possibilità di intervenire."
            ),
            example(
                "Spese del mese 1.800 €: 1.050 € fisse (58%) e 750 € variabili (42%). Se vuoi risparmiare 100 € in più al mese è più facile trovarli nei 750 € variabili, " +
                    "tagliando il 13%, che nei 1.050 € fissi, dove servirebbe cambiare contratto o fornitore."
            ),
            tip("Una volta all'anno guarda l'elenco delle fisse: un abbonamento dimenticato o una tariffa che si può rinegoziare valgono più di molti piccoli tagli.")
        )
    ),

    GuideSection(
        "Analisi: il simulatore Se risparmiassi di più",
        listOf(
            par("Parte dalla media degli ultimi tre mesi chiusi (o dal mese in corso se non ce ne sono) e ti fa vedere cosa cambierebbe riducendo le spese variabili."),
            bullets(
                "Entrate, Spese fisse, Spese variabili e Ora risparmi al mese: la fotografia di partenza.",
                "Obiettivo di risparmio al mese: scrivi quanto vorresti mettere da parte. Resta salvato.",
                "Un modo possibile: l'app calcola quanto devi recuperare e lo divide tra le categorie variabili in proporzione a quanto spendi, senza tagliare mai più della metà di una categoria. Se non basta, ti dice quanto manca.",
                "Prova tu: un cursore per ogni categoria (da 0 a 50%) e i tasti Tutte −10%, −20%, −30% e Azzera. In fondo vedi il risparmio mensile, la differenza rispetto a ora e il risparmio in un anno."
            ),
            example(
                "Entrate 2.000 €, fisse 1.000 €, variabili 800 €: oggi risparmi 200 €. Con l'obiettivo di 400 € devi recuperare 200 €: l'app suggerisce ad esempio Ristoranti −60 €, " +
                    "Shopping −80 €, Svago −60 €. Se invece usi i cursori e riduci tutte le variabili del 20% (−160 €), risparmi 360 € al mese, cioè 4.320 € in un anno."
            ),
            tip("Prima di tagliare tutto in modo uniforme, prova a concentrarti sulle due o tre categorie più alte: il risultato è più facile da mantenere.")
        )
    ),

    GuideSection(
        "Analisi: piccole spese che si ripetono",
        listOf(
            par(
                "Cerca le uscite con lo stesso titolo, di importo contenuto (in media fino a 30 €), che compaiono almeno quattro volte negli ultimi tre mesi. " +
                    "Una per una sembrano niente; sommate fanno una cifra."
            ),
            example("Caffè al bar: 20 volte in tre mesi a 1,50 € = circa 10 € al mese e 120 € all'anno. Colazione fuori: 30 volte a 4 € = 40 € al mese e 480 € all'anno."),
            par(
                "Per ogni voce vedi il numero di volte, la media, la stima al mese e all'anno e, in fondo, il totale annuo di tutte. Non devi eliminarle: serve a deciderlo con consapevolezza."
            )
        )
    ),

    GuideSection(
        "Il widget",
        listOf(
            par("Il widget è un solo pulsante, grande 2 x 1, da mettere sulla schermata del telefono: serve a inserire una transazione in un attimo."),
            bullets(
                "Aggiungerlo: tieni premuto sulla schermata del telefono, scegli Widget, cerca Il mio wallet e trascinalo dove vuoi.",
                "Usarlo: toccando + Nuova transazione si apre l'app direttamente sul modulo di inserimento, con la tastiera pronta per l'importo.",
                "Riservatezza: non mostra saldi né importi, quindi non c'è nulla da nascondere. Se è attivo il blocco dell'app, prima dell'inserimento viene chiesto lo sblocco."
            )
        )
    ),

    GuideSection(
        "Configurazione, sicurezza e backup",
        listOf(
            bullets(
                "Il tuo nome: appare nel saluto.",
                "Tema: automatico (segue il telefono), chiaro o scuro.",
                "Includi i debiti dei prestiti: toglie dal patrimonio netto il capitale ancora da restituire.",
                "Notifiche: avvisi per i budget all'80% e al 100% e il giorno prima di scadenze e addebiti della carta.",
                "Spese sopra la media: una notifica quando in una categoria hai già speso più di una volta e mezza la tua media mensile (almeno 30 € di media e 25 € di differenza), una sola volta al mese per categoria.",
                "Promemoria serale: una notifica all'ora che scegli, solo se quel giorno non hai inserito nessuna transazione. Se hai già registrato qualcosa, non arriva. Richiede le notifiche attive.",
                "Blocco dell'app: chiede lo sblocco del telefono all'apertura e nasconde le anteprime nelle app recenti.",
                "Backup completo: un file con tutto (account, transazioni, categorie, budget, abbonamenti, prestiti, ricorrenti, obiettivi, tag, scontrini). Il ripristino sostituisce i dati attuali.",
                "Backup automatico: una copia a settimana, anche in una cartella a tua scelta (ad esempio su Google Drive).",
                "CSV: importa ed esporta le transazioni. L'esportazione non contiene carte, abbonamenti, prestiti e budget: per quelli usa il backup completo."
            ),
            tip("Prima di aggiornare l'app o cambiare telefono, fai un backup completo e conservane una copia fuori dal telefono.")
        )
    ),

    GuideSection(
        "Un metodo per risparmiare davvero",
        listOf(
            par("Gli strumenti da soli non bastano: serve una piccola abitudine. Questa è una routine che richiede pochi minuti."),
            bullets(
                "Ogni giorno: registra le spese appena le fai (30 secondi).",
                "Ogni settimana: apri ANALISI ECONOMICA e guarda la previsione di fine mese. Se è bassa, riduci le spese variabili da subito, non a fine mese.",
                "Quando arriva lo stipendio: fai subito il trasferimento ai risparmi con la casella Risparmio, prima di spendere. Risparmiare per primo funziona meglio che risparmiare quello che avanza.",
                "Ogni mese: guarda la regola 50/30/20 e il mese chiuso nei Report. Scegli una sola categoria su cui migliorare.",
                "Ogni tre mesi: rivedi le spese fisse e le piccole spese ripetute, e aggiorna i budget."
            ),
            par("Piccoli miglioramenti costanti battono grandi propositi: portare il risparmio dal 5% al 10% del reddito, mantenuto per un anno, vale più di un mese al 30% seguito dall'abbandono.")
        )
    )
)
