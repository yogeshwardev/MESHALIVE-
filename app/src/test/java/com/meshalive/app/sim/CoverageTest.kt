package com.meshalive.app.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageTest {

    @Test
    fun denseAreaIsFullyCovered() {
        // 2,000 phones in 0.25 km² with 40 m range: one giant cluster containing the gateways.
        val r = Coverage.plan(PlanInput(areaKm2 = 0.25, appUsers = 2000, gateways = 2, carriers = 0))
        assertTrue("mesh ${r.meshPct}", r.meshPct > 95.0)
    }

    @Test
    fun sparseAreaNeedsCarriers() {
        // 300 phones over 25 km²: isolated clusters; carriers must add coverage.
        val r = Coverage.plan(PlanInput(areaKm2 = 25.0, appUsers = 300, gateways = 2, carriers = 6))
        assertTrue("mesh ${r.meshPct}", r.meshPct < 20.0)
        assertTrue("carriers ${r.withCarriersPct} vs mesh ${r.meshPct}", r.withCarriersPct > r.meshPct)
    }

    @Test
    fun moreUsersNeverReduceCoverageOfSameSeed() {
        val few = Coverage.plan(PlanInput(appUsers = 300, carriers = 0))
        val many = Coverage.plan(PlanInput(appUsers = 3000, carriers = 0))
        assertTrue(many.meshPct >= few.meshPct)
    }

    @Test
    fun noGatewaysAndNoCarriersMeansNoCoverage() {
        val r = Coverage.plan(PlanInput(gateways = 0, carriers = 0))
        assertEquals(0.0, r.meshPct, 0.0)
        assertEquals(0.0, r.withCarriersPct, 0.0)
    }

    @Test
    fun peopleScaleWithHouseholdSize() {
        val r = Coverage.plan(PlanInput(appUsers = 500, householdSize = 4.0))
        assertEquals(2000, r.peopleTotal)
        assertTrue(r.peopleCovered <= r.peopleTotal)
    }

    @Test
    fun sameSeedIsReproducible() {
        assertEquals(Coverage.plan(PlanInput()), Coverage.plan(PlanInput()))
    }
}
