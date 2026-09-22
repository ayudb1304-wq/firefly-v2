package com.firefly.app.core.group

import kotlin.random.Random

/** Random u16 per install, regenerated on "Leave group". 0x0000 and 0xFFFF are never issued. */
object SenderId {
    fun generate(random: Random = Random.Default): Int = random.nextInt(0x0001, 0xFFFF)

    fun isValid(id: Int): Boolean = id in 0x0001..0xFFFE

    fun hex(id: Int): String = "%04X".format(id)
}
