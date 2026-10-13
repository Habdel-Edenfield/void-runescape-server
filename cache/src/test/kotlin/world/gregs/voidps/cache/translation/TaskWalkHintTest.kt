package world.gregs.voidps.cache.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.gregs.voidps.cache.CacheDelegate
import world.gregs.voidps.cache.Config
import world.gregs.voidps.cache.Index
import world.gregs.voidps.cache.translation.CacheTranslationPatch.Op
import world.gregs.voidps.cache.translation.CacheTranslationPatch.Source
import world.gregs.voidps.cache.translation.CacheTranslationPatch.original
import java.io.File

/**
 * The Task System tab's walk to NPC icon: the hint drawing script asks [TaskWalkHint.HINT] for every line's map tile,
 * the tooltip script asks [TaskWalkHint.TOOLTIP] after translating, the back button shows on the tutorial set's task,
 * and all of it comes off again when undone.
 * `CacheTranslationPatchTest` checks data/cache has the current patch.
 */
class TaskWalkHintTest {

    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { it.resolve("data/cache").isDirectory }
    private val source = Source(CacheDelegate(root.resolve("data/cache").path + "/"))
    private val rows = CacheTranslationPatch.readTable(root.resolve(CacheTranslationPatch.TABLE_PATH))
    private val walks = TaskWalkHint.read(root)
    private val patch = CacheTranslationPatch.patch(source, rows, walks = walks)

    @Test
    fun `Explorer Jack's task walks from its first hint`() {
        val jack = walks.single { it.struct == 1724 } // introducing_explorer_jack_task
        assertEquals(1, jack.line)
        assertEquals("Click to walk to Explorer Jack.", jack.english)
        assertEquals("Clique para andar até o Explorer Jack.", jack.portuguese)
        val lines = patch.enums.values.single { it.valueType == 'i' && it.map?.get(1724) == 1 }
        assertEquals(0, lines.defaultInt)
    }

    @Test
    fun `Tab draws every hint tile through the walk hint`() {
        val hint = patch.scripts.filterValues { it.name == TaskWalkHint.HINT }.keys.single()
        val drawer = patch.scripts.getValue(TaskWalkHint.DRAW_HINTS)
        val calls = drawer.instructions.indices.filter { drawer.instructions[it] == Op.GOSUB && drawer.intOperands!![it] == hint }
        assertEquals(6, calls.size)
        assertEquals((1282..1287).toList(), calls.map { drawer.intOperands!![it - 1] })
        assertTrue(drawer.instructions.indices.none { drawer.instructions[it] == Op.STRUCT_PARAM && drawer.intOperands!![it - 1] in 1282..1287 })
    }

    @Test
    fun `Back button shows on the tutorial set's task`() {
        val progress = patch.scripts.filterValues { it.name == TaskWalkHint.PROGRESS }.keys.single()
        val drawer = patch.scripts.getValue(TaskWalkHint.DRAW_HINTS)
        val at = drawer.instructions.indices.single { drawer.instructions[it] == Op.GOSUB && drawer.intOperands!![it] == progress }
        // get_varp 281 (Unstable Foundations) < 135 hides the back button
        assertEquals(Op.PUSH_INT to 135, drawer.instructions[at + 1] to drawer.intOperands!![at + 1])
        val script = patch.scripts.getValue(progress)
        assertEquals(listOf(Op.PUSH_INT, Op.RETURN), script.instructions.toList())
        assertEquals(135, script.intOperands!![0])
    }

    @Test
    fun `Quest journal lines get their option from a fixed script`() {
        val script = patch.scripts.getValue(TaskWalkHint.QUEST_LINE_OP_ID)
        assertEquals(TaskWalkHint.QUEST_LINE_OP, script.name)
        // if_setop pops the component, then the option number, then the text
        assertEquals(listOf(Op.PUSH_INT to 1, Op.SLOAD to 0, Op.ILOAD to 0, Op.IF_SETOP to 0, Op.RETURN to 0), script.instructions.indices.map { script.instructions[it] to script.intOperands!![it] })
        assertTrue(patch.scripts.keys.filter { it != TaskWalkHint.QUEST_LINE_OP_ID }.all { it < TaskWalkHint.QUEST_LINE_OP_ID })
    }

    @Test
    fun `Tooltip asks the walk tooltip after translating`() {
        val walkTooltip = patch.scripts.filterValues { it.name == TaskWalkHint.TOOLTIP }.keys.single()
        val script = patch.scripts.getValue(CacheTranslationPatch.TOOLTIP_SCRIPT)
        val ops = script.instructions.toList()
        val ints = script.intOperands!!.toList()
        val at = ops.indices.single { ops[it] == Op.GOSUB && ints[it] == walkTooltip }
        assertEquals(Op.GOSUB, ops[at - 2])
        assertEquals(patch.tooltip, ints[at - 2])
        assertEquals(Op.ILOAD to 0, ops[at - 1] to ints[at - 1])
    }

    @Test
    fun `Patched scripts undo back to the original`() {
        val drawer = source.script(TaskWalkHint.DRAW_HINTS)!!.original(source)
        val ints = drawer.intOperands!!
        val tiles = drawer.instructions.indices.filter { drawer.instructions[it] == Op.STRUCT_PARAM && ints[it - 1] in 1282..1287 }
        assertEquals(6, tiles.size)
        assertTrue(drawer.instructions.indices.any { drawer.instructions[it] == Op.GET_VARP && ints[it] == 281 && ints[it + 1] == 135 })
        val (expected, _) = CacheTranslationPatch.HOOK_SCRIPTS.getValue(CacheTranslationPatch.TOOLTIP_SCRIPT)
        assertEquals(expected.toList(), source.script(CacheTranslationPatch.TOOLTIP_SCRIPT)!!.original(source).instructions.toList())
    }

    @Test
    fun `Client has room for the struct varc`() {
        // The client keeps the cache's varcs plus 1000 spare
        val varcs = source.cache.lastFileId(Index.CONFIGS, Config.VARC) + 1
        assertTrue(TaskWalkHint.STRUCT_VARC < varcs + 1000, "Varc ${TaskWalkHint.STRUCT_VARC} past the client's $varcs + 1000")
        assertTrue(TaskWalkHint.STRUCT_VARC >= varcs, "Varc ${TaskWalkHint.STRUCT_VARC} is one of the cache's")
    }
}
