package content.entity.player.dialogue

import org.junit.jupiter.api.Test

/** Al Kharid's npcs: the town, the palace, the Duel Arena, the Mage Training Arena and Shantay Pass */
class AlKharidTranslationTest : RegionTranslationTest("kharidian_desert/al_kharid", minimum = 20) {

    @Test
    fun `Al Kharid npcs speak Portuguese`() = talkToEveryNpc()
}
