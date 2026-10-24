package content.area.asgarnia.falador

import content.entity.npc.shop.openShop
import content.entity.player.dialogue.Happy
import content.entity.player.dialogue.Neutral
import content.entity.player.dialogue.type.choice
import content.entity.player.dialogue.type.npc
import world.gregs.voidps.engine.Script

class Flynn : Script {

    init {
        npcOperate("Talk-to", "flynn") {
            npc<Happy>("Hello. Do you want to buy or sell any maces?")
            choice {
                option<Neutral>("No, thanks.")
                option<Neutral>("Well, I'll have a look, at least.") {
                    openShop("flynns_mace_market")
                }
            }
        }
    }
}
