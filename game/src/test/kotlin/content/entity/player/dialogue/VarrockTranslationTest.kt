package content.entity.player.dialogue

import org.junit.jupiter.api.Test

/** Varrock's npcs: the city, palace, museum, Grand Exchange, guilds and dig site */
class VarrockTranslationTest : RegionTranslationTest("misthalin/varrock", minimum = 40) {

    @Test
    fun `Varrock npcs speak Portuguese`() = talkToEveryNpc()
}
