package content.skill.magic.spell

import content.entity.combat.hit.hit
import content.entity.effect.frozen
import content.entity.effect.toxin.poisoned
import content.skill.melee.CombatFormulaTest
import content.skill.melee.weapon.attackSpeed
import npcOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.variable.hasClock
import world.gregs.voidps.engine.client.variable.remaining
import world.gregs.voidps.engine.client.variable.stop
import world.gregs.voidps.engine.entity.character.mode.EmptyMode
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.timer.epochSeconds
import world.gregs.voidps.engine.timer.setCurrentTime

internal class AncientSpellEffectsTest : CombatFormulaTest() {

    @Test
    fun `Smoke spells poison target`() {
        val player = createPlayer(Skill.Magic to 99)
        val target = createPlayer(Skill.Constitution to 990)

        player.hit(target, offensiveType = "magic", spell = "smoke_blitz", damage = 100)
        tick(3)

        assertEquals(890, target.levels.get(Skill.Constitution))
        assertTrue(target.poisoned)
    }

    @Test
    fun `Shadow spells drain target`() {
        val player = createPlayer(Skill.Magic to 99)
        val target = createPlayer(Skill.Constitution to 990, Skill.Attack to 99)

        player.hit(target, offensiveType = "magic", spell = "shadow_blitz", damage = 100)
        tick(3)

        assertEquals(890, target.levels.get(Skill.Constitution))
        assertEquals(90, target.levels.get(Skill.Attack))
    }

    @Test
    fun `Blood spells heal caster`() {
        val player = createPlayer(Skill.Magic to 99, Skill.Constitution to 990)
        player.levels.set(Skill.Constitution, 500)
        val target = createPlayer(Skill.Constitution to 990)
        player.hit(target, offensiveType = "magic", spell = "blood_blitz", damage = 100)
        tick(3)

        assertEquals(890, target.levels.get(Skill.Constitution))
        assertEquals(525, player.levels.get(Skill.Constitution))
    }

    @Test
    fun `Ice spells freeze target`() {
        val player = createPlayer(Skill.Magic to 99)
        val target = createPlayer(Skill.Constitution to 990)
        player.hit(target, offensiveType = "magic", spell = "ice_blitz", damage = 100)
        tick(3)

        assertEquals(890, target.levels.get(Skill.Constitution))
        assertTrue(target.frozen)
    }

    @Test
    fun `Miasmic spells start half attack speed of target`() {
        val player = createPlayer(Skill.Magic to 99)
        val target = createPlayer(Skill.Constitution to 990)
        player.hit(target, offensiveType = "magic", spell = "miasmic_blitz", damage = 100)
        tick(3)

        assertEquals(890, target.levels.get(Skill.Constitution))
        assertTrue(target.hasClock("miasmic", epochSeconds()))
    }

    @Test
    fun `Miasmic slow wears off after the spell duration`() {
        val player = createPlayer(Skill.Magic to 99)
        val target = createPlayer(Skill.Constitution to 990)
        player.hit(target, offensiveType = "magic", spell = "miasmic_blitz", damage = 100)
        tick(3)
        val start = epochSeconds()

        setCurrentTime { (start + 35) * 1000L }
        assertTrue(target.hasClock("miasmic", epochSeconds()))

        setCurrentTime { (start + 36) * 1000L }
        assertFalse(target.hasClock("miasmic", epochSeconds()))
    }

    @Test
    fun `Miasmic slow doubles melee attack delay until it wears off`() {
        val caster = createPlayer(Skill.Magic to 99)
        val player = createPlayer(Skill.Constitution to 990)
        caster.hit(player, offensiveType = "magic", spell = "miasmic_blitz", damage = 100)
        tick(3)
        val start = epochSeconds()
        val npc = createNPC("giant_rat", player.tile.addY(1))

        player.npcOption(npc, "Attack")
        tickIf { !player.hasClock("action_delay") }
        assertEquals(player.attackSpeed * 2, player.remaining("action_delay"))

        player.mode = EmptyMode
        player.stop("action_delay")
        setCurrentTime { (start + 36) * 1000L }
        player.npcOption(npc, "Attack")
        tickIf { !player.hasClock("action_delay") }
        assertEquals(player.attackSpeed, player.remaining("action_delay"))
    }
}
