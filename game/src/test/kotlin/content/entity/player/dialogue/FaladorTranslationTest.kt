package content.entity.player.dialogue

import org.junit.jupiter.api.Test

/** Falador's npcs: the city, the park, the White Knights' Castle, the Mining Guild and the farm */
class FaladorTranslationTest : RegionTranslationTest("asgarnia/falador", minimum = 25) {

    @Test
    fun `Falador npcs speak Portuguese`() = talkToEveryNpc()
}
