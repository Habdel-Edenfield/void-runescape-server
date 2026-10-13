package content.entity.player.dialogue

import org.junit.jupiter.api.Test

/** Draynor Village's npcs: the village, the jail (Prince Ali Rescue), the Wise Old Man and Draynor Manor */
class DraynorTranslationTest : RegionTranslationTest("misthalin/draynor", minimum = 12) {

    @Test
    fun `Draynor npcs speak Portuguese`() = talkToEveryNpc()
}
