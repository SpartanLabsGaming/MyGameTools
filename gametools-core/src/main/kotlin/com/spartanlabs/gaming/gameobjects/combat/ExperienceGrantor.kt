package com.spartanlabs.gaming.gameobjects.combat

/**
 * Interface for objects that can grant experience to other objects.
 * Typically this is for `Alive`s that grant experience on death.
 * There can be exceptions to this such as MOBA towers that would
 * technically be "`Alive`" but don't grant experience so the system is
 * not hard-coded into `Alive`
 */
interface ExperienceGrantor {
    /**
     * The amount of experience granted on death.
     */
    val xpGrantedOnDeath : Double
    /** The action of granting experiend
     * @param receiver The object that will receive the experience
     */
    fun grantExperienceTo(receiver : ExperienceReceiver)
}


