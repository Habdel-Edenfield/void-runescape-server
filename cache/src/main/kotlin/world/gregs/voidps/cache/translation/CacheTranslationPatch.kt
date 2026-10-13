package world.gregs.voidps.cache.translation

import world.gregs.voidps.buffer.read.ArrayReader
import world.gregs.voidps.buffer.write.ArrayWriter
import world.gregs.voidps.cache.Cache
import world.gregs.voidps.cache.Index
import world.gregs.voidps.cache.config.data.StructDefinition
import world.gregs.voidps.cache.config.decoder.StructDecoder
import world.gregs.voidps.cache.definition.data.ClientScriptDefinition
import world.gregs.voidps.cache.definition.data.EnumDefinition
import world.gregs.voidps.cache.definition.data.InterfaceComponentDefinitionFull
import world.gregs.voidps.cache.definition.data.InterfaceDefinitionFull
import world.gregs.voidps.cache.definition.decoder.ClientScriptDecoder
import world.gregs.voidps.cache.definition.decoder.EnumDecoder
import world.gregs.voidps.cache.definition.decoder.InterfaceDecoderFull
import world.gregs.voidps.cache.definition.encoder.ClientScriptEncoder
import world.gregs.voidps.cache.definition.encoder.EnumEncoder
import java.io.File

/**
 * Brazilian Portuguese for interfaces the client draws itself from the cache: the Task System (task list
 * window, sidebar tab and completion pop-up) and the level up pop-up (`skill_level_details`).
 *
 * The server can't reach their text, so the Portuguese is added to the cache
 * next to the English and the interface scripts are changed to pick it when the client variable
 * [LANGUAGE_VARC] (`portuguese_dialogue` on the server) is 1. Each player still chooses their language
 * and the English stays untouched.
 *
 * Every change is a one-for-one instruction swap, so no jump inside an original script moves:
 * - `struct_param` of a text param ([STRUCT_TEXTS]: task names, descriptions and hints, prayer descriptions) becomes
 *   a call to [PARAM_TEXT]
 * - `enum` of the area and difficulty names becomes a call to [TASK_ENUM], `enum_string` of the level up
 *   messages a call to [ENUM_STRING]
 * - each translated string constant becomes a call to a script returning the English or the Portuguese
 * - the generic scripts hooks pass text to (tooltips, the level up link) get one call added to translate it
 *
 * This only works the patch out; `InterfaceTranslation` in tools writes it to the cache.
 */
object CacheTranslationPatch {

    const val LANGUAGE_VARC = 2000
    const val TABLE_PATH = "data/entity/player/dialogue/translation/interface_pt_br.tables.toml"

    /** Translation sources in tools/translation/sources, `<prefix>*.json` mapping English to Portuguese */
    val SOURCE_PREFIXES = listOf("pt_tasks", "pt_level_up", "pt_labels", "pt_ui")

    /** Task sets translated so far: Lumbridge/Draynor and the tutorial's Unstable Foundations */
    val AREAS = setOf(1, 60)

    const val TASK_STRUCTS = 3483
    const val AREA_NAMES = 3487
    const val DIFFICULTY_NAMES = 3488
    const val TASK_NAME = 1266
    private const val TASK_AREA = 1267
    const val TASK_DESCRIPTION = 1273
    const val TASK_SET = 1292
    val HINTS = 1274..1279
    const val PRAYER_DESCRIPTION = 734

    /** Struct params holding shown text, with the prefix of the table rows translating them */
    val STRUCT_TEXTS: Map<Int, String> = mapOf(TASK_NAME to "name_", TASK_DESCRIPTION to "desc_") +
        HINTS.associateWith { "hint" } + mapOf(TASK_SET to "set_", PRAYER_DESCRIPTION to "prayer_")
    val TEXT_PARAMS = STRUCT_TEXTS.keys
    const val TOOLTIP_SCRIPT = 38

    /** "You've just advanced a Cooking level!" for each skill, read with `enum_string` */
    const val LEVEL_UP_MESSAGES = 1477

    val INTERFACES = mapOf(
        "task_list" to 917, "task_system" to 1056, "task_popup" to 1055, "skill_level_details" to 741,
        "logout" to 182, "options" to 261, "stats" to 320, "quest_journals" to 190, "combat_styles" to 884, "prayer_list" to 271,
        "friends_list" to 550, "ignore_list" to 551, "clan_chat" to 589, "chat_background" to 137, "filter_buttons" to 751,
        "bank" to 762, "shop" to 620,
        "quest_intro" to 178, "audio_options" to 743, "area_status_icon" to 745, "toplevel" to 548, "toplevel_full" to 746,
    )

    /** Month names, read with `enum` by the date of birth box and other dates */
    const val MONTHS = 950

    /**
     * Generic scripts hooks pass text to, with the instructions they must still have and where the translation
     * call goes: the tooltip script (38) and the level up link's hover (2381: set text and colour).
     */
    val HOOK_SCRIPTS = mapOf(
        TOOLTIP_SCRIPT to (intArrayOf(Op.ILOAD, Op.PUSH_INT, Op.ILOAD, Op.SLOAD, Op.ILOAD, Op.ILOAD, Op.GOSUB, Op.RETURN) to 4),
        2381 to (intArrayOf(Op.SLOAD, Op.ILOAD, 2112, Op.ILOAD, Op.ILOAD, 2101, Op.RETURN) to 1),
        // The bank's and the quest intro's tooltips
        1160 to (intArrayOf(Op.ILOAD, Op.ILOAD, Op.ILOAD, Op.SLOAD, Op.ILOAD, Op.ILOAD, Op.GOSUB, Op.RETURN) to 4),
    )

    /**
     * Longer hook scripts, full of jumps, translated by three instructions put before everything else
     * (load the string argument, translate it, store it back), so no jump moves: script to its instruction count
     * and string argument. 724 is the area status icon's tooltip ("Multiway Combat", "PvP Safe Area").
     */
    val PREFIX_HOOK_SCRIPTS = mapOf(724 to (113 to 0))

    /** Every script whose hooks pass text the tooltip script translates */
    val HOOKED = HOOK_SCRIPTS.keys + PREFIX_HOOK_SCRIPTS.keys

    const val PARAM_TEXT = "pt_br_param_text"

    /** [PARAM_TEXT]'s name before it read any param, kept so an older patch can still be undone */
    private const val TASK_TEXT = "pt_br_task_text"
    const val TASK_ENUM = "pt_br_task_enum"
    const val ENUM_STRING = "pt_br_enum_string"
    const val STRING = "pt_br_string"
    const val TOOLTIP = "pt_br_tooltip"
    private const val ENUM_MARKER = Int.MIN_VALUE

    /** [ENUM_MARKER]'s value in an int enum, which can't hold the "pt_br" string */
    private const val INT_MARKER = 0x70745F62

    /** A string constant a task script shows; [occurrence] picks one copy when the same text means different things */
    data class ScriptText(val script: Int, val english: String, val occurrence: Int? = null)

    /** Scripts whose every string constant is shown text, apart from [SCRIPT_TEXT_KEPT] */
    private val WHOLE_SCRIPTS = listOf(3223, 3995, 976, 178)

    private val SCRIPT_TEXTS = listOf(
        ScriptText(3997, "Progress:"),
        ScriptText(4002, "Progress:  "),
        ScriptText(4017, "Click to hide completed Tasks."),
        ScriptText(4017, "Click to show completed Tasks."),
        ScriptText(4017, "Click to hide Tasks which are not in a set."),
        ScriptText(4017, "Click to show Tasks which are not in a set."),
        ScriptText(3991, "Difficulty : "),
        ScriptText(3991, "Area : "),
        ScriptText(3991, "ten"),
        ScriptText(3991, "forty"),
        ScriptText(3991, "Completing this Task will earn you "),
        ScriptText(3991, " coins."),
        ScriptText(3991, "You're already owed a reward! You may claim it from Explorer Jack or any other taskmaster, or increase it by completing more Tasks. "),
        ScriptText(3991, "You are also owed one or more items."),
        ScriptText(3977, "ten"),
        ScriptText(3977, "forty"),
        ScriptText(3977, "Completing this Task will earn you "),
        ScriptText(3977, " coins."),
        ScriptText(3977, "    Pin"),
        ScriptText(3977, "     Back"),
        ScriptText(3977, "You have already earned a sum of money, which you may claim by talking to any taskmaster or increase by continuing to complete Tasks."),
        ScriptText(3222, "This Task requires "),
        // "This Task requires 5 Attack." -> "Esta tarefa exige nível 5 em Attack."
        ScriptText(3222, " ", occurrence = 0),
        ScriptText(3222, "To complete this Task, you must complete the "),
        ScriptText(3222, " quest."),
        // Level up pop-up
        ScriptText(973, "You've just advanced 2 Herblore levels!"),
        ScriptText(973, "You've just advanced 3 Summoning levels!"),
        ScriptText(973, "You have now reached level "),
        ScriptText(973, "You've now reached the highest skill level that you can achieve during the tutorial. Once you finish the tutorial, you can advance this skill even further!"),
        ScriptText(973, "Well done! You've reached the total level "),
        ScriptText(973, "Well done! You've reached the Combat level "),
        ScriptText(973, " milestone!"),
        ScriptText(973, "Check out the skill advance guide to see what you'll be able to do when you reach even higher levels..."),
        // "Level 5 Cooking is one of the requirements for Cook's Assistant." -> "Nível 5 em Cooking é um dos requisitos da missão Cook's Assistant."
        ScriptText(1025, "Level "),
        ScriptText(1025, " ", occurrence = 0),
        ScriptText(1025, " is one of the requirements for "),
        ScriptText(1025, "You now have all the levels you need to complete "),
        // Logout tab: "When you have finished playing<br>RuneScape, always use the<br>button below to exit the game and logout safely."
        ScriptText(1104, "When you have finished playing"),
        ScriptText(1104, "RuneScape, always use a"),
        ScriptText(1104, "RuneScape, always use the"),
        ScriptText(1104, "button below to exit the game and logout safely."),
        // The button labels; the second copy of each is the right-click option
        ScriptText(1104, "Exit to Lobby", occurrence = 0),
        ScriptText(1104, "Exit to Login", occurrence = 0),
        ScriptText(1104, "Click here to log out"),
        // Options tab: "Mouse buttons<br>(currently 2)"
        ScriptText(1264, "Mouse buttons"),
        ScriptText(1264, "(currently 2)"),
        ScriptText(1264, "(currently 1)"),
        ScriptText(1265, "Chat effects"),
        ScriptText(1265, "(currently off)"),
        ScriptText(1265, "(currently on)"),
        ScriptText(1269, "Accept aid"),
        ScriptText(1269, "(currently off)"),
        ScriptText(1269, "(currently on)"),
        ScriptText(1271, "Run (currently off)"),
        ScriptText(1271, "Run (currently on)"),
        ScriptText(1272, "House options"),
        // Stats tab and the skill hover box
        ScriptText(544, "Total level: "),
        ScriptText(547, "Members' Skill"),
        ScriptText(547, "Current Xp:"),
        ScriptText(547, "Next level:"),
        ScriptText(547, "Remainder:"),
        ScriptText(547, "Target lvl:"),
        ScriptText(547, "Target xp:"),
        ScriptText(547, "Target XP:"),
        // Quest Journal: "Showing 12 of 188 items", "Quest Points: 7 / 300"
        ScriptText(943, "More advanced grouping options are available on a members' world."),
        ScriptText(943, "More advanced grouping options will be available when you've finished the Tutorial."),
        ScriptText(2162, "Showing all "),
        ScriptText(2162, " items"),
        ScriptText(2162, "Showing "),
        ScriptText(2162, " of "),
        ScriptText(2162, " (reversed)"),
        ScriptText(2166, "Quest Points: "),
        ScriptText(2168, "More advanced sorting options are available on a members' world."),
        ScriptText(2168, "More advanced sorting options are available after the Tutorial."),
        ScriptText(2169, "Free/Members"),
        ScriptText(2176, "Click to hide the quests you cannot start."),
        ScriptText(2176, "Some recommended stats and abilities (such as combat levels) are left for you to decide."),
        ScriptText(2176, "Click to show all quests regardless of requirements."),
        ScriptText(2176, "Click to hide the quests you have completed."),
        ScriptText(2176, "Click to show all quests regardless of completion status."),
        // Combat styles tab: style and attack names stay in English, the experience they train is translated
        ScriptText(1131, "Combat Lvl: "),
        ScriptText(1133, "Unarmed"),
        ScriptText(1137, "Special Attack ("),
        ScriptText(1139, "Auto Retaliate "),
        ScriptText(1139, "(Off)"),
        ScriptText(1139, "(On)"),
        ScriptText(1142, "Attack XP"),
        ScriptText(1142, "Strength XP"),
        ScriptText(1142, "Defence XP"),
        ScriptText(1142, "Ranged XP"),
        ScriptText(1142, "Magic XP"),
        ScriptText(1142, "Shared XP"),
        ScriptText(1143, "When active, you will automatically fight back if attacked."),
        ScriptText(1143, "Select to perform a special attack."),
        // Prayer tab
        ScriptText(1698, "This is the effect that prayers and curses have during combat. It includes curses that have used against you. The adjustment has no effect outside of combat. The percentage shown is relative to your skill level and may vary depending on the enemy you are fighting, and the prayers or curses used. Partial percentages are not shown."),
        ScriptText(1700, "Hide stat adjustments."),
        ScriptText(1700, "Show stat adjustments."),
        ScriptText(2299, "Select your quick curses:"),
        ScriptText(2299, "Select your quick prayers:"),
        // Chat box: private messages ("From Zezinho: oi"), system update timer and filter buttons
        ScriptText(84, "From "),
        ScriptText(84, "To "),
        ScriptText(89, "System update in: "),
        ScriptText(89, "From "),
        ScriptText(89, "To "),
        ScriptText(175, "You can't do this while in the tutorial."),
        ScriptText(175, "The friends list is still loading, your selection won't take effect immediately."),
        // Chat input: prompts and what it says while typing a private message or searching
        ScriptText(73, "Chat is not available until your Date of Birth is recorded. Please enter your DOB above."),
        ScriptText(73, "That player is not on your Friends list."),
        ScriptText(73, "You haven't received any messages to which you can reply."),
        ScriptText(73, "Use the reverse apostrophe (`) key to open the console to enter that command."),
        ScriptText(73, "It is usually located under the ESC key."),
        ScriptText(73, "You aren't in a Clan Chat channel."),
        ScriptText(86, "You cannot add a friend until you have entered your date of birth"),
        ScriptText(102, "Click here to continue"),
        ScriptText(107, "You can't do that while you're reporting abuse."),
        ScriptText(107, "Enter message to send to "),
        ScriptText(1037, "To "),
        ScriptText(1037, "Search for: *"),
        ScriptText(1038, "Search for: "),
        ScriptText(1039, "Too many results. Please refine your search."),
        ScriptText(1039, "No matching items found."),
        ScriptText(1044, "To "),
        ScriptText(1047, "To "),
        ScriptText(1047, "Please enter a value: *"),
        ScriptText(1048, "Please enter a value: "),
        ScriptText(1048, "Please enter a value: *"),
        ScriptText(1050, "You need to be in a Clan Chat channel to use Clan Quick Chat."),
        ScriptText(1062, " To "),
        ScriptText(1062, "Search "),
        ScriptText(1063, "From "),
        ScriptText(1077, "Sorry, this user is not on your Friends List."),
        ScriptText(1077, "Sorry, your friend is no longer playing RuneScape."),
        ScriptText(1900, "Search phrases..."),
        ScriptText(1900, "To "),
        ScriptText(1900, "Search for: *"),
        ScriptText(1901, "Search for: "),
        ScriptText(1902, "Too many results. Please refine your search."),
        ScriptText(1902, "No matching items found."),
        // Friends and ignore lists
        ScriptText(103, "Enter name of friend to add to list"),
        ScriptText(104, "Enter name of friend to delete from list"),
        ScriptText(125, "Loading Friends List."),
        ScriptText(125, "Please wait."),
        ScriptText(125, "Connecting to Friend Server."),
        ScriptText(125, "Last known as: "),
        ScriptText(125, "Last known as:"),
        ScriptText(126, "That player is currently offline."),
        ScriptText(105, "Enter name of player to add to list"),
        ScriptText(1419, "Enter name of player to delete from list"),
        ScriptText(129, "Loading Ignore List."),
        ScriptText(129, "Please wait."),
        ScriptText(129, "Last known as: "),
        ScriptText(129, "Last known as:"),
        // Clan chat
        ScriptText(111, "Enter the player name whose channel you wish to join:"),
        ScriptText(111, "Last name entered: "),
        ScriptText(111, "Your name: "),
        ScriptText(197, "Talking in: "),
        ScriptText(197, "Owner: "),
        ScriptText(197, "Talking in: Not in chat"),
        ScriptText(197, "Owner: None"),
        // The button labels; the second copy of each is the right-click option
        ScriptText(197, "Leave Chat", occurrence = 0),
        ScriptText(197, "Join Chat", occurrence = 0),
        ScriptText(2688, "Enter the player to ban from the channel:"),
        // Bank: "Total number of <col>free</col> bank slots used" keeps its order, "Total de espaços grátis usados no banco"
        ScriptText(1465, "Total number of "),
        ScriptText(1465, "free"),
        ScriptText(1465, "member"),
        ScriptText(1465, "demo"),
        ScriptText(1465, " bank slots used"),
        ScriptText(1465, "Note: this includes an overflow of "),
        ScriptText(1465, " free items"),
        ScriptText(1465, " member items"),
        ScriptText(1465, "Note: this includes an overflow of 1 free item"),
        ScriptText(1465, "Note: this includes an overflow of 1 member item"),
        ScriptText(1479, "Bank of RuneScape (no search entered)"),
        ScriptText(1479, "Bank of RuneScape (search: '"),
        ScriptText(1480, "Item is in tab "),
        ScriptText(1487, "Switch to note withdrawal mode"),
        ScriptText(1487, "Switch to item withdrawal mode"),
        ScriptText(1490, "Switch to insert items mode"),
        ScriptText(1490, "Switch to swap items mode"),
        ScriptText(1549, "Enter the name of the item you wish to search for:"),
        // Shop: requirements shown for an item, and its price when there's none
        ScriptText(2706, "You do not meet the requirements to use or wield this item."),
        ScriptText(2706, "You do not meet the requirements to use or wear this item."),
        ScriptText(2706, "You can wield this item but not use it."),
        ScriptText(2706, "You can wear this item but not use it."),
        ScriptText(2706, "You do not meet the requirements to use this item."),
        ScriptText(2706, "You can use this item but not wield it."),
        ScriptText(2706, "You can use this item but not wear it."),
        ScriptText(2706, "You do not meet the requirements to wield this item."),
        ScriptText(2706, "You do not meet the requirements to wear this item."),
        ScriptText(2723, "N/A"),
        ScriptText(2726, "Free"),
        // Low health warning
        ScriptText(1888, "Your health is low! Run away from your attacker and speak to Xenia."),
        ScriptText(1888, "Your health is low! Find a safe place away from your attacker"),
        ScriptText(1888, "or eat some food to heal yourself."),
        // Items kept on death
        ScriptText(118, "Items you will keep on death:"),
        ScriptText(118, "Items you will lose on death:"),
        ScriptText(118, "Items you will keep on death (if not skulled):"),
        ScriptText(118, "Items you will lose on death (if not skulled):"),
        // The information panel, put together in pieces: "You have the " + "Protect Item" + " prayer active..."
        ScriptText(118, "The normal amount of items kept is three."),
        ScriptText(118, " This reduces the items you keep from three to zero!"),
        ScriptText(118, "However, you also have the "),
        ScriptText(118, " curse active, which saves you one extra item!"),
        ScriptText(118, " prayer active, which saves you one extra item!"),
        ScriptText(118, "You have no factors affecting the items you keep."),
        ScriptText(118, "You have the "),
        ScriptText(118, "Items with a "),
        ScriptText(118, "white outline"),
        ScriptText(118, " are ones you will always lose."),
        ScriptText(118, "You have items in a creature's inventory, which will be dropped where it is standing if it or you die."),
        ScriptText(118, "Max items kept on death :"),
        ScriptText(118, "Carried wealth:"),
        ScriptText(118, "Risked wealth:"),
        ScriptText(118, "You're in a "),
        ScriptText(118, "player-owned house"),
        ScriptText(118, "which is a "),
        ScriptText(118, "safe area"),
        ScriptText(118, " If you die you will keep all your items except the items you would lose on leaving the house via the portal."),
        ScriptText(118, "You're in a game of "),
        ScriptText(118, " If you die you will keep all your items, except the team banner should you be carrying it."),
        ScriptText(118, " If you die you will keep all your items brought with you, including the Trouble Brewing manual, but will lose everything else."),
        ScriptText(118, " If you die you will keep all your items, except items from the dispensors, logs, hammers and Penance eggs."),
        ScriptText(118, " If you die you will keep all the items you brought with you."),
        ScriptText(118, ". See information to the left for a more detailed description."),
        ScriptText(118, "All items !"),
        // Price checker
        ScriptText(2184, "Click on items in your inventory to check their values."),
        ScriptText(2184, "Total value:"),
        ScriptText(2184, "Current trade limit:"),
        // Trade: wealth limit and loan duration
        ScriptText(2505, "Limit: "),
        ScriptText(713, "Until"),
        ScriptText(713, "logout"),
        ScriptText(713, "1 hour"),
        ScriptText(713, " hours"),
        ScriptText(713, "Duration"),
        // XP counter
        ScriptText(776, "Multiplier:"),
        ScriptText(776, "Bonus:"),
        ScriptText(776, "Elapsed:"),
        ScriptText(517, "Lots!"),
        // What would you like to make? box
        ScriptText(2268, "Click the object to see more options."),
        ScriptText(2268, "Right-click the object to see more options."),
    )

    /** Scripts of the translated tabs checked for English left over, with what stays in English in each */
    val CHECKED_SCRIPTS: Map<Int, Set<String>> = mapOf(
        1104 to setOf("Exit to Lobby", "Exit to Login", "Logout"),
        1264 to setOf(), 1265 to setOf(), 1269 to setOf(), 1271 to setOf(), 1272 to setOf(),
        544 to setOf(), 547 to setOf(), 548 to setOf(),
        943 to setOf(), 947 to setOf(" (r)", "Select"),
        2160 to setOf("View Quest Overview", "View Quest Journal", "Show on the World Map"),
        2162 to setOf("View Quest Journal", "View Quest Overview"),
        2166 to setOf(), 2168 to setOf(), 2169 to setOf(), 2176 to setOf(),
        1131 to setOf(), 1133 to setOf(), 1137 to setOf(), 1139 to setOf(), 1143 to setOf(),
        // Style and attack names, and the attack types and skills they train
        1142 to setOf(
            "Accurate", "Aggressive", "Aim and fire", "Bash", "Blaze", "Block", "Chop", "Chuck", "Controlled", "Crush", "Defensive",
            "Deflect", "Fend", "Flare", "Flick", "Focus", "Hack", "Impale", "Jab", "Kick", "Lash", "Lob", "Long fuse", "Long range",
            "Lunge", "Magic", "Medium fuse", "Pound", "Pummel", "Punch", "Ranged", "Rapid", "Reap", "Scorch", "Short fuse", "Slash",
            "Sling", "Smash", "Spike", "Stab", "Swipe",
        ),
        49 to setOf("Deactivate", "Activate"), 53 to setOf(), 1237 to setOf("Activate"), 1388 to setOf("Select", "Deselect"),
        1698 to setOf(), 1700 to setOf("Hide stat adjustments", "Show stat adjustments"), 1706 to setOf(),
        2290 to setOf("Deselect", "Select"),
        2299 to setOf("Finish quick curse selection", "Finish quick prayer selection", "Select quick curses", "Select quick prayers"),
        // Chat and friends: right-click options, and the friends list title naming the interface
        84 to setOf(
            "Accept alliance", "Accept challenge", "Accept trade", "Add friend", "Add ignore", "Give assistance", "Kick/ban", "Message",
            "Open invitation", "Quick Response", "Report abuse", "Vote",
        ),
        89 to setOf("Message", "Add friend", "Add ignore", "Report abuse", "Quick Response"),
        73 to setOf(), 86 to setOf(), 102 to setOf(), 103 to setOf(), 104 to setOf(), 105 to setOf(), 107 to setOf(),
        111 to setOf("Use:"), 123 to setOf("Friends List - RuneScape "),
        125 to setOf("Message", "Quick Message", "Delete", "Offline", "event_opbase"), 126 to setOf(), 129 to setOf("Delete"),
        175 to setOf(), 178 to setOf(), 197 to setOf("Leave Chat", "Join Chat"),
        1037 to setOf(), 1038 to setOf(), 1039 to setOf(), 1044 to setOf("non-alpha"), 1047 to setOf(), 1048 to setOf(), 1050 to setOf(),
        1059 to setOf("X"), 1062 to setOf("Return to: ", "Select: ", "Send: ", "Search phrases", "X. ", "Enter. "), 1063 to setOf("Send: "),
        1066 to setOf(), 1077 to setOf(), 1419 to setOf(),
        1596 to setOf("Remove friend ", "Remove ignore ", "Add friend ", "Add ignore ", "Kick/ban user "),
        1900 to setOf(), 1901 to setOf(), 1902 to setOf(), 2312 to setOf("Select"), 2688 to setOf(),
        // Bank and shop: the bank's name, right-click options and the option names the shop compares against
        1465 to setOf(), 1472 to setOf("Search"), 1474 to setOf("Bank of RuneScape"), 1479 to setOf(), 1480 to setOf(),
        1487 to setOf(), 1490 to setOf(), 1549 to setOf(),
        2706 to setOf("Wield", "Wear"),
        2723 to setOf("Info", "Buy 1", "Examine", "Buy 5", "Buy 10", "Buy 50", "Buy 500"),
        2726 to setOf("Info", "Take 1", "Take 5", "Take 10", "Take 50", "Examine"),
        // Small windows: low health, items kept on death, price checker, trade limit and loan, XP counter, make box
        1888 to setOf(), 2505 to setOf(),
        // Prayer and minigame names, and the item label
        118 to setOf("Protect Item", "Castle Wars", "Trouble Brewing", "Barbarian Assault", "Item : "),
        2184 to setOf("Remove-1", "Remove-5", "Remove-10", "Remove-All", "Remove-X", "Examine", " x "),
        713 to setOf("'Until logout'", "Remove", "Examine", "Specify", "Edit"),
        776 to setOf("1.1x", "Hello", "xp"), 517 to setOf(), 337 to setOf("xp"), 3368 to setOf("xp"),
        2268 to setOf(),
    )

    /**
     * String constants in the task scripts which aren't shown or stay in English: tags, numbers,
     * right-click options (kept in English like every other option), and a debug message.
     * Hook signatures ("iii", "Is"...) are recognised by [isSignature].
     */
    val SCRIPT_TEXT_KEPT = setOf(
        "", "<str>", "<br>", " -", ".", "null", "id", "160", "640", "2560", "<col=000080>", "<col=800000>",
        "<col=ffff00>", "<col=ff0000>", "<col=00ffff>",
        "Summary for", "Summary", "Pin", "Unpin", "Pin/Unpin Task", "Turn-on", "Turn-off",
        ",", ": ", "/", " / ", "%", "+", "%)", "<col=ff9040>",
        "Couldn't find the Task's struct.",
        // The task tab's name, reached from the game frame's hooks
        "Task List",
    )

    /** The argument types a script hook is registered with, e.g. "IIsii" */
    fun isSignature(text: String) = text.isNotEmpty() && text.all { it in "iIsSl1Yd" }

    /**
     * Fixed interface labels (interface to components) the server replaces when the interface opens, as no script
     * writes them. Any interface listed here is checked for labels nobody translates; see [LABELS_KEPT].
     */
    val LABELS = mapOf(
        917 to listOf(16, 20, 24, 28, 59, 95, 141, 145, 147),
        1056 to listOf(10, 15, 20, 25, 30, 35, 43, 48, 53, 113, 170, 172, 176, 178, 179, 180),
        1055 to listOf(4),
        741 to listOf(5, 10),
        371 to listOf(2, 27), // tutorial_overlay
        277 to listOf(3, 6, 9), // quest_complete
        178 to listOf(15, 16, 17, 18, 19, 30, 57, 67, 72, 76, 77, 78), // quest_intro
        335 to listOf(17, 19, 36, 37, 40, 41, 44, 83, 87), // trade_main
        334 to listOf(31, 32, 34, 35, 36, 45), // trade_confirm
        667 to listOf(27, 28, 29, 65), // equipment_bonuses
        102 to listOf(25), // items_kept_on_death
        13 to listOf(30), // bank_pin
        14 to listOf(60, 61, 62, 63, 64, 65, 68, 70), // bank_pin_settings
        620 to listOf(21, 22, 23, 24, 30, 31), // shop
        261 to listOf(1), // options
        320 to listOf(209, 211, 212, 213), // stats
        190 to listOf(1, 9, 11), // quest_journals
        884 to listOf(8), // combat_styles
        271 to listOf(44), // prayer_list
        751 to listOf(11, 13, 14, 16, 17, 19, 20, 22, 23, 25, 26, 28, 29, 31), // filter_buttons
        550 to listOf(7, 8), // friends_list
        551 to listOf(4, 5), // ignore_list
        589 to listOf(16), // clan_chat
        137 to listOf(10, 64, 110, 158, 169, 171), // chat_background
        762 to listOf(94, 98, 99, 100, 101, 102, 103, 104, 105, 115, 116), // bank
        743 to listOf(50, 52), // audio_options
        206 to listOf(), // price_checker
        944 to listOf(7, 15, 21), // dialogue_make_amount
        548 to listOf(38, 62, 64), // toplevel: XP counter
        746 to listOf(197, 211, 215), // toplevel_full: XP counter
    )

    /**
     * Fixed labels which stay as they are: interface names (Task List, Equipment Bonuses...), place names, numbers,
     * attack types and stat names, and placeholders the server or a script overwrites (left for when those are done).
     */
    val LABELS_KEPT = mapOf(
        917 to listOf(70, 107, 119, 121, 123, 125, 127, 129, 131, 143),
        1056 to listOf(107, 117),
        1055 to listOf(),
        741 to listOf(),
        371 to listOf(1), // server: percent
        277 to listOf(4, 7) + (10..17), // server: quest name, points, rewards
        178 to (9..14) + 70, // "( ? )", divider, server: status
        335 to listOf(15, 38, 43, 45), // server: title, status; script 2505: wealth limit
        334 to listOf(33, 37, 38, 39, 41, 42, 43), // server: status and item lists
        667 to (30..47) + 75, // server: bonus lines; window name
        102 to listOf(15, 17, 20, 34), // window name; script 118
        13 to (11..20) + listOf(28, 31), // digits; server: instruction, deletion
        14 to listOf(40, 41, 69, 71, 73, 86, 89, 91), // window name; server: messages, status, confirm
        620 to listOf(20), // server: shop name
        261 to listOf(13), // script 1110
        320 to listOf(7, 8, 15, 16, 24, 25, 32, 33, 40, 41, 48, 49, 56, 57, 64, 65, 72, 73, 80, 81, 89, 90, 97, 98, 106, 107, 114, 115, 122, 123, 130, 131, 138, 139, 146, 147, 154, 155, 161, 162, 168, 169, 175, 176, 182, 183, 189, 190, 196, 197, 201), // levels; script 544
        190 to listOf(2, 8), // scripts 2166, 2162
        884 to listOf(0, 1, 18), // scripts 1131, 1139
        271 to listOf(12, 30, 31, 32, 33, 34, 46), // scripts 1700, 1707, 2299
        751 to listOf(),
        550 to listOf(6), // script 123: Friends List, the interface name
        551 to listOf(3), // Ignore List, the interface name
        589 to listOf(4, 5, 14, 15), // script 197; Clan Chat, the interface name
        137 to listOf(8, 14, 125, 140), // scripts 1047, 1037/1900, 2334, 2316
        762 to listOf(25, 26, 29, 30, 31, 32, 42, 45), // counts; script 1465; Bank PIN, the feature's name; script 1474: the bank's name
        743 to listOf(21), // Audio Options, the window's name
        206 to listOf(17), // Price Checker, the window's name
        944 to listOf(),
        548 to listOf(53, 58, 59, 60, 61, 63), // XP counter placeholders scripts overwrite
        746 to listOf(210, 212, 214, 217, 218, 219), // XP counter placeholders scripts overwrite
    )

    /** Text the server puts into task chat messages next to the task name and difficulty (see TaskSystem.kt) */
    val CHAT = listOf(
        "Lumbridge and Draynor",
        "Explorer Jack in Lumbridge",
        "Bob in Bob's Axes in Lumbridge",
        "Ned in Draynor Village",
        "someone somewhere",
    )

    /*
        Reading the cache
     */

    class Source(val cache: Cache) {
        val structs: Array<StructDefinition> = StructDecoder().load(cache)
        val enums: Array<EnumDefinition> = EnumDecoder().load(cache)
        val interfaces: Array<InterfaceDefinitionFull> = InterfaceDecoderFull().load(cache)

        fun scriptIds(): List<Int> = cache.archives(Index.CLIENT_SCRIPTS).toList()

        fun scriptBytes(id: Int): ByteArray? = cache.data(Index.CLIENT_SCRIPTS, id, 0)

        fun script(id: Int): ClientScriptDefinition? {
            val data = scriptBytes(id) ?: return null
            val definition = ClientScriptDefinition(id)
            ClientScriptDecoder().readLoop(definition, ArrayReader(data))
            return definition
        }

        fun enumBytes(id: Int): ByteArray? = cache.data(Index.ENUMS, id ushr 8, id and 0xff)

        fun enumIds(): List<Int> = cache.archives(Index.ENUMS).flatMap { archive -> cache.files(Index.ENUMS, archive).map { (archive shl 8) or it } }

        /** Task structs in task index order */
        fun tasks(): List<StructDefinition> {
            val structs = enums[TASK_STRUCTS].map!!
            return structs.keys.sorted().mapNotNull { this.structs.getOrNull(structs[it] as Int) }
        }

        fun area(task: StructDefinition): Int = task.params?.get(TASK_AREA) as? Int ?: -1

        fun label(id: Int, component: Int): String = interfaces[id].components!!.first { it.id and 0xffff == component }.text
    }

    /** One text to translate: [row] is its id in the table */
    data class Text(val row: String, val kind: String, val english: String)

    /** Every text the translated interfaces can show, in table order; tasks outside [AREAS] only when [all] */
    fun texts(source: Source, all: Boolean = true): List<Text> {
        val texts = mutableListOf<Text>()
        val seen = mutableSetOf<String>()
        fun add(row: String, kind: String, english: String) {
            if (english.isNotEmpty() && seen.add("$kind:$english")) {
                texts.add(Text(row, kind, english))
            }
        }
        for (task in source.tasks()) {
            if (!all && source.area(task) !in AREAS) {
                continue
            }
            val id = task.id
            add("name_$id", "name", task.text(TASK_NAME))
            add("desc_$id", "text", task.text(TASK_DESCRIPTION))
            for ((i, hint) in HINTS.withIndex()) {
                add("hint${i + 1}_$id", "text", task.text(hint))
            }
            add("set_$id", "text", task.text(TASK_SET))
        }
        for ((key, value) in source.enums[DIFFICULTY_NAMES].map!!.toSortedMap()) {
            add("difficulty_$key", "difficulty", value as String)
        }
        for ((key, value) in source.enums[AREA_NAMES].map!!.toSortedMap()) {
            add("area_$key", "area", value as String)
        }
        for ((key, value) in source.enums[LEVEL_UP_MESSAGES].map!!.toSortedMap()) {
            add("level_up_$key", "level_up", value as String)
        }
        for (struct in source.structs) {
            add("prayer_${struct.id}", "prayer", struct.text(PRAYER_DESCRIPTION))
        }
        for ((key, value) in source.enums[MONTHS].map!!.toSortedMap()) {
            add("month_$key", "month", value as String)
        }
        for ((script, english) in scriptTexts(source)) {
            add("script_${script}_${seen.count { it.startsWith("script:") }}", "script", english)
        }
        for ((i, tooltip) in hookTexts(source).withIndex()) {
            add("tooltip_$i", "tooltip", tooltip)
        }
        for ((id, components) in LABELS) {
            for (component in components) {
                // Labels repeat across tabs, so each component gets its own row for the server to find
                texts.add(Text("label_${id}_$component", "label", source.label(id, component)))
            }
        }
        for ((i, chat) in CHAT.withIndex()) {
            add("chat_$i", "chat", chat)
        }
        return texts
    }

    private fun StructDefinition.text(param: Int): String = params?.get(param) as? String ?: ""

    /** The (script, english) of every string constant to translate, checking each still exists */
    fun scriptTexts(source: Source): List<Pair<Int, String>> {
        val texts = mutableListOf<Pair<Int, String>>()
        for (script in WHOLE_SCRIPTS) {
            val definition = source.script(script)!!.original(source)
            for (string in definition.stringOperands.orEmpty().filterNotNull().distinct()) {
                if (string !in SCRIPT_TEXT_KEPT) {
                    texts.add(script to string)
                }
            }
        }
        for (text in SCRIPT_TEXTS) {
            val definition = source.script(text.script)!!.original(source)
            check(definition.stringOperands.orEmpty().contains(text.english)) { "Script ${text.script} no longer has '${text.english}'." }
            texts.add(text.script to text.english)
        }
        return texts
    }

    /** Text the interfaces pass to [HOOK_SCRIPTS]: tooltips and the level up link */
    fun hookTexts(source: Source): List<String> {
        val tooltips = linkedSetOf<String>()
        for (id in INTERFACES.values) {
            for (component in source.interfaces[id].components!!) {
                for (hook in component.hooks()) {
                    if (hook.firstOrNull() in HOOKED) {
                        hook.filterIsInstance<String>().forEach { tooltips.add(it) }
                    }
                }
            }
        }
        return tooltips.toList()
    }

    /** Every script hook of a component, e.g. `[38, -2147483645, 60096601, "Tooltip", 45, 135]` */
    fun InterfaceComponentDefinitionFull.hooks(): List<Array<Any>> = javaClass.declaredFields
        .filter { it.type == Array<Any>::class.java }
        .mapNotNull { field ->
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            field.get(this) as Array<Any>?
        }

    /*
        The table
     */

    data class Row(val id: String, val english: String, val portuguese: String)

    /** Reads `interface_pt_br.tables.toml`, which only ever holds the simple lines `writeTable` produces */
    fun readTable(file: File): List<Row> {
        val rows = mutableListOf<Row>()
        var id: String? = null
        var english: String? = null
        for (line in file.readLines(Charsets.UTF_8)) {
            when {
                line.startsWith("[.") -> id = line.removePrefix("[.").removeSuffix("]")
                id == null -> continue // Column types
                line.startsWith("en = ") -> english = unquote(line.removePrefix("en = "))
                line.startsWith("pt = ") -> rows.add(Row(id, english!!, unquote(line.removePrefix("pt = "))))
            }
        }
        return rows
    }

    private fun unquote(value: String): String = value.removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"")

    /** Rows of one kind of text keyed by their English, e.g. `kind(rows, "name_")` for task names */
    fun kind(rows: List<Row>, vararg prefixes: String) = rows.filter { row -> prefixes.any { row.id.startsWith(it) } }.associate { it.english to it.portuguese }

    /*
        The patch
     */

    object Op {
        const val PUSH_INT = 0
        const val GET_VARP = 1
        const val PUSH_STRING = 3
        const val IF_NE = 7
        const val IF_EQ = 8
        const val RETURN = 21
        const val GET_VARBIT = 25
        const val ILOAD = 33
        const val ISTORE = 34
        const val SLOAD = 35
        const val SSTORE = 36
        const val GOSUB = 40
        const val GET_VARC = 42
        const val ENUM_STRING = 3400
        const val ENUM = 3408
        const val ADD = 4000
        const val SUB = 4001
        const val MUL = 4002
        const val COMPARE = 4107
        const val STRUCT_PARAM = 4500
        val BRANCHES = setOf(6, 7, 8, 9, 10, 31, 32, 51)
    }

    private const val KEY_INT = 105 // 'i'
    private const val VALUE_STRING = 115 // 's'

    /** Scripts and enums an earlier patch added, found by name and by a marker entry */
    private class Ours(source: Source) {
        val scripts = mutableMapOf<Int, ClientScriptDefinition>()
        val enums = mutableListOf<Int>()

        init {
            for (id in source.scriptIds()) {
                val name = scriptName(source.scriptBytes(id) ?: continue)
                if (name.startsWith("pt_br_")) {
                    scripts[id] = source.script(id)!!
                }
            }
            for (id in source.enumIds()) {
                val definition = EnumDefinition(id)
                EnumDecoder().readLoop(definition, ArrayReader(source.enumBytes(id) ?: continue))
                val marker = definition.map?.get(ENUM_MARKER)
                if (marker == "pt_br" || marker == INT_MARKER) {
                    enums.add(id)
                }
            }
        }
    }

    /** Name at the start of a script, read as Latin-1 so translated text never trips the decoder */
    private fun scriptName(data: ByteArray): String = String(data, 0, data.indexOf(0), Charsets.ISO_8859_1)

    private val ours = mutableMapOf<Cache, Ours>()

    private fun ours(source: Source) = ours.getOrPut(source.cache) { Ours(source) }

    /** The script as it was before any patch, undoing the call swaps */
    fun ClientScriptDefinition.original(source: Source): ClientScriptDefinition {
        val ours = ours(source)
        if (ours.scripts.isEmpty() || instructions.none { it == Op.GOSUB }) {
            return this
        }
        val instructions = instructions.toMutableList()
        val ints = (intOperands ?: IntArray(instructions.size)).toMutableList()
        val strings = (stringOperands ?: arrayOfNulls(instructions.size)).toMutableList()
        var i = 0
        while (i < instructions.size) {
            val target = if (instructions[i] == Op.GOSUB) ours.scripts[ints[i]] else null
            when (target?.name) {
                PARAM_TEXT, TASK_TEXT -> {
                    instructions[i] = Op.STRUCT_PARAM
                    ints[i] = 0
                }
                TASK_ENUM -> {
                    instructions[i] = Op.ENUM
                    ints[i] = 0
                }
                ENUM_STRING -> {
                    instructions[i] = Op.ENUM_STRING
                    ints[i] = 0
                }
                STRING -> {
                    instructions[i] = Op.PUSH_STRING
                    ints[i] = 0
                    strings[i] = target.stringOperands!![3]
                }
                TOOLTIP -> {
                    // A prefix hook: sload, gosub, sstore before the original first instruction
                    if (i == 1 && instructions[0] == Op.SLOAD && instructions[2] == Op.SSTORE) {
                        repeat(3) {
                            instructions.removeAt(0)
                            ints.removeAt(0)
                            strings.removeAt(0)
                        }
                        i = 0
                        continue
                    }
                    check(instructions.none { it in Op.BRANCHES }) { "Can't remove an instruction from script $id with jumps." }
                    instructions.removeAt(i)
                    ints.removeAt(i)
                    strings.removeAt(i)
                    continue
                }
            }
            i++
        }
        return copy(instructions = instructions.toIntArray(), intOperands = ints.toIntArray(), stringOperands = strings.toTypedArray())
    }

    private class Builder(val name: String) {
        val instructions = mutableListOf<Int>()
        val ints = mutableListOf<Int>()
        val strings = mutableListOf<String?>()

        fun op(op: Int, operand: Int = 0) {
            instructions.add(op)
            ints.add(operand)
            strings.add(null)
        }

        fun string(value: String) {
            instructions.add(Op.PUSH_STRING)
            ints.add(0)
            strings.add(value)
        }

        /** A branch to be pointed at a later instruction with [land] */
        fun branch(op: Int): Int {
            op(op)
            return instructions.lastIndex
        }

        fun land(branch: Int) {
            ints[branch] = instructions.size - branch - 1
        }

        fun languageIs(portuguese: Boolean): Int {
            op(Op.GET_VARC, LANGUAGE_VARC)
            op(Op.PUSH_INT, 1)
            return branch(if (portuguese) Op.IF_EQ else Op.IF_NE)
        }

        fun build(id: Int, intArgs: Int = 0, stringArgs: Int = 0, intVars: Int = intArgs, stringVars: Int = stringArgs) = ClientScriptDefinition(
            id = id,
            name = name,
            intArgumentCount = intArgs,
            stringArgumentCount = stringArgs,
            intVariableCount = intVars,
            stringVariableCount = stringVars,
            instructions = instructions.toIntArray(),
            intOperands = ints.toIntArray(),
            stringOperands = strings.toTypedArray(),
        )
    }

    /**
     * (struct, param) -> struct_param, or when the language varc is 1 the struct's entry in the Portuguese enum
     * [paramEnums] gives for the param, if it has one.
     */
    private fun paramText(id: Int, paramEnums: Int) = Builder(PARAM_TEXT).apply {
        val english = languageIs(portuguese = false)
        op(Op.PUSH_INT, KEY_INT)
        op(Op.PUSH_INT, KEY_INT)
        op(Op.PUSH_INT, paramEnums)
        op(Op.ILOAD, 1)
        op(Op.ENUM)
        op(Op.ISTORE, 2)
        op(Op.ILOAD, 2)
        op(Op.PUSH_INT, -1)
        val untranslated = branch(Op.IF_EQ)
        op(Op.PUSH_INT, KEY_INT)
        op(Op.PUSH_INT, VALUE_STRING)
        op(Op.ILOAD, 2)
        op(Op.ILOAD, 0)
        op(Op.ENUM)
        op(Op.SSTORE, 0)
        op(Op.SLOAD, 0)
        string("")
        op(Op.COMPARE)
        op(Op.PUSH_INT, 0)
        val empty = branch(Op.IF_EQ)
        op(Op.SLOAD, 0)
        op(Op.RETURN)
        land(english)
        land(untranslated)
        land(empty)
        op(Op.ILOAD, 0)
        op(Op.ILOAD, 1)
        op(Op.STRUCT_PARAM)
        op(Op.RETURN)
    }.build(id, intArgs = 2, intVars = 3, stringVars = 1)

    /** (key type, value type, enum, key) -> enum, reading the Portuguese copy of [copies] when the language varc is 1 */
    private fun taskEnum(id: Int, copies: Map<Int, Int>) = Builder(TASK_ENUM).apply {
        val english = languageIs(portuguese = false)
        for ((original, copy) in copies) {
            op(Op.ILOAD, 2)
            op(Op.PUSH_INT, original)
            val other = branch(Op.IF_NE)
            op(Op.PUSH_INT, copy)
            op(Op.ISTORE, 2)
            land(other)
        }
        land(english)
        op(Op.ILOAD, 0)
        op(Op.ILOAD, 1)
        op(Op.ILOAD, 2)
        op(Op.ILOAD, 3)
        op(Op.ENUM)
        op(Op.RETURN)
    }.build(id, intArgs = 4)

    /** (enum, key) -> enum_string, reading the Portuguese copy of [copies] when the language varc is 1 */
    private fun enumString(id: Int, copies: Map<Int, Int>) = Builder(ENUM_STRING).apply {
        val english = languageIs(portuguese = false)
        for ((original, copy) in copies) {
            op(Op.ILOAD, 0)
            op(Op.PUSH_INT, original)
            val other = branch(Op.IF_NE)
            op(Op.PUSH_INT, copy)
            op(Op.ISTORE, 0)
            land(other)
        }
        land(english)
        op(Op.ILOAD, 0)
        op(Op.ILOAD, 1)
        op(Op.ENUM_STRING)
        op(Op.RETURN)
    }.build(id, intArgs = 2)

    /** () -> [english] or [portuguese]; the English must stay the 4th instruction so the patch can be undone */
    private fun string(id: Int, english: String, portuguese: String) = Builder(STRING).apply {
        val translated = languageIs(portuguese = true)
        string(english)
        op(Op.RETURN)
        land(translated)
        string(portuguese)
        op(Op.RETURN)
    }.build(id)

    /** (text) -> its translation when the language varc is 1 and it's one of [tooltips] */
    private fun tooltip(id: Int, tooltips: Map<String, String>) = Builder(TOOLTIP).apply {
        val translate = languageIs(portuguese = true)
        op(Op.SLOAD, 0)
        op(Op.RETURN)
        land(translate)
        for ((english, portuguese) in tooltips) {
            op(Op.SLOAD, 0)
            string(english)
            op(Op.COMPARE)
            op(Op.PUSH_INT, 0)
            val other = branch(Op.IF_NE)
            string(portuguese)
            op(Op.RETURN)
            land(other)
        }
        op(Op.SLOAD, 0)
        op(Op.RETURN)
    }.build(id, stringArgs = 1)

    /** Everything a patch writes, keyed by id, what it removes, and where it put things */
    class Patch(
        val scripts: Map<Int, ClientScriptDefinition>,
        val enums: Map<Int, EnumDefinition>,
        val removeScripts: Set<Int>,
        val removeEnums: Set<Int>,
        val paramText: Int,
        /** Text param to the enum holding its Portuguese, keyed by struct */
        val paramEnums: Map<Int, Int>,
        val taskEnum: Int,
        val enumString: Int,
        val tooltip: Int,
    )

    /** Works out the patched scripts and enums for [rows] from the original cache, without writing anything */
    fun patch(source: Source, rows: List<Row>): Patch {
        val ours = ours(source)
        val portuguese = rows.associate { it.id to it.portuguese }
        // Texts shared between params (a hint that's also a description) have one row, so these look up by kind
        val lookups = mapOf(
            "name_" to kind(rows, "name_"),
            "text" to kind(rows, "desc_", "hint", "set_"),
            "prayer_" to kind(rows, "prayer_"),
        )
        val scriptStrings = kind(rows, "script_")
        val tooltipTexts = kind(rows, "tooltip_")

        // Fresh ids after the original cache
        var nextScript = source.scriptIds().filter { it !in ours.scripts }.max() + 1
        var nextEnum = source.enumIds().filter { it !in ours.enums }.max() + 1
        val paramEnum = nextEnum++
        val areaEnum = nextEnum++
        val difficultyEnum = nextEnum++
        val levelUpEnum = nextEnum++
        val monthEnum = nextEnum++
        val paramText = nextScript++
        val taskEnum = nextScript++
        val tooltip = nextScript++
        val enumString = nextScript++

        val enums = mutableMapOf<Int, EnumDefinition>()
        val copies = listOf(
            Triple(areaEnum, AREA_NAMES, "area"),
            Triple(difficultyEnum, DIFFICULTY_NAMES, "difficulty"),
            Triple(levelUpEnum, LEVEL_UP_MESSAGES, "level_up"),
            Triple(monthEnum, MONTHS, "month"),
        )
        for ((copy, original, prefix) in copies) {
            val enum = source.enums[original]
            val map = enum.map!!.mapValues { (key, value) -> portuguese["${prefix}_$key"] ?: value }.toMutableMap()
            map[ENUM_MARKER] = "pt_br"
            enums[copy] = enum.copy(id = copy, length = map.size, map = map)
        }
        // One enum per text param, struct id to Portuguese, and [paramEnum] from param to that enum
        val paramEnums = mutableMapOf<Int, Int>()
        for ((param, prefix) in STRUCT_TEXTS) {
            val lookup = lookups[prefix] ?: lookups.getValue("text")
            val texts = mutableMapOf<Int, Any>(ENUM_MARKER to "pt_br")
            for (struct in source.structs) {
                val english = struct.text(param)
                if (english.isNotEmpty()) {
                    texts[struct.id] = lookup[english] ?: continue
                }
            }
            if (texts.size > 1) {
                val id = nextEnum++
                enums[id] = EnumDefinition(id = id, keyType = 'i', valueType = 's', defaultString = "", length = texts.size, map = texts)
                paramEnums[param] = id
            }
        }
        val map = paramEnums.toMutableMap<Int, Any>()
        map[ENUM_MARKER] = INT_MARKER
        enums[paramEnum] = EnumDefinition(id = paramEnum, keyType = 'i', valueType = 'i', defaultInt = -1, length = map.size, map = map)

        val scripts = mutableMapOf<Int, ClientScriptDefinition>()
        scripts[paramText] = paramText(paramText, paramEnum)
        scripts[taskEnum] = taskEnum(taskEnum, mapOf(AREA_NAMES to areaEnum, DIFFICULTY_NAMES to difficultyEnum, MONTHS to monthEnum))
        scripts[enumString] = enumString(enumString, mapOf(LEVEL_UP_MESSAGES to levelUpEnum))
        val tooltips = hookTexts(source).associateWith { tooltipTexts[it] }.filterValues { it != null }.mapValues { it.value!! }
        scripts[tooltip] = tooltip(tooltip, tooltips)

        // One string script per translated constant
        val stringScripts = mutableMapOf<Pair<String, String>, Int>()
        val wanted = mutableMapOf<Int, MutableList<ScriptText>>()
        for ((script, english) in scriptTexts(source)) {
            val text = SCRIPT_TEXTS.firstOrNull { it.script == script && it.english == english } ?: ScriptText(script, english)
            wanted.getOrPut(script) { mutableListOf() }.add(text)
        }

        for (id in source.scriptIds()) {
            if (id in ours.scripts) {
                continue
            }
            val current = source.script(id) ?: continue
            val original = current.original(source)
            val instructions = original.instructions.copyOf()
            val ints = (original.intOperands ?: IntArray(instructions.size)).copyOf()
            val strings = (original.stringOperands ?: arrayOfNulls(instructions.size)).copyOf()
            var changed = false
            for (i in instructions.indices) {
                // Task texts: push_int <param>, struct_param
                if (instructions[i] == Op.STRUCT_PARAM && i > 0 && instructions[i - 1] == Op.PUSH_INT && ints[i - 1] in TEXT_PARAMS) {
                    instructions[i] = Op.GOSUB
                    ints[i] = paramText
                    changed = true
                }
                // Area, difficulty and month names: push_int 'i', push_int 's', push_int <enum>, <key>, enum
                if (instructions[i] == Op.PUSH_INT &&
                    ints[i] in setOf(AREA_NAMES, DIFFICULTY_NAMES, MONTHS) &&
                    i > 1 &&
                    instructions[i - 1] == Op.PUSH_INT &&
                    ints[i - 1] == VALUE_STRING &&
                    instructions[i - 2] == Op.PUSH_INT &&
                    ints[i - 2] == KEY_INT
                ) {
                    var j = i + 1
                    while (instructions[j] != Op.ENUM) {
                        check(instructions[j] in setOf(Op.PUSH_INT, Op.ILOAD, Op.GET_VARBIT, Op.GET_VARP, Op.GET_VARC, Op.STRUCT_PARAM)) {
                            "Unexpected key for enum ${ints[i]} in script $id at $j."
                        }
                        j++
                    }
                    instructions[j] = Op.GOSUB
                    ints[j] = taskEnum
                    changed = true
                }
                // Level up messages: push_int <enum>, <key>, enum_string
                if (instructions[i] == Op.ENUM_STRING &&
                    i > 1 &&
                    instructions[i - 2] == Op.PUSH_INT &&
                    ints[i - 2] == LEVEL_UP_MESSAGES &&
                    instructions[i - 1] in setOf(Op.PUSH_INT, Op.ILOAD, Op.GET_VARBIT, Op.GET_VARP, Op.GET_VARC)
                ) {
                    instructions[i] = Op.GOSUB
                    ints[i] = enumString
                    changed = true
                }
            }
            for (text in wanted[id].orEmpty()) {
                val pt = scriptStrings[text.english] ?: continue
                val call = stringScripts.getOrPut(text.english to pt) { nextScript++ }
                var occurrence = 0
                var replaced = false
                for (i in instructions.indices) {
                    if (instructions[i] == Op.PUSH_STRING && strings[i] == text.english) {
                        if (text.occurrence == null || text.occurrence == occurrence) {
                            instructions[i] = Op.GOSUB
                            ints[i] = call
                            strings[i] = null
                            replaced = true
                        }
                        occurrence++
                    }
                }
                check(replaced) { "Script $id no longer has '${text.english}'." }
                changed = true
            }
            if (!changed) {
                continue
            }
            check(original.stringOperands.orEmpty().filterNotNull().all { s -> s.all { it.code < 0x80 } }) {
                "Script $id has text outside ASCII, which the decoder would change when written back."
            }
            scripts[id] = original.copy(instructions = instructions, intOperands = ints, stringOperands = strings)
        }

        // Text hooks pass in: translated right after the script loads its string argument
        for ((id, shape) in HOOK_SCRIPTS) {
            val (expected, at) = shape
            val generic = source.script(id)!!.original(source)
            check(generic.instructions.contentEquals(expected)) { "Hook script $id changed." }
            scripts[id] = generic.copy(
                instructions = generic.instructions.toMutableList().apply { add(at, Op.GOSUB) }.toIntArray(),
                intOperands = generic.intOperands!!.toMutableList().apply { add(at, tooltip) }.toIntArray(),
                stringOperands = (generic.stringOperands ?: arrayOfNulls(generic.instructions.size)).toMutableList().apply { add(at, null) }.toTypedArray(),
            )
        }

        for ((id, shape) in PREFIX_HOOK_SCRIPTS) {
            val (size, argument) = shape
            val generic = source.script(id)!!.original(source)
            check(generic.instructions.size == size) { "Hook script $id changed." }
            scripts[id] = generic.copy(
                instructions = (intArrayOf(Op.SLOAD, Op.GOSUB, Op.SSTORE) + generic.instructions),
                intOperands = (intArrayOf(argument, tooltip, argument) + generic.intOperands!!),
                stringOperands = arrayOf<String?>(null, null, null) + (generic.stringOperands ?: arrayOfNulls(generic.instructions.size)),
            )
        }

        for ((key, id) in stringScripts) {
            scripts[id] = string(id, key.first, key.second)
        }
        return Patch(
            scripts = scripts,
            enums = enums,
            removeScripts = ours.scripts.keys - scripts.keys,
            removeEnums = ours.enums.toSet() - enums.keys,
            paramText = paramText,
            paramEnums = paramEnums,
            taskEnum = taskEnum,
            enumString = enumString,
            tooltip = tooltip,
        )
    }

    fun encode(script: ClientScriptDefinition): ByteArray {
        val writer = ArrayWriter(4096 + script.instructions.size * 8)
        with(ClientScriptEncoder()) { writer.encode(script) }
        return writer.toArray()
    }

    fun encode(enum: EnumDefinition): ByteArray {
        val writer = ArrayWriter(1024 + enum.length * 256)
        with(EnumEncoder()) { writer.encode(enum) }
        return writer.toArray()
    }

    /** Scripts and enums whose bytes in the cache differ from [patch], i.e. empty when the cache is up to date */
    fun differences(source: Source, patch: Patch): List<String> {
        val differences = mutableListOf<String>()
        for ((id, script) in patch.scripts) {
            if (!encode(script).contentEquals(source.scriptBytes(id))) {
                differences.add("script $id")
            }
        }
        for ((id, enum) in patch.enums) {
            if (!encode(enum).contentEquals(source.enumBytes(id))) {
                differences.add("enum $id")
            }
        }
        patch.removeScripts.mapTo(differences) { "old script $it" }
        patch.removeEnums.mapTo(differences) { "old enum $it" }
        return differences
    }
}
