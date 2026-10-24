package content.entity.player.dialogue.type

import content.entity.player.dialogue.Expression
import net.pearx.kasechange.toSnakeCase
import world.gregs.voidps.engine.entity.character.player.Player

class ChoiceOption {

    val strings = mutableListOf<String>()
    val options = mutableListOf<suspend Player.() -> Unit>()
    val expressions = mutableListOf<String?>()

    /**
     * Displays option [text] when [filter] is true and if selected invokes [block]
     */
    fun option(text: String, block: suspend Player.() -> Unit = {}) {
        strings.add(text)
        options.add(block)
        expressions.add(null)
    }

    /**
     * Same as [option] but repeats [text] as player dialogue with [Expression] [E] before invoking [block]
     * when the choice menu was skipped, so the player still sees what was said
     */
    @JvmName("optionInline")
    inline fun <reified E : Expression> option(text: String, noinline block: suspend Player.() -> Unit = {}) {
        strings.add(text)
        options.add(block)
        expressions.add(E::class.simpleName!!.toSnakeCase())
    }

    suspend fun invoke(index: Int, context: Player, repeat: Boolean = false) {
        check(index in options.indices) { "Invalid choice: $index in $strings" }
        val expression = expressions[index]
        if (repeat && expression != null) {
            context.player(expression, strings[index])
        }
        options[index].invoke(context)
    }

    fun build(): List<String> = strings
}
