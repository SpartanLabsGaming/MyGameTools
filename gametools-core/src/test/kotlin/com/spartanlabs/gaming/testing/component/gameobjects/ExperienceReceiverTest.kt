package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.DefaultExperienceReceiver
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
//endregion

/** Covers [DefaultExperienceReceiver]'s leveling behaviour. */
class ExperienceReceiverTest {

    @Test
    fun `a deposit below the threshold accrues without leveling up`() {
        val receiver = DefaultExperienceReceiver()

        receiver.receiveExperience(5.0)

        assertEquals(1.0, receiver.level)
        assertEquals(5.0, receiver.experience)
    }

    @Test
    fun `a deposit that exactly meets the threshold levels up once with no leftover`() {
        val receiver = DefaultExperienceReceiver()

        receiver.receiveExperience(receiver.nextLevelXPRequired)

        assertEquals(2.0, receiver.level)
        assertEquals(0.0, receiver.experience)
    }

    @Test
    fun `a deposit past the threshold levels up once and keeps the remainder`() {
        val receiver = DefaultExperienceReceiver()

        receiver.receiveExperience(receiver.nextLevelXPRequired + 4.0)

        assertEquals(2.0, receiver.level)
        assertEquals(4.0, receiver.experience)
    }

    @Test
    fun `several small deposits that cumulatively cross the threshold still level up`() {
        val receiver = DefaultExperienceReceiver()
        val threshold = receiver.nextLevelXPRequired

        repeat(20) { receiver.receiveExperience(threshold / 20.0) }

        assertEquals(2.0, receiver.level)
        assertEquals(0.0, receiver.experience, absoluteTolerance = 1e-9)
    }

    @Test
    fun `a single large deposit can level up more than once`() {
        val receiver = DefaultExperienceReceiver()
        val firstThreshold = receiver.nextLevelXPRequired // level 1 -> 2, needs 11.0
        val secondThreshold = 10.0 + 2.0.let { it * it } // level 2 -> 3, needs 14.0

        receiver.receiveExperience(firstThreshold + secondThreshold + 3.0)

        assertEquals(3.0, receiver.level)
        assertEquals(3.0, receiver.experience, absoluteTolerance = 1e-9)
    }

    @Test
    fun `zero experience leaves level and experience unchanged`() {
        val receiver = DefaultExperienceReceiver()

        receiver.receiveExperience(0.0)

        assertEquals(1.0, receiver.level)
        assertEquals(0.0, receiver.experience)
    }
}
