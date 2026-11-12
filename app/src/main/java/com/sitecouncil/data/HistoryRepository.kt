package com.sitecouncil.data

import com.sitecouncil.domain.AnalysisResult
import com.sitecouncil.domain.JsonExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val dao: AnalysisDao,
) {
    fun observe(): Flow<List<HistoryRow>> = dao.observeHistory()

    suspend fun save(result: AnalysisResult): Long {
        val json = JsonExtractor.json.encodeToString(AnalysisResult.serializer(), result)
        return dao.insert(
            AnalysisEntity(
                url = result.url,
                createdAt = result.createdAt,
                totalScore = result.totalScore,
                screenshotPath = result.screenshotPath,
                resultJson = json,
            )
        )
    }

    suspend fun load(id: Long): AnalysisResult? {
        val entity = dao.getById(id) ?: return null
        return try {
            JsonExtractor.json.decodeFromString(AnalysisResult.serializer(), entity.resultJson)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun delete(id: Long) {
        val entity = dao.getById(id)
        dao.deleteById(id)
        val path = entity?.screenshotPath
        if (!path.isNullOrBlank()) {
            withContext(Dispatchers.IO) { File(path).delete() }
        }
    }
}
