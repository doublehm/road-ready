package com.roadready.data

object FaultCategories {
    data class FaultItem(val code: String, val label: String)
    data class FaultCategory(val id: String, val title: String, val items: List<FaultItem>)

    val categories = listOf(
        FaultCategory("A", "Observation", listOf(
            FaultItem("A1", "Shoulder Check"),
            FaultItem("A2", "Scan"),
            FaultItem("A3", "Mirror Check"),
            FaultItem("A4", "Blind Spot"),
            FaultItem("A5", "360 Awareness"),
            FaultItem("A6", "Signage Recognition"),
            FaultItem("A7", "Pedestrian Awareness"),
            FaultItem("A8", "Cyclist Awareness"),
        )),
        FaultCategory("B", "Space Margins", listOf(
            FaultItem("B1", "Following Distance"),
            FaultItem("B2", "Lane Position"),
            FaultItem("B3", "Lateral Space"),
            FaultItem("B4", "Stopping Distance"),
            FaultItem("B5", "Intersection Gap"),
            FaultItem("B6", "Parking Distance"),
            FaultItem("B7", "Curb Distance"),
            FaultItem("B8", "Merge Gap"),
            FaultItem("B9", "Passing Distance"),
            FaultItem("B10", "Oncoming Clearance"),
            FaultItem("B11", "Crosswalk Space"),
            FaultItem("B12", "Railway Clearance"),
            FaultItem("B13", "Emergency Vehicle Space"),
            FaultItem("B14", "Construction Zone Space"),
        )),
        FaultCategory("C", "Speed", listOf(
            FaultItem("C1", "Over Speed Limit"),
            FaultItem("C2", "Under Speed (Impeding)"),
            FaultItem("C3", "Speed for Conditions"),
            FaultItem("C4", "Approach Speed"),
            FaultItem("C5", "Curve Speed"),
            FaultItem("C6", "School Zone Speed"),
            FaultItem("C7", "Playground Zone Speed"),
            FaultItem("C8", "Construction Zone Speed"),
            FaultItem("C9", "Residential Speed"),
        )),
        FaultCategory("D", "Steering", listOf(
            FaultItem("D1", "Over-Steering"),
            FaultItem("D2", "Under-Steering"),
            FaultItem("D3", "Wandering"),
            FaultItem("D4", "Hand Position"),
        )),
        FaultCategory("E", "Communication", listOf(
            FaultItem("E1", "Signal Timing"),
            FaultItem("E2", "Signal Use"),
            FaultItem("E3", "Horn Use"),
            FaultItem("E4", "Eye Contact"),
        )),
        FaultCategory("F", "Device Detected", listOf(
            FaultItem("F1", "Harsh Braking"),
            FaultItem("F2", "Speeding"),
            FaultItem("F3", "Sharp Turn"),
            FaultItem("F4", "Rapid Acceleration"),
            FaultItem("F5", "Hard Cornering"),
            FaultItem("F6", "Excessive Jerk"),
            FaultItem("F7", "Unstable Grip"),
        )),
    )

    val allCriteria = categories.flatMap { cat ->
        cat.items.map { item -> item to cat }
    }

    val deviceEventToCode = mapOf(
        "harsh_braking" to "F1",
        "speeding" to "F2",
        "sharp_turn" to "F3",
        "rapid_acceleration" to "F4",
        "hard_cornering" to "F5",
        "excessive_jerk" to "F6",
        "unstable_grip" to "F7",
    )
}
