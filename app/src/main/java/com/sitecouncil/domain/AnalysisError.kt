package com.sitecouncil.domain

/** Fehler, die der Nutzer als verständliche Meldung zu sehen bekommt. */
sealed class AnalysisError(val detail: String = "") : Exception(detail) {
    class NoApiKey : AnalysisError()
    class InvalidUrl : AnalysisError()
    class PageUnreachable(detail: String) : AnalysisError(detail)
    class Unauthorized : AnalysisError()
    class RateLimit : AnalysisError()
    class Api(detail: String) : AnalysisError(detail)
    class Network(detail: String) : AnalysisError(detail)
    class InvalidJson(detail: String) : AnalysisError(detail)
    class Timeout : AnalysisError()
    class AllExpertsFailed(detail: String) : AnalysisError(detail)
    class Unknown(detail: String) : AnalysisError(detail)

    /** Bei diesen Fehlern lohnt kein weiterer Aufruf, die Analyse bricht ab. */
    fun isFatal(): Boolean = this is NoApiKey || this is Unauthorized
}
