package com.example.finanzecasa.domain

/**
 * Scelta dell'icona di una categoria. Le chiavi corrispondono al catalogo icone dell'interfaccia.
 * Ordine: icona scelta dall'utente > parola chiave nel nome > icona della categoria principale > generica.
 */
object IconRules {
    const val DEFAULT = "category"

    // L'ordine conta: la prima regola che corrisponde vince. Si confronta l'inizio di ogni parola del nome.
    private val rules: List<Pair<String, List<String>>> = listOf(
        "grocery" to listOf("super", "spesa", "aliment", "grocer", "market"),
        "coffee" to listOf("caff", "aperitiv", "colazion"),
        "restaurant" to listOf("ristor", "cibo", "pranz", "cena", "pizz", "dining", "food", "bevand"),
        "home" to listOf("casa", "affitt", "mutuo", "condomin", "arredo", "famigl", "home"),
        "gas" to listOf("benzin", "carburant", "diesel", "gasolio", "rifornim"),
        "bolt" to listOf("luce", "elettric", "energia", "bolletta", "utenz", "gas"),
        "water" to listOf("acqua", "idric"),
        "shield" to listOf("assicuraz", "polizz"),
        "car" to listOf("auto", "macchin", "veicol", "parcheg", "pedagg"),
        "bus" to listOf("trasport", "autobus", "treno", "metro", "mezzi", "taxi", "biglietti"),
        "medical" to listOf("salute", "medic", "farmac", "dentist", "visit", "sanit", "ospedal"),
        "fitness" to listOf("sport", "palestra", "fitness"),
        "school" to listOf("scuol", "istruz", "libr", "corsi", "corso", "universit", "formazion"),
        "childcare" to listOf("figli", "bambin", "bimb", "asilo", "baby"),
        "pets" to listOf("animal", "veterin", "cane", "gatto", "pets"),
        "movie" to listOf("cinema", "film", "svago", "intratten", "libero", "teatro", "hobby"),
        "music" to listOf("music", "concert"),
        "subscriptions" to listOf("abbonam", "netflix", "spotify", "streaming"),
        "phone" to listOf("telefon", "cellulare", "internet", "wifi", "fibra", "mobile"),
        "clothes" to listOf("vestit", "abbigl", "scarp", "moda"),
        "beauty" to listOf("parrucc", "barbier", "bellezza", "estetic", "cosmet", "benessere"),
        "beach" to listOf("viagg", "vacanz", "hotel", "turism", "ferie"),
        "gift" to listOf("regal", "donazion", "benefic"),
        "work" to listOf("stipend", "lavoro", "salario"),
        "savings" to listOf("risparm", "accantonam"),
        "invest" to listOf("invest", "titoli", "azioni", "etf", "cripto"),
        "receipt" to listOf("tasse", "imposte", "tributi", "bollo", "irpef", "fiscal"),
        "bank" to listOf("banca", "bancari", "commission", "interessi", "conto"),
        "shopping" to listOf("shopping", "acquisti", "negozi"),
        "computer" to listOf("elettronic", "informat", "computer", "tecnolog")
    )

    /** Icona suggerita dal solo nome, oppure null se nessuna parola chiave corrisponde. */
    fun guess(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val tokens = name.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        for ((key, words) in rules) {
            if (tokens.any { token -> words.any { word -> token.startsWith(word) } }) return key
        }
        return null
    }

    fun resolve(iconKey: String?, name: String?, parentIconKey: String?, parentName: String?): String =
        iconKey ?: guess(name) ?: parentIconKey ?: guess(parentName) ?: DEFAULT
}
