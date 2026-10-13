"""Generates dialogue_pt_br.tables.toml from the Tutorial Island scripts.

The `en` column is taken from the Kotlin source so it matches exactly; this file
only holds the Portuguese side. Fails if any tutorial line lacks a translation.
Usage:
  python3 -I tools/translation/gen_dialogue.py <repo root> tools/translation/sources          # writes the table
  python3 -I tools/translation/gen_dialogue.py <repo root> tools/translation/sources --dump   # lists untranslated lines in sources/missing.json
"""
import glob
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.dont_write_bytecode = True
import skill_text  # noqa: E402

ROOT = sys.argv[1]
KOTLIN = ROOT + "/game/src/main/kotlin/"
SRC = KOTLIN + "content/area/misthalin/tutorial_island/"
TRANSLATIONS = sys.argv[2] if len(sys.argv) > 2 else None
OUT = ROOT + "/data/entity/player/dialogue/translation/dialogue_pt_br.tables.toml"

LIT = r'"((?:[^"\\$]|\\.|\$\{[^}]*\}|\$[a-zA-Z_][a-zA-Z0-9_.]*|\$)*)"'
PATTERNS = [
    # The npc id may be a literal, a variable or a named argument: npc<Happy>(target.id, "...") / npc<Happy>(npcId = "x", "...")
    re.compile(r'\b(?:npc|player)(?:<\w+>)?\(\s*(?:(?:npcId\s*=\s*)?(?:"[a-z0-9_]+"|[A-Za-z_][\w.]*)\s*,\s*)?' + LIT),
    re.compile(r'\bstatement\(\s*' + LIT),
    re.compile(r'\bchoice\(\s*' + LIT),
    re.compile(r'\boption(?:<\w+>)?\(\s*' + LIT),
    re.compile(r'\bitem\(\s*"[a-z0-9_]+"\s*,\s*' + LIT),
    re.compile(r'\bitems\(\s*"[a-z0-9_]+"\s*,\s*"[a-z0-9_]+"\s*,\s*' + LIT),
    # Combat tutors pass the npc's follow-up question through their own menu(followUp) helper
    re.compile(r'\bmenu\(\s*' + LIT),
]
MAX_BYTES = 500
KINDS = ["npc or player line", "statement", "choice title", "choice option", "item box", "item box", "npc line (menu helper)"]

# (file, row prefix, header comment)
NPCS = [
    ("RunescapeGuide", "guide", "RuneScape Guide"),
    ("SurvivalExpert", "survival_expert", "Survival Expert"),
    ("MasterChef", "master_chef", "Master Chef"),
    ("QuestGuide", "quest_guide", "Quest Guide"),
    ("MiningInstructor", "mining_instructor", "Mining Instructor"),
    ("CombatInstructor", "combat_instructor", "Combat Instructor"),
    ("FinancialAdvisor", "financial_advisor", "Financial Advisor"),
    ("BrotherBrace", "brother_brace", "Brother Brace"),
    ("MagicInstructor", "magic_instructor", "Magic Instructor"),
    # Shared scripts used by NPCs on the island, found by matching island spawns against every handler
    ("../../../entity/npc/Banker", "banker", "Banker - every banker, including on Tutorial Island"),
] + ([
    (path, path.split("/")[-1].lower(), f"{path.split('/')[-1]} - Lumbridge")
    for path in json.load(open(sys.argv[2] + "/lumbridge_scripts.json"))
] if len(sys.argv) > 2 else [])

# Lines built from variables at runtime, written with the same %server% placeholder the table uses
VARIABLES = {
    'Welcome to ${Settings["server.name"]}.': "Welcome to %server%.",
    'This is a branch of the Bank of ${Settings["server.name"]}. We have branches in many towns.': "This is a branch of the Bank of %server%. We have branches in many towns.",
}

PT = {
    # RuneScape Guide
    "Greetings! I see you are a new arrival to this land. My job is to teach you a few basic skills and functions.":
        "Saudações! Vejo que você acabou de chegar a estas terras. Meu trabalho é ensinar algumas habilidades e funções básicas.",
    "As you wish. I'll send you on your way with the usual supplies.":
        "Como quiser. Vou mandar você seguir viagem com os suprimentos de sempre.",
    "The options panel lets you change the screen brightness, the volume of the music and sound effects, and whether other players may offer you help.":
        "O painel de opções permite mudar o brilho da tela, o volume da música e dos efeitos sonoros, e se outros jogadores podem oferecer ajuda a você.",
    "That's all I have to teach you. Go through that door and the Survival Expert will show you how to look after yourself.":
        "Isso é tudo o que tenho para ensinar. Passe por aquela porta e o Survival Expert vai mostrar como cuidar de si mesmo.",
    "Thanks, I'll do that.":
        "Obrigado, vou fazer isso.",
    "You've learnt all I have to teach. Follow the arrow to your next instructor.":
        "Você já aprendeu tudo o que eu tinha para ensinar. Siga a seta até o seu próximo instrutor.",
    "Welcome to %server%.":
        "Bem-vindo a %server%.",
    "First we shall go through some of the game's control panels, which you can find at the bottom right of your screen.":
        "Primeiro vamos ver alguns dos painéis de controle do jogo, que ficam no canto inferior direito da tela.",
    "Click on the flashing spanner icon to open your game options.":
        "Clique no ícone de chave inglesa piscando para abrir as opções do jogo.",
    "Welcome to Lumbridge! To get more help, simply click on the Lumbridge Guide or one of the Tutors - these can be found by looking for the question mark icon on your minimap.":
        "Bem-vindo a Lumbridge! Para mais ajuda, clique no Lumbridge Guide ou em um dos Tutors - eles aparecem com o ícone de ponto de interrogação no seu minimapa.",
    "Do you want to go through the tutorial?":
        "Você quer fazer o tutorial?",
    "Yes, show me how to play.":
        "Sim, me mostre como jogar.",
    "No, send me to the mainland.":
        "Não, me mande para o continente.",

    # Survival Expert
    "Hello there! I'm here to teach you how to survive out in the wilds.":
        "Olá! Estou aqui para te ensinar a sobreviver na natureza selvagem.",
    "Take this hatchet and tinderbox. Chop down one of these trees, then use the tinderbox on the logs to light a fire.":
        "Pegue este machado (hatchet) e esta pederneira (tinderbox). Corte uma destas árvores e depois use a pederneira nas toras (logs) para acender uma fogueira.",
    "The Survival Expert gives you a bronze hatchet and a tinderbox.":
        "O Survival Expert te dá um machado de bronze (bronze hatchet) e uma pederneira (tinderbox).",
    "Well done! Now let's try some fishing.":
        "Muito bem! Agora vamos tentar pescar.",
    "Take this small fishing net and use it on the fishing spots in the pond. Then cook what you catch on your fire.":
        "Pegue esta rede de pesca pequena (small fishing net) e use-a nos pontos de pesca do lago. Depois cozinhe o que pescar na sua fogueira.",
    "The Survival Expert gives you a small fishing net.":
        "O Survival Expert te dá uma rede de pesca pequena (small fishing net).",
    "Lost your equipment? Here, take another.":
        "Perdeu seu equipamento? Tome, pegue outro.",
    "Chop a tree with your hatchet for logs, then use your tinderbox on them to light a fire.":
        "Corte uma árvore com o machado para conseguir toras e depois use a pederneira nelas para acender uma fogueira.",
    "Use your net on a fishing spot to catch shrimp, and cook them on a fire once you've caught some.":
        "Use a rede num ponto de pesca para pegar camarões (shrimp) e cozinhe-os numa fogueira depois de pescar alguns.",
    "Your skills panel shows how much experience each of those has earned you.":
        "O seu painel de habilidades (skills) mostra quanta experiência cada uma dessas atividades já te deu.",

    # Master Chef
    "Hello there! I'm the cook. There's more to cooking than throwing a fish on a fire, you know.":
        "Olá! Eu sou o cozinheiro. Sabe, cozinhar é mais do que jogar um peixe no fogo.",
    "Take this pot of flour and bucket of water. Use them together to make dough, then cook the dough on my range.":
        "Pegue este pote de farinha (pot of flour) e este balde de água (bucket of water). Use um no outro para fazer massa (dough) e depois asse a massa no meu fogão (range).",
    "Use the bucket of water on the pot of flour to make dough, then cook the dough on my range.":
        "Use o balde de água no pote de farinha para fazer massa e depois asse a massa no meu fogão.",
    "The music player lists every tune you've unlocked. More open up as you explore the world.":
        "O tocador de música lista todas as músicas que você já desbloqueou. Mais músicas se abrem conforme você explora o mundo.",
    "You've got your dough. Cook it on the range over there.":
        "Você já tem a sua massa. Asse no fogão ali.",
    "Lost your ingredients? Not to worry, here's some more.":
        "Perdeu os ingredientes? Não se preocupe, aqui tem mais.",
    "The cook gives you a pot of flour.":
        "O cozinheiro te dá um pote de farinha (pot of flour).",
    "The cook gives you a bucket of water.":
        "O cozinheiro te dá um balde de água (bucket of water).",
    "The cook gives you a pot of flour and a bucket of water.":
        "O cozinheiro te dá um pote de farinha e um balde de água.",

    # Quest Guide
    "Greetings, traveller. I am the Quest Guide, and I'm here to tell you about quests.":
        "Saudações, viajante. Eu sou o Quest Guide e estou aqui para te falar sobre as missões (quests).",
    "Click on the flashing question mark icon to open your quest journal. It lists every quest, and how far through each one you are.":
        "Clique no ícone de ponto de interrogação piscando para abrir o seu diário de missões (Quest Journal). Ele lista todas as missões e o quanto você já avançou em cada uma.",
    "Quests are set by people all over the world. Completing them earns you rewards and quest points.":
        "As missões são dadas por pessoas do mundo todo. Completá-las te dá recompensas e pontos de missão (quest points).",
    "That's everything from me. Climb down the ladder to meet the Mining Instructor.":
        "Isso é tudo da minha parte. Desça a escada para encontrar o Mining Instructor.",
    "Check your quest journal whenever you want to know what to do next.":
        "Consulte o seu diário de missões sempre que quiser saber o que fazer em seguida.",

    # Mining Instructor
    "Hello there. This is where we teach Mining and Smithing.":
        "Olá. É aqui que ensinamos Mining e Smithing (mineração e forja).",
    "Prospect those rocks to find out what ore they hold. Start with the tin, then the copper.":
        "Use 'Prospect' naquelas rochas para descobrir que minério elas têm. Comece pelo estanho (tin) e depois o cobre (copper).",
    "Now you know what's in them, you'll want to get it out. Take this pickaxe and mine some tin and some copper.":
        "Agora que você sabe o que tem nelas, vai querer tirar de lá. Pegue esta picareta (pickaxe) e minere um pouco de estanho e de cobre.",
    "The Mining Instructor gives you a bronze pickaxe.":
        "O Mining Instructor te dá uma picareta de bronze (bronze pickaxe).",
    "A fine bronze bar. Now take this hammer and use the bar on the anvil to smith a dagger.":
        "Uma bela barra de bronze. Agora pegue este martelo (hammer) e use a barra na bigorna (anvil) para forjar uma adaga (dagger).",
    "The Mining Instructor gives you a hammer.":
        "O Mining Instructor te dá um martelo (hammer).",
    "Lost your tools? Here's a replacement.":
        "Perdeu suas ferramentas? Aqui estão outras.",
    "Prospecting a rock tells you which ore it holds. Mining it with a pickaxe gets you the ore itself.":
        "Usar 'Prospect' numa rocha mostra que minério ela tem. Minerar com a picareta te dá o minério em si.",
    "Use the ore on the furnace to smelt it into a bar - copper and tin together make bronze.":
        "Use o minério na fornalha (furnace) para fundir uma barra - cobre e estanho juntos formam bronze.",
    "Then use the bar on an anvil while carrying a hammer, and choose what you'd like to smith.":
        "Depois use a barra numa bigorna levando um martelo e escolha o que quer forjar.",

    # Combat Instructor
    "Hello there! Ready to learn how to fight?":
        "Olá! Pronto para aprender a lutar?",
    "First you'll need to wield a weapon. Open your worn equipment tab and take a look at what you're carrying.":
        "Primeiro você precisa empunhar uma arma. Abra a aba de equipamento vestido (worn equipment) e veja o que você está carregando.",
    "Good. A dagger is quick, but a sword hits harder and a shield keeps you alive. Take these and equip them both.":
        "Bom. Uma adaga é rápida, mas uma espada bate mais forte e um escudo te mantém vivo. Pegue estes e equipe os dois.",
    "The Combat Instructor gives you a bronze sword and a wooden shield.":
        "O Combat Instructor te dá uma espada de bronze (bronze sword) e um escudo de madeira (wooden shield).",
    "Nicely done. Not every fight should be up close, though.":
        "Muito bem. Mas nem toda luta precisa ser de perto.",
    "Take this shortbow and these arrows, then kill another rat from a distance.":
        "Pegue este arco curto (shortbow) e estas flechas (arrows) e mate outro rato à distância.",
    "The Combat Instructor gives you a shortbow and some arrows.":
        "O Combat Instructor te dá um arco curto (shortbow) e algumas flechas (arrows).",
    "You'll not get far unarmed. Take these.":
        "Desarmado você não vai longe. Pegue isto.",
    "Keep practising. The rats in the cage won't hurt you.":
        "Continue praticando. Os ratos da jaula não vão te machucar.",

    # Financial Advisor
    "Hello, and welcome to the bank of the future!":
        "Olá, e bem-vindo ao banco do futuro!",
    "Your money pouch carries your coins for you, so you'll never need to make room for them in your backpack.":
        "A sua bolsa de dinheiro (money pouch) guarda as suas moedas, então você nunca vai precisar abrir espaço para elas na mochila.",
    "Head through the next door to meet Brother Brace.":
        "Passe pela próxima porta para encontrar o Brother Brace.",
    "Look after your coins and they'll look after you.":
        "Cuide das suas moedas e elas cuidarão de você.",

    # Brother Brace
    "Hello there, and welcome to the church. I'm here to tell you about Prayer.":
        "Olá, e bem-vindo à igreja. Estou aqui para te falar sobre Prayer (oração).",
    "Open your prayer list and take a look at what's available to you.":
        "Abra a sua lista de orações (prayer list) e veja o que está disponível para você.",
    "Prayers drain your prayer points while they're active. Bury bones or pray at an altar to restore them.":
        "As orações gastam seus pontos de oração enquanto estão ativas. Enterre ossos ou reze num altar para recuperá-los.",
    "Now let me show you the friends list. Open it and you'll see who's online.":
        "Agora deixe eu te mostrar a lista de amigos (friends list). Abra e você verá quem está online.",
    "Your ignore list works the same way, but for people you'd rather not hear from.":
        "A sua lista de ignorados (ignore list) funciona do mesmo jeito, mas para quem você prefere não ouvir.",
    "That's me done. Head out of the church and speak to the Magic Instructor.":
        "Terminei. Saia da igreja e fale com o Magic Instructor.",
    "May Saradomin watch over you.":
        "Que Saradomin olhe por você.",

    # Magic Instructor
    "Hello.":
        "Olá.",
    "Good day, newcomer. My name is Terrova. I'm here to tell you about Magic. Let's start by opening your spellbook.":
        "Bom dia, recém-chegado. Meu nome é Terrova. Estou aqui para te falar sobre Magic (magia). Vamos começar abrindo o seu livro de feitiços (spellbook).",
    "Good. This is a list of your spells. Currently you can only cast one offensive spell called Wind Strike. Let's try it out on one of those chickens.":
        "Bom. Esta é a lista dos seus feitiços. Por enquanto você só pode lançar um feitiço de ataque, chamado Wind Strike. Vamos testá-lo numa daquelas galinhas.",
    "Terrova gives you five <blue>air runes</col> and five <blue>mind runes</col>!":
        "Terrova te dá cinco <blue>air runes</col> (runas de ar) e cinco <blue>mind runes</col> (runas da mente)!",
    "Out of runes? Take some more.":
        "Acabaram as runas? Pegue mais algumas.",
    "Cast Wind Strike on a chicken to finish your training.":
        "Lance Wind Strike numa galinha para terminar o seu treino.",
    "Well you're all finished here now. I'll give you a reasonable number of runes when you leave.":
        "Bem, você terminou tudo aqui. Vou te dar uma boa quantidade de runas quando você sair.",
    "Do you want to go to the mainland?":
        "Você quer ir para o continente?",
    "Yes.":
        "Sim.",
    "No.":
        "Não.",
    "That's fine. Talk to me again whenever you're ready.":
        "Tudo bem. Fale comigo de novo quando estiver pronto.",
    "Yes, I'm ready to leave.":
        "Sim, estou pronto para partir.",
    "Good good. I've deactivated the protective spells around the island, so now you can teleport yourself out of here.":
        "Ótimo, ótimo. Desativei os feitiços de proteção em volta da ilha, então agora você pode se teletransportar daqui.",
    "When you get to the mainland you will find yourself in the town of Lumbridge. If you want some ideas on where to go next, talk to my friend Phileas, also known as the Lumbridge Guide. You can't miss him; he's holding a big staff with a question mark on the end.":
        "Quando chegar ao continente, você vai estar na cidade de Lumbridge. Se quiser ideias de para onde ir depois, fale com o meu amigo Phileas, também conhecido como Lumbridge Guide. Não tem como não vê-lo: ele segura um grande cajado com um ponto de interrogação na ponta.",
    "He also has a white beard and carries a rucksack full of scrolls. There are also tutors willing to teach you about the many skills you could learn.":
        "Ele também tem barba branca e carrega uma mochila cheia de pergaminhos. Também há tutores dispostos a te ensinar as muitas habilidades que você pode aprender.",
    "When you get to Lumbridge, look for this icon on your minimap. The Lumbridge Guide and the other tutors will be standing near one of these.":
        "Quando chegar a Lumbridge, procure este ícone no seu minimapa. O Lumbridge Guide e os outros tutores vão estar perto de um destes.",
    "The Lumbridge Guide should be standing slightly to the north-east of the castle's courtyard and the others you will find scattered around Lumbridge.":
        "O Lumbridge Guide deve estar um pouco a nordeste do pátio do castelo, e os outros você vai encontrar espalhados por Lumbridge.",
    "If all else fails, visit the RuneScape website for a whole chestload of information on quests, skills and minigames as well as a very good starter's guide.":
        "Se nada mais der certo, visite o site do RuneScape para um baú cheio de informações sobre missões, habilidades e minijogos, além de um ótimo guia para iniciantes.",

    # Banker
    "Good day. How may I help you?":
        "Bom dia. Como posso ajudar?",
    "Before we go any further, I should inform you that an item you lent out has been returned to you.":
        "Antes de continuarmos, devo informar que um item que você emprestou foi devolvido a você.",
    "Before we go any further, I should inform you that you have items ready for collection from the Grand Exchange.":
        "Antes de continuarmos, devo informar que você tem itens prontos para retirada no Grand Exchange.",
    "I'd like to access my bank account, please.":
        "Gostaria de acessar minha conta no banco, por favor.",
    "I'd like to check my PIN settings.":
        "Gostaria de ver as configurações do meu PIN.",
    "I'd like to see my collection box.":
        "Gostaria de ver minha caixa de coleta (collection box).",
    "I'd like to see my Returned Items box.":
        "Gostaria de ver minha caixa de itens devolvidos (Returned Items).",
    "What is this place?":
        "Que lugar é este?",
    "This is a branch of the Bank of %server%. We have branches in many towns.":
        "Esta é uma agência do Banco de %server%. Temos agências em muitas cidades.",
    "And what do you do?":
        "E o que vocês fazem?",
    "We will look after your items and money for you. Leave your valuables with us if you want to keep them safe.":
        "Nós cuidamos dos seus itens e do seu dinheiro. Deixe seus objetos de valor conosco se quiser mantê-los em segurança.",
    "Didn't you used to be called the Bank of Varrock?":
        "Vocês não se chamavam Banco de Varrock?",
    "Yes we did, but people kept on coming into our branches outside of Varrock and telling us that our signs were wrong. They acted as if we didn't know what town we were in or something.":
        "Sim, mas as pessoas viviam entrando nas nossas agências fora de Varrock dizendo que as nossas placas estavam erradas. Agiam como se não soubéssemos em que cidade estávamos.",
}

if TRANSLATIONS:
    # pt_tasks*/pt_level_up*/pt_labels*/pt_ui* belong to the interfaces the client draws from the cache (tools/.../InterfaceTranslation.kt)
    # pt_*_runtime.json hold text only seen while playing (SkillActionTranslationTest, BankPinTranslationTest), written as is
    for file in sorted(f for f in glob.glob(TRANSLATIONS + "/pt_*.json") if not f.split("/")[-1].startswith(("pt_tasks", "pt_level_up", "pt_labels", "pt_ui")) and not f.endswith("_runtime.json")):
        PT.update({en: pt for en, pt in json.load(open(file)).items() if "\n" not in en})

# Text shared across the game rather than said by one NPC: (header, [(row id, en, pt)]).
# `%1%`, `%2%`... match whatever is in that place, so lines built from variables can be translated.
GENERAL = [
    ("Level up - LevelUp.kt; skills keep their English names to match the skills tab", [
        ("level_up_congratulations_a", "Congratulations! You've just advanced a %1% level!", "Parabéns! Você subiu um nível em %1%!"),
        ("level_up_congratulations_an", "Congratulations! You've just advanced an %1% level!", "Parabéns! Você subiu um nível em %1%!"),
        ("level_up_reached", "You have now reached level %1%!", "Agora você está no nível %1%!"),
        ("level_up_hint", "Open your stats menu and click on the flashing icon for more information.", "Abra o menu de habilidades e clique no ícone piscando para mais informações."),
        ("level_up_message_a", "You've just advanced a %1% level! You have reached level %2%.", "Você subiu um nível em %1%! Agora você está no nível %2%."),
        ("level_up_message_an", "You've just advanced an %1% level! You have reached level %2%.", "Você subiu um nível em %1%! Agora você está no nível %2%."),
        ("level_up_max", "<red>Well done! You've achieved the highest possible level in this skill!", "<red>Muito bem! Você alcançou o nível máximo nesta habilidade!"),
    ]),
    ("Translation command - Translation.kt", [
        ("translation_enabled", "Portuguese dialogue enabled.", "Diálogos em português ativados."),
    ]),
    ("Tutorial Island progress bar - TutorialIsland.kt", [
        ("tutorial_progress", "%1%% Done", "%1%% concluído"),
    ]),
    ("How many box - MakeAmount.kt, Cooking.kt and Furnace.kt", [
        ("make_amount_default", "Choose how many you wish to make, then<br>click on the chosen item to begin.", "Escolha quantos quer fazer e depois<br>clique no item escolhido para começar."),
        ("make_amount_cook", "How many would you like to cook?", "Quantos você quer cozinhar?"),
        ("make_amount_bake", "How many would you like to bake?", "Quantos você quer assar?"),
        ("make_amount_roast", "How many would you like to roast?", "Quantos você quer assar no espeto?"),
        ("make_amount_cut", "How many would you like to cut?", "Quantos você quer cortar?"),
        ("make_amount_heat", "How many would you like to heat?", "Quantos você quer aquecer?"),
    ]),
    ("Chat messages - Tutorial Island activities, measured by playing each one with Portuguese on; item names stay in English", [
        ("chat_cant_reach", "I can't reach that.", "Não consigo alcançar isso."),
        ("chat_nothing_happens", "Nothing interesting happens.", "Nada de interessante acontece."),
        ("chat_level_required", "You need to have a %1% level of %2%.", "Você precisa de nível %2% em %1%."),
        ("chat_instructor_first", "You should talk to your instructor before going through the %1%.", "Fale com o seu instrutor antes de passar por aqui."),
        ("woodcutting_swing_tree", "You swing your hatchet at the tree.", "Você golpeia a árvore com o machado."),
        ("woodcutting_swing_ivy", "You swing your hatchet at the ivy.", "Você golpeia a hera com o machado."),
        ("woodcutting_get_logs", "You get some %1%.", "Você conseguiu: %1%."),
        ("woodcutting_no_hatchet", "You need a hatchet to chop down this tree.", "Você precisa de um machado (hatchet) para cortar esta árvore."),
        ("woodcutting_no_usable_hatchet", "You do not have a hatchet which you have the woodcutting level to use.", "Você não tem um machado que o seu nível de Woodcutting permita usar."),
        ("woodcutting_full", "Your inventory is too full to hold any more logs.", "O seu inventário está cheio demais para guardar mais toras."),
        ("woodcutting_birds_nest", "<red>A bird's nest falls out of the %1%!", "<red>Um ninho de pássaro (bird's nest) caiu da árvore!"),
        ("firemaking_attempt", "You attempt to light the logs.", "Você tenta acender as toras."),
        ("firemaking_success", "The fire catches and the logs begin to burn.", "O fogo pega e as toras começam a queimar."),
        ("firemaking_cant_here", "You can't light a fire here.", "Você não pode acender uma fogueira aqui."),
        ("fishing_cast_net", "You cast out your net...", "Você lança a sua rede..."),
        ("fishing_catch", "You catch some %1%.", "Você pescou: %1%."),
        ("fishing_need_tackle", "You need a %1% to catch these fish.", "Para pescar estes peixes você precisa de: %1%."),
        ("fishing_full", "Your inventory is too full to hold any more fish.", "O seu inventário está cheio demais para guardar mais peixes."),
        ("cooking_shrimps", "You successfully cook some shrimps.", "Você cozinhou os camarões (shrimps) com sucesso."),
        ("cooking_shrimps_burnt", "You accidentally burn the shrimps.", "Você queimou os camarões sem querer."),
        ("cooking_bread", "You successfully bake some bread.", "Você assou um pão (bread) com sucesso."),
        ("cooking_bread_burnt", "You accidentally burn the bread dough.", "Você queimou a massa de pão sem querer."),
        ("cooking_bread_dough", "You mix the water and flour to make some bread dough.", "Você mistura a água e a farinha e faz massa de pão (bread dough)."),
        ("cooking_nothing", "You have nothing to cook.", "Você não tem nada para cozinhar."),
        ("mining_prospect", "You examine the rock for ores...", "Você examina a rocha em busca de minério..."),
        ("mining_no_ore", "This rock contains no ore.", "Esta rocha não contém minério."),
        ("mining_contains", "This rock contains %1%.", "Esta rocha contém: %1%."),
        ("mining_swing", "You swing your pickaxe at the rock.", "Você golpeia a rocha com a picareta."),
        ("mining_get_ore", "You manage to mine some %1%.", "Você conseguiu minerar: %1%."),
        ("mining_no_pickaxe", "You need a pickaxe to mine this rock.", "Você precisa de uma picareta (pickaxe) para minerar esta rocha."),
        ("mining_no_usable_pickaxe", "You do not have a pickaxe which you have the mining level to use.", "Você não tem uma picareta que o seu nível de Mining permita usar."),
        ("mining_full", "Your inventory is too full to hold any more ore.", "O seu inventário está cheio demais para guardar mais minério."),
        ("mining_depleted", "There is currently no ore available in this rock.", "No momento não há minério nesta rocha."),
        ("mining_auto_on", "<green>Auto-mining on.", "<green>Mineração automática ligada."),
        ("mining_auto_off", "<orange>Auto-mining off.", "<orange>Mineração automática desligada."),
        ("mining_auto_not_here", "You can't auto-mine here.", "A mineração automática não funciona aqui."),
        ("smelting_bronze", "You smelt the copper and tin together in the furnace.", "Você funde o cobre e o estanho juntos na fornalha."),
        ("smelting_retrieve", "You retrieve a bar of %1%.", "Você retira uma barra de %1g%."),
        ("smelting_no_ores", "You don't have any ores to smelt.", "Você não tem minérios para fundir."),
        ("smelting_needs_bronze", "You need one copper ore and one tin ore to make a bronze bar.", "Você precisa de um minério de cobre (copper ore) e um de estanho (tin ore) para fazer uma barra de bronze (bronze bar)."),
        ("smelting_needs_elemental", "You need one elemental ore and four heaps of coal to make an elemental bar.", "Você precisa de um minério elemental (elemental ore) e quatro montes de carvão (coal) para fazer uma barra elemental (elemental bar)."),
        ("smelting_needs_steel", "You need one iron ore and two heaps of coal to make a steel bar.", "Você precisa de um minério de ferro (iron ore) e dois montes de carvão (coal) para fazer uma barra de aço (steel bar)."),
        ("smelting_needs_mithril", "You need one mithril ore and four heaps of coal to make a mithril bar.", "Você precisa de um minério de mithril (mithril ore) e quatro montes de carvão (coal) para fazer uma barra de mithril (mithril bar)."),
        ("smelting_needs_adamant", "You need one adamantite ore and six heaps of coal to make an adamant bar.", "Você precisa de um minério de adamantite (adamantite ore) e seis montes de carvão (coal) para fazer uma barra de adamant (adamant bar)."),
        ("smelting_needs_rune", "You need one runite ore and eight heaps of coal to make a rune bar.", "Você precisa de um minério de runite (runite ore) e oito montes de carvão (coal) para fazer uma barra de rune (rune bar)."),
        ("smithing_make_a", "You hammer the %1% and make a %2%.", "Você martela a barra de %1g% e faz: %2%."),
        ("smithing_make_an", "You hammer the %1% and make an %2%.", "Você martela a barra de %1g% e faz: %2%."),
        ("smithing_no_hammer", "You need a Hammer to smith items.", "Você precisa de um martelo (Hammer) para forjar itens."),
        ("smithing_not_enough_bars", "You do not have enough bars to smith this item.", "Você não tem barras suficientes para forjar este item."),
        ("ranged_no_ammo", "There is no ammo left in your quiver.", "Não há mais munição na sua aljava."),
        ("magic_no_runes", "You do not have the required items to cast this spell.", "Você não tem os itens necessários para lançar este feitiço."),
        ("music_unlocked", "<red>You have unlocked a new music track: %1%.", "<red>Você desbloqueou uma nova música: %1%."),
    ]),
    ("Farmer advice - Farmer.kt picks one at random inside a when, which the extractor can't see", [
        ("farmer_advice_belladonna", "There is a special patch for growing Belladonna - I believe that it is somewhere near Draynor Manor, where the ground is a tad 'unblessed'", "Existe um canteiro especial para plantar Belladonna (beladona) - acho que fica em algum lugar perto da Draynor Manor, onde o solo é meio 'amaldiçoado'"),
        ("farmer_advice_hops", "Hops are good for brewing ales. I believe there's a brewery up in Keldagrim somewhere, and I've heard rumours that a place called Phasmatys used to be good for that type of thing. 'Fore they all died, of course.", "O lúpulo (hops) é bom para fazer cerveja. Acho que tem uma cervejaria lá em Keldagrim, e ouvi boatos de que um lugar chamado Phasmatys era bom para esse tipo de coisa. Antes de todo mundo morrer, claro."),
        ("farmer_advice_areas", "There are many Farming areas out there. I know of Elstan's area near Falador, Dantaera's area near Catherby, Kragen's area near Ardougne and Lyra's area in Morytania. That said, I'm sure there's plenty more that I don't know about.", "Existem muitas áreas de Farming (agricultura) por aí. Conheço a do Elstan perto de Falador, a da Dantaera perto de Catherby, a do Kragen perto de Ardougne e a da Lyra em Morytania. Mas tenho certeza de que há muitas outras que eu não conheço."),
        ("farmer_advice_compost", "Don't just throw away your weeds after you've raked a patch - put them in a compost bin and make some compost.", "Não jogue fora o mato depois de limpar um canteiro com o ancinho (rake) - coloque numa composteira (compost bin) e faça adubo (compost)."),
        ("farmer_advice_plantpots", "You don't have to buy all your plantpots you know, you can make them yourself on a pottery wheel. If you're good enough at crafting, that is.", "Sabe, você não precisa comprar todos os seus vasos (plantpots), dá para fazer na roda de oleiro (pottery wheel). Se você for bom em Crafting (artesanato), claro."),
        ("farmer_advice_sacks", "You can put up to ten potatoes, cabbages or onions in vegetable sacks, although you can't have a mix in the same sack.", "Você pode colocar até dez batatas, repolhos ou cebolas em sacos de legumes (vegetable sacks), mas não dá para misturar no mesmo saco."),
        ("farmer_advice_fruit_trees", "If you want to grow fruit trees you could try a few places: Catherby, Brimhaven and The Gnome Stronghold all have fruit tree patches.", "Se quiser plantar árvores frutíferas, tente alguns lugares: Catherby, Brimhaven e The Gnome Stronghold têm canteiros para elas."),
        ("farmer_advice_soil", "You can fill plantpots with soil from any empty patch, if you have a gardening trowel.", "Você pode encher vasos com terra de qualquer canteiro vazio, se tiver uma pazinha de jardinagem (gardening trowel)."),
        ("farmer_advice_disease", "The only way to cure a bush or tree of disease is to prune away the diseased leaves with a pair of secateurs. For all other crops I would just apply some plant-cure.", "O único jeito de curar a doença de um arbusto ou árvore é podar as folhas doentes com uma tesoura de poda (secateurs). Para as outras plantações, eu só aplicaria um pouco de plant-cure (remédio para plantas)."),
        ("farmer_advice_tools", "You can buy all the farming tools from farming shops, which can be found close to the allotments in catherby.", "Você pode comprar todas as ferramentas de agricultura nas lojas de farming, que ficam perto das hortas em Catherby."),
        ("farmer_advice_watering", "Vegetables, hops and flowers need constant watering - if you ignore my advice, you will sooner or later find yourself in possession of a dead farming patch.", "Legumes, lúpulo e flores precisam ser regados sempre - se ignorar meu conselho, mais cedo ou mais tarde vai acabar com um canteiro morto."),
    ]),
    ("Chat messages - Lumbridge scripts; quest names stay in English as in the quest list", [
        ("lumbridge_quest_blood_pact", "Congratulations, you've completed a quest: <navy>The Blood Pact", "Parabéns, você completou uma missão: <navy>The Blood Pact"),
        ("lumbridge_quest_cooks_assistant", "Congratulations, you've completed a quest: <navy>cook's assistant", "Parabéns, você completou uma missão: <navy>Cook's Assistant"),
        ("lumbridge_bank_space", "You don't have enough bank space to store that.", "Você não tem espaço suficiente no banco para guardar isso."),
        ("lumbridge_doomsayer_toggle", "You cannot toggle this warning screen on or off.", "Você não pode ligar ou desligar esta tela de aviso."),
        ("lumbridge_doomsayer_visits", "You need to go to the area it is linked to enough times to have the option to do so.", "Você precisa visitar a área ligada a este aviso mais vezes para ter essa opção."),
        ("lumbridge_receive_coins", "You receive %1% coins.", "Você recebeu %1% moedas (coins)."),
        ("lumbridge_inventory_full", "Inventory full. To make more room, sell, drop or bank something.", "Inventário cheio. Para abrir espaço, venda, largue ou guarde algo no banco."),
        ("lumbridge_guard_silent", "The guard somehow doesn't feel like talking to you.", "Por algum motivo, o guarda não está a fim de conversar com você."),
        ("lumbridge_cow_refuses", "The cow doesn't want that.", "A vaca não quer isso."),
        ("lumbridge_cow_kicks", "The cow kicks you and stuns you.", "A vaca dá um coice e deixa você atordoado."),
        ("lumbridge_light_far", "That light creature is too far away to see you clearly.", "Essa criatura de luz está longe demais para ver você direito."),
        ("lumbridge_light_bored", "The creature is momentarily attracted to the light, but quickly loses interest.", "A criatura se sente atraída pela luz por um instante, mas logo perde o interesse."),
        ("lumbridge_light_attracted", "The light creature is attracted to your beam and comes towards you...", "A criatura de luz é atraída pelo seu feixe e vem na sua direção..."),
        ("lumbridge_sheep_special", "The farmer wouldn't like you shearing these special sheep. Try the white ones.", "O fazendeiro não gostaria que você tosquiasse estas ovelhas especiais. Tente as brancas."),
        ("lumbridge_sheep_shears", "You need a set of shears to do this.", "Você precisa de uma tesoura de tosquia (shears) para fazer isso."),
        ("lumbridge_sheep_whatever", "The... whatever it is... manages to get away from you!", "A... seja lá o que for... conseguiu fugir de você!"),
        ("lumbridge_sheep_escape", "The sheep manages to get away from you!", "A ovelha conseguiu fugir de você!"),
        ("lumbridge_vinesweeper", "The leprechaun sends you to Winkin's Farm.", "O leprechaun manda você para a Winkin's Farm."),
        ("lumbridge_urhney_pockets", "<red>You don't want to dip into those pockets without good reason.", "<red>Você não quer mexer nesses bolsos sem um bom motivo."),
        ("lumbridge_urhney_filthy", "<red>They're holy ...and filthy.", "<red>Eles são sagrados ...e imundos."),
        ("fishing_no_bait", "You don't have any %1%.", "Você não tem nenhum(a): %1%."),
        ("penguin_already_spy", "You've already spotted this penguin spy.", "Você já encontrou este pinguim espião."),
        ("penguin_spy", "You spy on the penguin.", "Você espiona o pinguim."),
        ("penguin_already_bear", "You've already spotted this polar bear agent.", "Você já encontrou este agente urso-polar."),
        ("penguin_found_bear", "You found the polar bear agent.", "Você encontrou o agente urso-polar."),
        ("penguin_recent", "You have recently spotted %1% %2%.", "Você encontrou recentemente: %1% %2%."),
        ("penguin_recent_bear", "You have recently spotted the polar bear agent.", "Você encontrou recentemente o agente urso-polar."),
        ("penguin_points", "You have %1% Penguin Points to spend with Larry.", "Você tem %1% Penguin Points para gastar com o Larry."),
        ("rockslug_busy", "Someone else is fighting that.", "Outra pessoa já está lutando com isso."),
        ("rockslug_salt_weak", "The rockslug isn't weak enough to be affected by the salt.", "A rockslug ainda não está fraca o bastante para o sal fazer efeito."),
        ("rockslug_salt_dies", "The rockslug shrivels up and dies.", "A rockslug murcha e morre."),
    ]),
    ("Chat messages - achievement tasks (TaskSystem.kt); the task name, difficulty and set come from interface_pt_br like in the Task List", [
        ("task_completed_set", "You have completed the Task '%1%' in the %2% %3% set!", "Você completou a tarefa '%1%' do conjunto de %3% (%2%)!"),
        ("task_completed", "You have completed the Task '%1%'!", "Você completou a tarefa '%1%'!"),
        ("task_total_one", "You have now completed %1% Task in total.", "Agora você completou %1% tarefa no total."),
        ("task_total", "You have now completed %1% Tasks in total.", "Agora você completou %1% tarefas no total."),
        # Sent as two lines: "...all of the Beginner Tasks in the Lumbridge and Draynor" / "set. Speak to Explorer Jack in Lumbridge to claim your reward."
        ("task_set_finished", "Congratulations! You have completed all of the %1% Tasks in the %2%", "Parabéns! Você completou todas as tarefas do conjunto de %2% (%1%)."),
        ("task_set_reward", "set. Speak to %1% to claim your reward.", "Fale com %1% para receber sua recompensa."),
    ]),
    ("Interfaces the server fills in - quest complete scroll (Quest.kt, rewards joined with <br>), trade (Trade*.kt)", [
        ("quest_complete_title", "You have completed %1%!", "Você completou %1%!"),
        ("quest_rewards_cooks_assistant", "1 Quest Point<br>300 Cooking XP<br>500 coins<br>20 sardines<br>Access to the cook's range", "1 ponto de missão<br>300 de XP em Cooking<br>500 moedas (coins)<br>20 sardinhas (sardines)<br>Acesso ao fogão do cozinheiro"),
        ("quest_rewards_restless_ghost", "1 Quest Point<br>1,125 Prayer XP<br>A Ghostspeak Amulet", "1 ponto de missão<br>1.125 de XP em Prayer<br>Um amuleto da fala fantasma"),
        ("quest_rewards_lost_city", "3 Quest Points<br>Access to Zanaris", "3 pontos de missão<br>Acesso a Zanaris"),
        ("quest_rewards_tears_of_guthix", "1 Quest Point<br>1000 Crafting XP<br>Access to the Tears of Guthix<br>cave", "1 ponto de missão<br>1000 de XP em Crafting<br>Acesso à caverna das<br>Tears of Guthix"),
        ("quest_rewards_blood_pact", "1 Quest Point<br>Kayle's sling, Caitlin's staff<br>and Reese's sword<br>100 Attack, Strength,<br>Defence, Ranged and Magic<br>XP<br>Access to the Lumbridge<br>Catacombs dungeon", "1 ponto de missão<br>Kayle's sling, Caitlin's staff<br>e Reese's sword<br>100 de XP em Attack, Strength,<br>Defence, Ranged e Magic<br>Acesso ao calabouço<br>Lumbridge Catacombs"),
        ("trade_with", "Trading with: %1%", "Trocando com: %1%"),
        ("trade_slots", "has %1% free inventory slots.", "tem %1% espaços livres no inventário."),
        ("trade_waiting", "Waiting for other ..", "Esperando o outro jogador.."),
        ("trade_other_accepted", "Other player has accepted.", "O outro jogador aceitou."),
        ("trade_confirm_sure", "Are you sure you want to make this trade?", "Tem certeza de que quer fazer esta troca?"),
    ]),
    ("View distance - RegionLoading.kt, the `view_distance`/`distancia` command", [
        ("view_distance_title", "How far away should scenery be shown?", "Até que distância mostrar o cenário?"),
        ("view_distance_near", "Near", "Perto"),
        ("view_distance_medium", "Medium", "Médio"),
        ("view_distance_far", "Far", "Longe"),
        ("view_distance_very_far", "Very far", "Muito longe"),
        ("view_distance_set_near", "View distance set to near.", "Distância de visão: perto."),
        ("view_distance_set_medium", "View distance set to medium.", "Distância de visão: média."),
        ("view_distance_set_far", "View distance set to far.", "Distância de visão: longe."),
        ("view_distance_set_very_far", "View distance set to very far.", "Distância de visão: muito longe."),
    ]),
    ("World map - WorldMapWalk.kt, walking to the marker", [
        ("world_map_walk_no_way", "You can't find a way to walk to your marker.", "Você não encontrou um caminho a pé até a sua marcação."),
    ]),
    ("Glossary - short words skills insert into lines (rune types, metals, farming patches, directions); used where a pt line writes %1g% instead of %1%, see Translation.kt", [
        ("word_air", "air", "ar (air)"),
        ("word_mind", "mind", "mente (mind)"),
        ("word_water", "water", "água (water)"),
        ("word_earth", "earth", "terra (earth)"),
        ("word_fire", "fire", "fogo (fire)"),
        ("word_body", "body", "corpo (body)"),
        ("word_cosmic", "cosmic", "cosmos (cosmic)"),
        ("word_chaos", "chaos", "caos (chaos)"),
        ("word_astral", "astral", "astral"),
        ("word_nature", "nature", "natureza (nature)"),
        ("word_law", "law", "lei (law)"),
        ("word_death", "death", "morte (death)"),
        ("word_blood", "blood", "sangue (blood)"),
        ("word_soul", "soul", "alma (soul)"),
        ("word_mist", "mist", "névoa (mist)"),
        ("word_dust", "dust", "poeira (dust)"),
        ("word_mud", "mud", "lama (mud)"),
        ("word_smoke", "smoke", "fumaça (smoke)"),
        ("word_steam", "steam", "vapor (steam)"),
        ("word_lava", "lava", "lava"),
        ("word_bronze", "bronze", "bronze"),
        ("word_iron", "iron", "ferro (iron)"),
        ("word_steel", "steel", "aço (steel)"),
        ("word_mithril", "mithril", "mithril"),
        ("word_adamant", "adamant", "adamante (adamant)"),
        ("word_rune", "rune", "runita (rune)"),
        ("word_blurite", "blurite", "blurita (blurite)"),
        ("word_silver", "silver", "prata (silver)"),
        ("word_gold", "gold", "ouro (gold)"),
        ("word_allotment", "allotment", "canteiro de legumes (allotment)"),
        ("word_herb_patch", "herb patch", "canteiro de ervas (herb patch)"),
        ("word_flower_patch", "flower patch", "canteiro de flores (flower patch)"),
        ("word_tree_patch", "tree patch", "canteiro de árvore (tree patch)"),
        ("word_fruit_tree_patch", "fruit tree patch", "canteiro de árvore frutífera (fruit tree patch)"),
        ("word_hops_patch", "hops patch", "canteiro de lúpulo (hops patch)"),
        ("word_bush_patch", "bush patch", "canteiro de arbustos (bush patch)"),
        ("word_cactus_patch", "cactus patch", "canteiro de cacto (cactus patch)"),
        ("word_belladonna_patch", "belladonna patch", "canteiro de beladona (belladonna patch)"),
        ("word_blindweed_patch", "blindweed patch", "canteiro de blindweed (blindweed patch)"),
        ("word_calquat_tree_patch", "calquat tree patch", "canteiro de calquat (calquat tree patch)"),
        ("word_evil_turnip_patch", "evil turnip patch", "canteiro de nabo maligno (evil turnip patch)"),
        ("word_mushroom_patch", "mushroom patch", "canteiro de cogumelos (mushroom patch)"),
        ("word_spirit_tree_patch", "spirit tree patch", "canteiro de árvore espiritual (spirit tree patch)"),
        ("word_north", "north", "norte"),
        ("word_north_east", "north-east", "nordeste"),
        ("word_east", "east", "leste"),
        ("word_south_east", "south-east", "sudeste"),
        ("word_south", "south", "sul"),
        ("word_south_west", "south-west", "sudoeste"),
        ("word_west", "west", "oeste"),
        ("word_north_west", "north-west", "noroeste"),
    ]),
    ("Dialogue boxes - text the client fills in from the cache", [
        ("click_to_continue", "Click here to continue", "Clique aqui para continuar"),
    ]),
]

# Row ids already published by the prototype, kept so nothing referencing them changes
GUIDE_IDS = {
    "Greetings! I see you are a new arrival to this land. My job is to teach you a few basic skills and functions.": "guide_greetings",
    "Do you want to go through the tutorial?": "guide_tutorial_question",
    "Yes, show me how to play.": "guide_tutorial_yes",
    "No, send me to the mainland.": "guide_tutorial_no",
    "As you wish. I'll send you on your way with the usual supplies.": "guide_skip_tutorial",
    "First we shall go through some of the game's control panels, which you can find at the bottom right of your screen.": "guide_control_panels",
    "Click on the flashing spanner icon to open your game options.": "guide_spanner_icon",
    "The options panel lets you change the screen brightness, the volume of the music and sound effects, and whether other players may offer you help.": "guide_options_panel",
    "That's all I have to teach you. Go through that door and the Survival Expert will show you how to look after yourself.": "guide_next_door",
    "Thanks, I'll do that.": "guide_player_thanks",
    "You've learnt all I have to teach. Follow the arrow to your next instructor.": "guide_learnt_all",
    "Welcome to Lumbridge! To get more help, simply click on the Lumbridge Guide or one of the Tutors - these can be found by looking for the question mark icon on your minimap.": "guide_welcome_lumbridge",
    "Welcome to %server%.": "guide_welcome_server",
}


SERVER_NAME = re.compile(r'\$\{(?:Settings\["server\.name"\]|World\.name)\}')
VARIABLE = re.compile(r"\$\{[^}]*\}|\$[a-zA-Z_][a-zA-Z0-9_]*")


def placeholders(text):
    """Kotlin template to table text: the server name becomes %server%, other variables %1%, %2%..."""
    text = SERVER_NAME.sub("%server%", text)
    count = [0]

    def number(_):
        count[0] += 1
        return f"%{count[0]}%"

    return VARIABLE.sub(number, text)


def path_of(name):
    return KOTLIN + name + ".kt" if name.startswith("content/") else SRC + name + ".kt"


def extract(name, with_kind=False):
    src = open(path_of(name)).read()
    found = []
    for kind, pattern in zip(KINDS, PATTERNS):
        for match in pattern.finditer(src):
            found.append((match.start(), kind, match.group(1)))
    texts = []
    seen = set()
    for _, kind, text in sorted(found):
        assert "\\" not in text, (name, text)
        # Multi-line literals only exist in commented-out code here and can't be written to the table
        if "\n" in text:
            continue
        text = placeholders(text)
        if text not in seen:
            seen.add(text)
            texts.append((kind, text) if with_kind else text)
    return texts


def toml(value):
    assert "\\" not in value, value
    return '"' + value.replace('"', '\\"') + '"'


def skill_rows(seen):
    """Rows for the skills' code and data tables (see skill_text.py), and what's left to translate by file"""
    out = []
    missing = {}

    def section(header, prefix, texts, source, kind):
        rows = []
        for text in texts:
            if text in seen:
                continue
            seen.add(text)
            if text not in PT:
                missing.setdefault(source, []).append({"kind": kind(text) if callable(kind) else kind, "en": text})
                continue
            if max(len(text.encode()), len(PT[text].encode())) > MAX_BYTES:
                print(f"SKIPPED, over {MAX_BYTES} bytes, stays in English: {text[:60]}...")
                continue
            rows += ["", f"[.{prefix}_{len(rows) // 4 + 1}]", f"en = {toml(text)}", f"pt = {toml(PT[text])}"]
        if rows:
            out.extend(["", f"# {header}"] + rows)

    by_file = {}
    for rel, kind, _, keys in skill_text.code_texts(ROOT):
        by_file.setdefault(rel, []).append((kind, keys))
    for rel, entries in by_file.items():
        prefix = "skill_" + re.sub(r"[^a-z0-9]+", "_", rel.removesuffix(".kt").lower())
        # The dump keeps whether each line is chat or dialogue; the table only needs the text
        kinds = {key: kind for kind, keys in entries for key in keys}
        section(f"Skills - {rel}", prefix, list(kinds), "skill/" + rel, lambda key: kinds[key])
    for folder in skill_text.FOLDERS:
        by_file = {}
        for rel, kind, _, keys in skill_text.code_texts(ROOT, folder):
            by_file.setdefault(rel, []).append((kind, keys))
        for rel, entries in by_file.items():
            path = f"{folder}/{rel}"
            kinds = {key: kind for kind, keys in entries for key in keys}
            section(f"Text - {path}", "text_" + re.sub(r"[^a-z0-9]+", "_", path.removesuffix(".kt").lower()), list(kinds), path, lambda key: kinds[key])
    for rel, texts in skill_text.data_texts(ROOT):
        name = re.sub(r"[^a-z0-9]+", "_", rel.removesuffix(".toml").lower())
        section(f"Skill data - data/{rel}", "data_" + name, texts, "data/" + rel, "data")
    runtime = {}
    for file in sorted(glob.glob(TRANSLATIONS + "/pt_*_runtime.json")):
        runtime.update(json.load(open(file)))
    rows = []
    for en, pt in runtime.items():
        if en in seen:
            continue
        seen.add(en)
        rows += ["", f"[.runtime_{len(rows) // 4 + 1}]", f"en = {toml(en)}", f"pt = {toml(pt)}"]
    if rows:
        out.extend(["", "# Text only seen while playing (SkillActionTranslationTest, BankPinTranslationTest): built from data the code reads, passed in as variables, or Bank PIN messages joined line by line with <br>"] + rows)
    return out, missing


def main():
    out = [
        "# Brazilian Portuguese dialogue, used by `Player.translate` when `portuguese_dialogue` is on.",
        "# `en` must match the original text exactly; lines without a row stay in English.",
        "# `%server%` stands in for the `server.name` setting in both columns.",
        "# Keep NPC, item and place names as the game shows them so players can find them;",
        "# items and skills get the English name in brackets the first time they appear.",
        "",
        "[dialogue_pt_br]",
        'en = "string"',
        'pt = "string"',
    ]
    seen = set()
    missing = []
    for file, prefix, title in NPCS:
        out += ["", f"# {title} - Tutorial Island"]
        count = 0
        for text in extract(file):
            if text in seen:
                continue
            seen.add(text)
            if text not in PT:
                missing.append(text)
                continue
            # Tables reject strings over 512 bytes when loading, which would stop the server starting
            if max(len(text.encode()), len(PT[text].encode())) > MAX_BYTES:
                print(f"SKIPPED, over {MAX_BYTES} bytes, stays in English: {text[:60]}...")
                continue
            count += 1
            row = GUIDE_IDS.get(text, f"{prefix}_{count}")
            out += ["", f"[.{row}]", f"en = {toml(text)}", f"pt = {toml(PT[text])}"]
    for header, rows in GENERAL:
        out += ["", f"# {header}"]
        for row, en, pt in rows:
            assert all(ord(c) <= 0xFF for c in pt), pt
            out += ["", f"[.{row}]", f"en = {toml(en)}", f"pt = {toml(pt)}"]
            seen.add(en)
    skills, skill_missing = skill_rows(seen)
    out += skills
    missing += [en for lines in skill_missing.values() for en in (line["en"] for line in lines)]
    if "--dump" in sys.argv:
        dump = {}
        for file, prefix, title in NPCS:
            lines = [{"kind": kind, "en": text} for kind, text in extract(file, with_kind=True) if text not in PT]
            if lines:
                dump[file] = lines
        dump.update(skill_missing)
        json.dump(dump, open(TRANSLATIONS + "/missing.json", "w"), indent=1, ensure_ascii=False)
        print(f"{sum(len(v) for v in dump.values())} untranslated lines in {len(dump)} scripts")
        return
    unused = [text for text in PT if text not in seen]
    if missing or unused:
        sys.exit(f"Missing: {missing}\nUnused: {unused}")
    for text in PT.values():
        bad = [c for c in text if ord(c) > 0xFF]
        assert not bad, (text, bad)
    open(OUT, "w").write("\n".join(out) + "\n")
    print(f"{len(seen)} lines written to {OUT}")


main()
