package content.entity.player.dialogue

import org.junit.jupiter.api.Test

/** Port Sarim's npcs: the docks, the shops, the jail and the Rusty Anchor */
class PortSarimTranslationTest : RegionTranslationTest("asgarnia/port_sarim", minimum = 15) {

    @Test
    fun `Port Sarim npcs speak Portuguese`() = talkToEveryNpc()
}
