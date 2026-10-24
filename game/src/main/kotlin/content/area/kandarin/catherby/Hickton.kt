package content.area.kandarin.catherby

import content.entity.npc.shop.openShop
import content.entity.player.dialogue.Neutral
import content.entity.player.dialogue.skillcapeShop
import content.entity.player.dialogue.type.choice
import content.entity.player.dialogue.type.npc
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill

class Hickton : Script {

    init {
        npcOperate("Trade", "hickton") {
            openHicktonShop(this)
        }

        npcOperate("Talk-to", "hickton") {
            npc<Neutral>("Welcome to Hickton's Archery Store. Do you want to see my wares?")

            choice {
                option<Neutral>("Can you tell me about your cape?") {
                    npc<Neutral>("Certainly! Skillcapes are a symbol of achievement. Only people who have mastered a skill and reached level 99 can get their hands on them and gain the benefits they carry.")
                    npc<Neutral>("Is there anything else I can help you with?")

                    choice {
                        option("I'd like to view your store, please.") {
                            openHicktonShop(this)
                        }
                        option<Neutral>("No thank you.")
                    }
                }

                option("Yes, please.") {
                    openHicktonShop(this)
                }

                option<Neutral>("No, I prefer to bash things close up.")
            }
        }
    }

    fun openHicktonShop(player: Player) {
        player.openShop(player.skillcapeShop(Skill.Fletching, "hicktons_archery_emporium"))
    }
}
