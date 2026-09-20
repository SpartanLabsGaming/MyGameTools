package com.spartanlabs.gaming.gameobjects.combat

import com.spartanlabs.gaming.gameobjects.HomingProjectile

class StunningHomingProjectile(creator: Alive, target: Alive) : HomingProjectile(
    location = creator.location,
    dimensions = creator.dimensions.apply { amplifyBy(0.5) },
    damage = creator.damage.value,
    target = target,
    index = creator.world!!.spatialIndex
), BuffPlacer by StunPlacer()

