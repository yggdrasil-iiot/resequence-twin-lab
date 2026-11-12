package com.resequencetwin.control.hermod

/**
 * Lifecycle of a recipe-setpoint drift finding as governed by hermod (pillar ②) and observed by the
 * twin (pillar ③) over the Sparkplug B governance surface.
 *
 * Mapped from hermod's `Governance/LastEventType` wire vocabulary:
 * - `RECONCILING`   → [RECONCILING]          (hermod parked the run; awaiting human gate)
 * - `CONFIRMED`     → [CLEARED]              (governed write-back confirmed; drift cleared)
 * - `RECON_FAILED`  → [RECONCILING_FAILED]   (veto/compensation; drift persists)
 */
enum class ReconciliationState { RECONCILING, CLEARED, RECONCILING_FAILED }

/** A governance annotation for one logical setpoint key, with provenance (which hermod run). */
data class ReconciliationAnnotation(
    val state: ReconciliationState,
    val runId: String,
    val atMillis: Long,
)
