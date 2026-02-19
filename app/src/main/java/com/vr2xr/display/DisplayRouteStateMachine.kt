package com.vr2xr.display

sealed interface DisplayRouteState {
    data object NoOutput : DisplayRouteState
    data class ExternalPending(val displayId: Int) : DisplayRouteState
    data class ExternalActive(val displayId: Int) : DisplayRouteState
}

enum class ActiveRoute {
    NONE,
    EXTERNAL
}

enum class RouteTarget {
    NONE,
    HOLD_CURRENT,
    EXTERNAL
}

data class DisplayRouteSnapshot(
    val externalDisplayId: Int?,
    val externalSurfaceReady: Boolean,
    val activeRoute: ActiveRoute,
    val activeSurfaceBound: Boolean
)

data class DisplayRouteDecision(
    val state: DisplayRouteState,
    val target: RouteTarget
)

class DisplayRouteStateMachine {
    fun decide(snapshot: DisplayRouteSnapshot): DisplayRouteDecision {
        val externalDisplayId = snapshot.externalDisplayId
        if (externalDisplayId != null) {
            if (snapshot.externalSurfaceReady) {
                return DisplayRouteDecision(
                    state = DisplayRouteState.ExternalActive(externalDisplayId),
                    target = RouteTarget.EXTERNAL
                )
            }
            val holdCurrent = snapshot.activeRoute == ActiveRoute.EXTERNAL && snapshot.activeSurfaceBound
            return DisplayRouteDecision(
                state = DisplayRouteState.ExternalPending(externalDisplayId),
                target = if (holdCurrent) RouteTarget.HOLD_CURRENT else RouteTarget.NONE
            )
        }
        return DisplayRouteDecision(
            state = DisplayRouteState.NoOutput,
            target = RouteTarget.NONE
        )
    }
}

fun DisplayRouteState.asDebugLabel(): String {
    return when (this) {
        DisplayRouteState.NoOutput -> "no-output"
        is DisplayRouteState.ExternalPending -> "external-pending(display=${displayId})"
        is DisplayRouteState.ExternalActive -> "external-active(display=${displayId})"
    }
}
