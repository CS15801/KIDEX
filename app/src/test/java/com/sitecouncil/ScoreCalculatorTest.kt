package com.sitecouncil

import com.sitecouncil.domain.ActionItem
import com.sitecouncil.domain.CostEstimator
import com.sitecouncil.domain.ExpertVote
import com.sitecouncil.domain.Finding
import com.sitecouncil.domain.Price
import com.sitecouncil.domain.ScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreCalculatorTest {

    @Test
    fun equalWeightsGiveArithmeticMean() {
        val scores = mapOf("seo" to 80, "geo" to 60, "texting" to 70)
        assertEquals(70, ScoreCalculator.total(scores, emptyMap()))
    }

    @Test
    fun customWeightsShiftTheResult() {
        val scores = mapOf("seo" to 100, "geo" to 0)
        val weights = mapOf("seo" to 3.0, "geo" to 1.0)
        assertEquals(75, ScoreCalculator.total(scores, weights))
    }

    @Test
    fun zeroWeightRemovesCategory() {
        val scores = mapOf("seo" to 100, "geo" to 0)
        assertEquals(100, ScoreCalculator.total(scores, mapOf("geo" to 0.0)))
    }

    @Test
    fun emptyScoresGiveZero() {
        assertEquals(0, ScoreCalculator.total(emptyMap(), emptyMap()))
    }

    @Test
    fun scoresAreClampedToValidRange() {
        assertEquals(100, ScoreCalculator.total(mapOf("seo" to 140), emptyMap()))
        assertEquals(0, ScoreCalculator.total(mapOf("seo" to -20), emptyMap()))
    }

    @Test
    fun prioritizePutsHighImpactLowEffortFirst() {
        val actions = listOf(
            ActionItem(title = "teuer", impact = 5, effort = 5),
            ActionItem(title = "schnell", impact = 4, effort = 1),
            ActionItem(title = "klein", impact = 2, effort = 1),
            ActionItem(title = "gross", impact = 5, effort = 2),
        )
        val sorted = ScoreCalculator.prioritize(actions).map { it.title }
        assertEquals(listOf("gross", "schnell", "klein", "teuer"), sorted)
    }

    @Test
    fun fallbackActionsComeFromFindings() {
        val votes = listOf(
            ExpertVote(
                key = "seo", expert = "SEO-Experte", score = 50,
                findings = listOf(Finding(problem = "Title fehlt", fix = "Title setzen", impact = 9, effort = 0)),
            )
        )
        val actions = ScoreCalculator.actionsFromFindings(votes)
        assertEquals(1, actions.size)
        assertEquals("Title fehlt", actions[0].title)
        assertEquals(5, actions[0].impact)
        assertEquals(1, actions[0].effort)
    }

    @Test
    fun costEstimateGrowsWithPersonaCount() {
        val price = Price(input = 2.0, output = 10.0)
        val small = CostEstimator.estimate(3, price)
        val large = CostEstimator.estimate(12, price)
        assertTrue(large.costUsd > small.costUsd)
        assertEquals(6 + 3 + 1 + 2, small.calls)
        assertEquals(2.0 + 10.0, price.cost(1_000_000, 1_000_000), 0.0001)
    }
}
