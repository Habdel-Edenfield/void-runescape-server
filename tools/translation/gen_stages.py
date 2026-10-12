"""Generates tutorial_island_pt_br.tables.toml, one row per tutorial_island stage.

Fails if a stage is missing, has more than 6 lines, or a line is longer than the
longest English line. Usage: python3 -I gen_stages.py <repo root>
"""
import re
import sys

ROOT = sys.argv[1]
SOURCE = ROOT + "/data/area/misthalin/tutorial_island/tutorial_island.tables.toml"
OUT = ROOT + "/data/entity/player/dialogue/translation/tutorial_island_pt_br.tables.toml"

S = {
    0: ("Primeiros passos", [
        "Para começar o tutorial, use o botão esquerdo do mouse e clique",
        "no %server% Guide nesta sala. Ele está indicado por uma seta",
        "amarela piscando sobre a cabeça. Se não conseguir vê-lo, use",
        "as setas do teclado para girar a câmera."]),
    1: ("Controles do jogador", [
        "Clique no ícone de chave inglesa piscando no canto inferior",
        "direito da tela. Ele mostra os controles do jogador."]),
    2: ("Controles do jogador", [
        "No painel lateral você vê várias opções: o brilho da tela, o",
        "volume da música e se o seu personagem aceita ajuda de outros",
        "jogadores. Não se preocupe muito com isso agora. Fale com o",
        "RuneScape Guide para continuar."]),
    3: ("Interagindo com o cenário", [
        "Você pode interagir com muitos objetos do cenário clicando",
        "neles. O botão direito do mouse mostra mais opções. Fique à",
        "vontade para testar com as coisas desta sala e depois clique na",
        "porta indicada pela seta amarela para ir ao próximo instrutor."]),
    4: ("Andando por aí", [
        "Siga o caminho para encontrar o próximo instrutor. Clicar no",
        "chão faz você andar até aquele ponto. Fale com o Survival",
        "Expert perto do lago para continuar o tutorial. Lembre-se de",
        "que dá para girar a câmera com as setas do teclado."]),
    5: ("Vendo os itens que você recebeu.", [
        "Clique no ícone de mochila piscando, à direita da janela",
        "principal, para ver o seu inventário. O inventário é uma lista",
        "de tudo o que está na sua mochila."]),
    6: ("Corte uma árvore", [
        "Você pode clicar no ícone da mochila a qualquer momento para",
        "ver os itens do seu inventário. Repare que agora você tem um",
        "machado (hatchet) no inventário. Use-o para conseguir toras",
        "(logs) clicando em uma das árvores da área."]),
    7: ("Fazendo uma fogueira", [
        "Muito bem! Você conseguiu cortar toras da árvore! Agora use a",
        "pederneira (tinderbox) do inventário para acender as toras.",
        "Primeiro clique na pederneira para usá-la ('Use').",
        "Depois clique nas toras do inventário para acendê-las."]),
    8: ("", [
        "Você ganhou experiência.",
        "Clique no ícone de gráfico de barras piscando, perto do botão",
        "do inventário, para ver as suas habilidades (skills)."]),
    9: ("As suas habilidades.", [
        "Aqui você vê o quanto é bom em cada habilidade. Ao passar o",
        "mouse sobre os ícones deste painel, a caixinha amarela mostra",
        "exatamente quanta experiência você tem e quanto falta para o",
        "próximo nível. Fale com o Survival Expert para continuar."]),
    10: ("Pesque alguns camarões", [
        "Clique no ponto de pesca brilhante, indicado pela seta",
        "piscando. Lembre-se: você pode ver o inventário clicando no",
        "ícone da mochila."]),
    11: ("Cozinhando os seus camarões.", [
        "Agora que você pescou camarões (shrimp), vamos cozinhá-los.",
        "Primeiro acenda uma fogueira: corte uma árvore e use a",
        "pederneira nas toras. Se perder o machado ou a pederneira, a",
        "Brynna te dá outro."]),
    12: ("Queimando os seus camarões.", [
        "Você acabou de queimar o seu primeiro camarão. Isso é normal.",
        "Com mais experiência em Cooking, você vai queimar menos.",
        "Vamos tentar cozinhar sem queimar desta vez. Primeiro pesque",
        "mais camarões e depois use-os numa fogueira."]),
    13: ("Sua primeira refeição em %server%!", [
        "Se quiser revisar algo que aprendeu até aqui, fale com o",
        "Survival Expert. Agora você pode seguir para o próximo",
        "instrutor. Clique no portão indicado e siga o caminho.",
        "Lembre-se: você pode mover a câmera com as setas do teclado."]),
    14: ("Encontre o seu próximo instrutor.", [
        "Siga o caminho até a porta com a seta amarela em cima.",
        "Clique na porta para abri-la. Repare no minimapa no canto",
        "superior direito: ele mostra uma vista de cima da área ao seu",
        "redor. Ele também pode ser usado para se orientar."]),
    15: ("Encontre o seu próximo instrutor.", [
        "Fale com o cozinheiro indicado. Ele vai te ensinar partes mais",
        "avançadas de Cooking (culinária), como combinar ingredientes.",
        "Ele também vai te mostrar o menu do tocador de música."]),
    16: ("Fazendo massa.", [
        "Esta é a base de muitas refeições. Para fazer massa (dough),",
        "misture farinha e água. Primeiro clique com o botão direito no",
        "balde de água e escolha 'Use', depois clique com o botão",
        "esquerdo no pote de farinha."]),
    17: ("Assando a massa.", [
        "Agora que você fez a massa, pode assá-la. Para isso, use a",
        "massa no fogão (range) indicado pela seta. Se perder a massa,",
        "fale com o Lev - ele te dá mais ingredientes."]),
    18: ("Assando a massa", [
        "Muito bem! O seu primeiro pão. Conforme ganhar experiência em",
        "Cooking, você vai poder fazer outras coisas, como tortas, bolos",
        "e até kebabs. Agora que pegou o jeito, vamos em frente.",
        "Clique no ícone piscando no canto inferior direito para ver o",
        "tocador de música."]),
    19: ("O tocador de música.", [
        "Nesta tela você controla a música que toca. Conforme explora o",
        "mundo, mais músicas vão sendo desbloqueadas. Depois de olhar",
        "este menu, use a próxima porta para continuar. Se precisar",
        "revisar algo daqui, fale com o Lev."]),
    20: ("Emotes.", [
        "",
        "Que tal mostrar alguns sentimentos? Você vai ver um ícone",
        "piscando no formato de uma pessoa. Clique nele para ver os",
        "seus emotes (gestos)."]),
    21: ("Emotes.", [
        "Quando as palavras não bastam para dizer como você se sente,",
        "tente um emote. Vá em frente, teste um! Alguns emotes aparecem",
        "em cinza e ainda não podem ser usados. Não se preocupe!",
        "Conforme avança no jogo, você ganha acesso a todo tipo de",
        "coisa, inclusive emotes mais divertidos."]),
    22: ("Correndo.", [
        "Falta pouco para chegar ao próximo guia.",
        "Que tal ir correndo? Você pode correr clicando",
        "no ícone da bota ao lado do minimapa ou segurando",
        "a tecla Ctrl enquanto clica no destino."]),
    23: ("Corra até o próximo guia.", [
        "Agora que o botão de correr está ligado, siga o caminho até o",
        "fim. Talvez você repare que o número no botão vai diminuindo.",
        "Essa é a sua energia de corrida. Se ela chegar a zero, você",
        "para de correr. Clique na porta para passar por ela."]),
    24: ("", [
        "Fale com o Quest Guide.",
        "",
        "Ele vai te contar tudo sobre as missões (quests).",
        ""]),
    25: ("", [
        "Abra o diário de missões (Quest Journal).",
        "",
        "Clique no ícone piscando ao lado do inventário.",
        ""]),
    26: ("O seu diário de missões", [
        "",
        "Este é o Quest Journal, a lista de todas as missões do jogo.",
        "Fale de novo com o Quest Guide para uma explicação.",
        ""]),
    27: ("", [
        "Seguindo em frente.",
        "Hora de entrar em algumas cavernas. Clique na escada para",
        "descer até a próxima área.",
        ""]),
    28: ("Mining e Smithing.", [
        "Agora vamos arrumar uma arma para você. Melhor ainda: você",
        "mesmo pode fazer a sua primeira arma. Não se preocupe, o",
        "Mining Instructor vai te ajudar. Fale com ele e ele explica",
        "tudo.",
        ""]),
    29: ("Prospecção.", [
        "Para examinar uma rocha minerável, clique nela com o botão",
        "direito e escolha 'Prospect'. Isso mostra o tipo de minério que",
        "dá para tirar dela. Tente agora em uma das rochas indicadas.",
        ""]),
    30: ("É estanho.", [
        "",
        "Agora você sabe que há estanho (tin) nas rochas cinzas. Tente",
        "examinar as marrons em seguida.",
        ""]),
    31: ("É cobre.", [
        "Fale com o Mining Instructor para saber mais sobre esses tipos",
        "de minério e como minerá-los. Ele vai até te dar as",
        "ferramentas necessárias.",
        ""]),
    32: ("Minerando.", [
        "É bem simples. Basta clicar com o botão direito na rocha e",
        "escolher 'Mine'. Você só consegue minerar com uma picareta",
        "(pickaxe). Então tente: primeiro minere um minério de estanho.",
        ""]),
    33: ("Minerando.", [
        "Agora que tem minério de estanho, só falta o de cobre (copper).",
        "Aí você terá tudo para fazer uma barra de bronze. Como antes,",
        "clique com o botão direito na rocha de cobre e escolha 'Mine'.",
        ""]),
    34: ("Fundindo.", [
        "Agora você deve ter minério de cobre e de estanho. Vamos",
        "fundi-los numa barra de bronze. Para isso, clique com o botão",
        "direito no minério de estanho ou de cobre, escolha 'Use' e",
        "depois clique na fornalha (furnace). Tente agora."]),
    35: ("Você fez uma barra de bronze!", [
        "",
        "Fale com o Mining Instructor e ele vai te mostrar como",
        "transformá-la numa arma.",
        ""]),
    36: ("Forjando uma adaga.", [
        "Para forjar, você precisa de um martelo (hammer), barras de",
        "metal suficientes para o item desejado e uma bigorna (anvil).",
        "Para começar, clique na bigorna ou use a barra nela.",
        ""]),
    37: ("Forjando uma adaga.", [
        "Com o menu de Smithing aberto, você vê a lista de tudo o que",
        "pode fazer. No seu nível, só dá para fazer a adaga (dagger);",
        "isso aparece no texto branco embaixo dela. Escolha a adaga",
        "para continuar."]),
    38: ("Você terminou nesta área.", [
        "Então vamos em frente. Passe pelos portões indicados pela seta.",
        "Lembre-se: talvez você precise mover a câmera para ver o que",
        "está ao redor. Fale com o guia para revisar a qualquer momento.",
        ""]),
    39: ("Combate.", [
        "",
        "Nesta área você vai aprender a lutar com espadas e arcos.",
        "Fale com o guia e ele vai te contar tudo.",
        ""]),
    40: ("Empunhando armas.", [
        "",
        "Agora você tem acesso a uma nova tela. Clique no ícone piscando",
        "de um homem, o que fica à direita do ícone da mochila.",
        ""]),
    41: ("Este é o seu equipamento vestido.", [
        "Aqui você vê os itens que está usando. Repare no botão",
        "'View equipment stats'. Clique nele agora para ver os",
        "detalhes do que você está equipando.",
        ""]),
    42: ("Tela de equipamento", [
        "Você vê os itens que está vestindo no equipamento à esquerda",
        "da tela, com as estatísticas somadas à direita. Vamos",
        "adicionar algo. Clique com o botão esquerdo na adaga para",
        "empunhá-la ('Wield')."]),
    43: ("Agora você está segurando a adaga.", [
        "Roupas, armaduras, armas e muitos outros itens são equipados",
        "assim. Para tirar um item, clique nele no equipamento vestido.",
        "Você pode fechar esta janela clicando no pequeno 'x' no canto",
        "superior direito. Fale com o Combat Instructor para continuar."]),
    44: ("Tirando itens.", [
        "No painel de equipamento vestido, clique com o botão direito",
        "na adaga e escolha a opção 'Remove' na lista. Depois de tirar",
        "a adaga, empunhe a espada e o escudo. Ao passar o mouse sobre",
        "um item, o nome dele aparece no canto superior esquerdo da",
        "tela."]),
    45: ("Tela de combate.", [
        "",
        "Clique no ícone piscando de espadas cruzadas para ver a tela",
        "de combate.",
        ""]),
    46: ("Esta é a sua tela de combate.", [
        "Aqui você escolhe o tipo de ataque que o seu personagem vai",
        "usar. Monstros diferentes têm fraquezas diferentes. Passe o",
        "mouse sobre os botões para ver o tipo de XP que cada ataque",
        "dá. Agora que tem as ferramentas para a batalha, que tal",
        "enfrentar alguns ratos? Clique nos portões indicados."]),
    47: ("Atacando.", [
        "",
        "Para atacar o rato, clique nele com o botão direito e escolha",
        "'Attack'. Você vai andar até ele e começar a bater nele.",
        ""]),
    48: ("Relaxe e assista.", [
        "Durante a luta, você vê uma barra sobre a sua cabeça. Ela",
        "mostra quanta vida (health) você ainda tem. O seu oponente",
        "também tem uma. Você continua atacando o rato até ele morrer",
        "ou até você fazer outra coisa."]),
    49: ("Muito bem, sua primeira vitória!", [
        "",
        "Passe pelo portão e fale com o Combat Instructor; ele vai te",
        "dar a próxima tarefa.",
        ""]),
    50: ("Ataque à distância nos ratos.", [
        "Agora você tem um arco e algumas flechas. Antes de usá-los,",
        "você precisa equipá-los. Depois de equipar o equipamento de",
        "longo alcance (ranged), tente matar outro rato. Lembre-se:",
        "para atacar, clique com o botão direito e escolha 'Attack'."]),
    51: ("Seguindo em frente.", [
        "Você completou as tarefas daqui. Para seguir, clique na escada",
        "indicada. Se precisar revisar algo que aprendeu aqui, é só",
        "falar com o Combat Instructor e ele explica o que puder."]),
    52: ("O banco.", [
        "Siga o caminho até chegar à frente do prédio. Este é o Banco",
        "de %server%, onde você pode guardar os seus itens mais",
        "valiosos. Para abrir o seu cofre, clique com o botão direito",
        "num guichê aberto indicado e escolha 'Use'."]),
    53: ("Este é o seu cofre do banco.", [
        "Aqui você guarda coisas em segurança. Se você morrer, tudo o",
        "que estiver no banco fica salvo. Para depositar algo, clique",
        "com o botão direito no item e escolha 'Store'. Depois de",
        "olhar bem, feche a janela e passe pela porta indicada."]),
    54: ("Conselhos financeiros.", [
        "",
        "O guia daqui vai te contar tudo sobre ganhar dinheiro. É só",
        "clicar nele para ouvir o que ele tem a dizer.",
        ""]),
    55: ("", [
        "",
        "Siga pela próxima porta.",
        "",
        ""]),
    56: ("Prayer (oração).", [
        "Siga o caminho até a capela e entre.",
        "Lá dentro, fale com o monge. Ele vai te contar tudo sobre a",
        "habilidade Prayer.",
        ""]),
    57: ("O seu menu de orações.", [
        "",
        "Clique no ícone piscando para abrir o menu de Prayer.",
        "",
        ""]),
    58: ("", [
        "O seu menu de orações.",
        "",
        "Fale com o Brother Brace e ele vai te explicar as orações.",
        ""]),
    59: ("", [
        "Lista de amigos.",
        "Agora você deve ver outro ícone novo. Clique no rosto",
        "sorridente piscando para abrir a sua lista de amigos.",
        ""]),
    60: ("Esta é a sua lista de amigos.", [
        "",
        "O Brother Brace vai explicar isto daqui a pouco, mas antes",
        "clique no botão de ponto vermelho no canto inferior direito",
        "da lista de amigos."]),
    61: ("Esta é a sua lista de ignorados.", [
        "As duas listas - amigos e ignorados - ajudam muito a saber",
        "quando os seus amigos estão online ou a bloquear mensagens",
        "de pessoas de quem você simplesmente não gosta. Fale com o",
        "Brother Brace e ele vai te contar mais."]),
    62: ("", [
        "O seu último instrutor!",
        "Você está quase terminando a Ilha Tutorial. Passe pela porta",
        "para encontrar o caminho até o seu último instrutor.",
        ""]),
    63: ("O seu último instrutor!", [
        "É só seguir o caminho até a casa do mago, onde você vai",
        "aprender a lançar feitiços. Fale com o mago indicado para",
        "saber mais.",
        ""]),
    64: ("Abra a sua última aba.", [
        "",
        "Abra o livro de feitiços (Magic Spellbook) clicando no ícone",
        "piscando ao lado da aba de orações que você acabou de ver.",
        ""]),
    65: ("", [
        "Esta é a sua lista de feitiços.",
        "",
        "Pergunte ao mago sobre ela.",
        ""]),
    66: ("Lance Wind Strike numa galinha.", [
        "Agora que tem runas, você deve ver o ícone do Wind Strike no",
        "canto superior esquerdo da tela de magia - o terceiro a partir",
        "da esquerda. Vá até as galinhas na jaula, clique no ícone do",
        "Wind Strike e escolha uma galinha como alvo. Pode levar",
        "algumas tentativas. Se precisar de mais runas, peça a Terrova."]),
    67: ("Você está quase terminando o tutorial!", [
        "",
        "Agora só falta se teletransportar para o continente. É só",
        "falar com Terrova e ele vai te explicar como.",
        ""]),
    68: ("Você está quase terminando o tutorial!", [
        "",
        "Basta clicar no primeiro feitiço, Home Teleport, no seu livro",
        "de feitiços (Magic Spellbook). Ele não precisa de runas, mas só",
        "pode ser lançado uma vez a cada 30 minutos.",
        ""]),
}


def english():
    text = open(SOURCE).read()
    stages = {}
    longest = 0
    for row in re.split(r"\n(?=\[\.stage_)", text)[1:]:
        stage = int(re.match(r"\[\.stage_(\d+)\]", row).group(1))
        lines = re.search(r"^lines = \[(.*)\]", row, re.M)
        values = re.findall(r'"((?:[^"\\]|\\.)*)"', lines.group(1)) if lines else []
        longest = max([longest] + [len(v) for v in values])
        stages[stage] = values
    return stages, longest


def main():
    stages, longest = english()
    if set(stages) != set(S):
        sys.exit(f"Stage mismatch: missing {set(stages) - set(S)}, extra {set(S) - set(stages)}")
    out = [
        "# Brazilian Portuguese copy of the `tutorial_island` instruction box, swapped in by",
        "# `Player.translate(row)` when `portuguese_dialogue` is on. Stages without a row stay in English.",
        "# Each line must fit the box (1 title + 6 lines); `%server%` is replaced with `server.name`.",
        "",
        "[tutorial_island_pt_br]",
        'title = "string"',
        'lines = "list<string>"',
    ]
    for stage in sorted(S):
        title, lines = S[stage]
        assert len(lines) <= 6, (stage, len(lines))
        for line in lines + [title]:
            assert len(line) <= longest, (stage, len(line), line)
            assert all(ord(c) <= 0xFF for c in line), (stage, line)
            assert '"' not in line and "\\" not in line, line
        out += ["", f"[.stage_{stage}]"]
        if title:
            out.append(f'title = "{title}"')
        out.append("lines = [" + ", ".join(f'"{line}"' for line in lines) + "]")
    open(OUT, "w").write("\n".join(out) + "\n")
    print(f"{len(S)} stages written, longest English line {longest} chars")


main()
