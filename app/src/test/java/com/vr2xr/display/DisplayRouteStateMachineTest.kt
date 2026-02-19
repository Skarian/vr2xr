package com.vr2xr.display

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayRouteStateMachineTest {
    private val machine = DisplayRouteStateMachine()

    @Test
    fun externalActiveRoutesToExternalSurface() {
        val decision = machine.decide(
            DisplayRouteSnapshot(
                externalDisplayId = 7,
                externalSurfaceReady = true,
                activeRoute = ActiveRoute.NONE,
                activeSurfaceBound = true
            )
        )

        assertEquals(DisplayRouteState.ExternalActive(7), decision.state)
        assertEquals(RouteTarget.EXTERNAL, decision.target)
    }

    @Test
    fun externalPendingHoldsExistingExternalRoute() {
        val decision = machine.decide(
            DisplayRouteSnapshot(
                externalDisplayId = 7,
                externalSurfaceReady = false,
                activeRoute = ActiveRoute.EXTERNAL,
                activeSurfaceBound = true
            )
        )

        assertEquals(DisplayRouteState.ExternalPending(7), decision.state)
        assertEquals(RouteTarget.HOLD_CURRENT, decision.target)
    }

    @Test
    fun externalPendingClearsOutputInsteadOfSwitching() {
        val decision = machine.decide(
            DisplayRouteSnapshot(
                externalDisplayId = 7,
                externalSurfaceReady = false,
                activeRoute = ActiveRoute.NONE,
                activeSurfaceBound = true
            )
        )

        assertEquals(DisplayRouteState.ExternalPending(7), decision.state)
        assertEquals(RouteTarget.NONE, decision.target)
    }

    @Test
    fun noOutputWhenExternalDisplayIsGone() {
        val decision = machine.decide(
            DisplayRouteSnapshot(
                externalDisplayId = null,
                externalSurfaceReady = false,
                activeRoute = ActiveRoute.NONE,
                activeSurfaceBound = false
            )
        )

        assertEquals(DisplayRouteState.NoOutput, decision.state)
        assertEquals(RouteTarget.NONE, decision.target)
    }

    @Test
    fun noOutputWhenNoSurfaceAndNoExternalDisplay() {
        val decision = machine.decide(
            DisplayRouteSnapshot(
                externalDisplayId = null,
                externalSurfaceReady = false,
                activeRoute = ActiveRoute.NONE,
                activeSurfaceBound = false
            )
        )

        assertEquals(DisplayRouteState.NoOutput, decision.state)
        assertEquals(RouteTarget.NONE, decision.target)
    }
}
