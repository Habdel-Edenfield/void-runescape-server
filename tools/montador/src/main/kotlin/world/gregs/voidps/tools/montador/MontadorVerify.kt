package world.gregs.voidps.tools.montador

import com.displee.cache.CacheLibrary
import world.gregs.voidps.cache.Index
import kotlin.system.exitProcess

/**
 * Checks that [Component634] writes back exactly the bytes it read, for every component of every interface
 * in the cache. Read only.
 *
 * Usage: `MontadorVerify <cache dir>`
 */
object MontadorVerify {

    @JvmStatic
    fun main(args: Array<String>) {
        val library = CacheLibrary(args.getOrElse(0) { "data/cache/" })
        val (checked, problems) = verify(library)
        library.close()
        println("Checked $checked components: ${problems.size} differ")
        for (problem in problems.take(20)) {
            println("  $problem")
        }
        if (problems.isNotEmpty()) {
            exitProcess(1)
        }
    }

    fun verify(library: CacheLibrary): Pair<Int, List<String>> {
        val index = library.index(Index.INTERFACES)
        var checked = 0
        val problems = mutableListOf<String>()
        for (archive in index.archiveIds()) {
            for (file in index.archive(archive)?.fileIds() ?: continue) {
                val data = library.data(Index.INTERFACES, archive, file) ?: continue
                checked++
                try {
                    val again = Component634.decode(data).encode()
                    if (!again.contentEquals(data)) {
                        val at = data.indices.firstOrNull { it >= again.size || again[it] != data[it] } ?: data.size
                        problems.add("$archive:$file differs at byte $at (${data.size} -> ${again.size})")
                    }
                } catch (exception: Exception) {
                    problems.add("$archive:$file $exception")
                }
            }
        }
        return checked to problems
    }
}
