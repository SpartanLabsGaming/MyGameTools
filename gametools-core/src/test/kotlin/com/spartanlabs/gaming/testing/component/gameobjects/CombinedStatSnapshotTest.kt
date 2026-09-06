package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.CombinedStat
import com.spartanlabs.gaming.gameobjects.CombinedStatSnapshot
//endregion

//region 2. Intended Function
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
//endregion

/** Covers [CombinedStatSnapshot] copying a [CombinedStat]'s numbers and its JSON round trip. */
class CombinedStatSnapshotTest {

    @Test
    fun `from copies the current amount and the max`() {
        val snapshot = CombinedStatSnapshot from CombinedStat(startingValue = 30.0, maxValue = 100.0)

        assertEquals(30.0, snapshot.value)
        assertEquals(100.0, snapshot.maxValue)
    }

    @Test
    fun `the snapshot survives a JSON round trip unchanged`() {
        val snapshot = CombinedStatSnapshot from CombinedStat(startingValue = 5.0, maxValue = 10.0)

        assertEquals(snapshot, Json.decodeFromString<CombinedStatSnapshot>(Json.encodeToString(snapshot)))
    }
}
