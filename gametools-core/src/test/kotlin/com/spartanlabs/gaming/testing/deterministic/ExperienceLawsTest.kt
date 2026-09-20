package com.spartanlabs.gaming.testing.deterministic

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.combat.DefaultExperienceReceiver
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
//endregion

/**
 * Level 4a - deterministic logic. Pins [DefaultExperienceReceiver.nextLevelXPRequired]'s
 * `10.0 + level^2` law across a sweep of levels, independent of any leveling behaviour.
 */
class ExperienceLawsTest {

    private val expectedByLevel = mapOf(
        1.0 to 11.0,
        2.0 to 14.0,
        3.0 to 19.0,
        5.0 to 35.0,
        10.0 to 110.0,
    )

    @Test
    fun `nextLevelXPRequired follows 10 plus level squared at every level`() {
        val receiver = DefaultExperienceReceiver()
        for ((level, expected) in expectedByLevel) {
            receiver.level = level
            assertEquals(expected, receiver.nextLevelXPRequired, "level $level")
        }
    }
}
