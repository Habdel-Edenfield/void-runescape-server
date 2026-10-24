# Mapa das janelas que o cliente desenha em inglês

Programas que geraram o relatório https://claude.ai/artifact/A6NgmQ8YiF543ttWpMhuyq (resumo em [`../MAPA_JANELAS_CLIENTE.md`](../MAPA_JANELAS_CLIENTE.md)).
Só leem o cache: nunca gravam nada. Servem para refazer o mapa depois de trocar o cache ou de ampliar a tradução.

| Arquivo | O que faz |
|---|---|
| `mapa.sh` | Roda tudo: compila os dois programas Java, lê o cache e chama `analyse.py`. |
| `Dump.java` | Lê interfaces, scripts do cliente (CS2), enums e structs e grava em JSON (`interfaces.jsonl`, `scripts.jsonl`, `enums.jsonl`, `structs.jsonl`). |
| `Count.java` | Conta nomes e opções de itens, NPCs, objetos, missões, chat rápido e mapa (`other_texts.txt`). |
| `analyse.py` | Liga cada janela aos seus textos (fixos, scripts, enums/structs, dicas, opções) e ao Kotlin que a abre. Grava `per_interface.json`, `appendix.json` (tabela do relatório) e `shared_scripts.json`. |
| `show.py` | Mostra o detalhe de algumas janelas pelo número. |

## Rodar

A partir da raiz do repositório, com a saída fora do Git:

```bash
bash tools/translation/mapa_cliente/mapa.sh ~/Documents/void-downloads/cache-antes-tarefas-pt /tmp/mapa
python3 -I tools/translation/mapa_cliente/show.py /tmp/mapa 371 884 762
```

- **Cache original** (`cache-antes-tarefas-pt`): conta todo o inglês. **`data/cache`** (com o patch): mostra só o que o patch ainda deixa em inglês, porque os scripts `pt_br_*` são ignorados. Em vez de ler o `data/cache` direto, copie-o antes: o programa só lê, mas o servidor pode estar usando a pasta.
- Precisa dos jars dos módulos `cache` e `buffer` (`./gradlew build`) e do jar do servidor para as outras dependências. Para usar outros, defina `CACHE_JAR`, `BUFFER_JAR` e `VOID_SERVER_JAR`.

## Limites

- Um script usado por 6 janelas ou mais conta como "dividido" e não entra na soma de cada uma.
- Opções do botão direito só são separadas quando o script as define direto; verbos montados por partes ("Cook " + "All") contam como texto.
- Rótulos fixos que o servidor troca a partir de uma tabela (`CacheInterfaceTranslation.kt`) continuam contados como inglês: o programa só reconhece `sendText("janela", "componente", ...)` escrito no Kotlin.
- O decodificador do projeto lê texto como UTF-8 e o cache guarda Latin-1: acentos aparecem trocados nos JSON.
