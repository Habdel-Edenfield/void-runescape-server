# Map editor

Ferramentas de mapa deste fork: minimapa de cima e linha de comando para exportar, editar e gravar regiões no cache 634. Fica num módulo separado para não conflitar com atualizações do GregHib/void; a única linha fora daqui é `include("mapeditor")` em `settings.gradle.kts`.

Tudo roda a partir da raiz do repositório.

## Minimapa

```bash
./gradlew :mapeditor:renderMinimap   # gera images/<andar>/<região>.png (~2 min, fora do git)
./gradlew :mapeditor:minimap         # abre o viewer
```

No viewer, o campo de cima aceita o número da região (ex. `12850`) e os três de baixo `x`, `y`, `andar`; Enter salta para lá. Rode `renderMinimap` de novo depois de gravar mudanças no cache.

## Exportar, editar e gravar uma região

```bash
./gradlew :mapeditor:mapRegion -Pargs="export 12850"          # ou um tile: export 3222,3218
./gradlew :mapeditor:mapRegion -Pargs="import mapeditor/regions/12850.toml --cache /caminho/da/copia/"
./gradlew :mapeditor:mapRegion -Pargs="import mapeditor/regions/12850.toml --live"
```

- `export` lê `data/cache/` (ou `--cache`) e escreve `mapeditor/regions/<região>.toml`, que não vai para o git.
- O arquivo tem, por andar, grades 64×64 de `height`, `overlay`, `overlay_shape`, `overlay_rotation`, `settings` e `underlay`. A primeira linha é a borda norte e a primeira coluna a borda oeste. Os objetos ficam em `[objects]` com coordenadas do mundo. `extras` é o bloco de luz e ambiente do fim do arquivo de terreno: não mexa.
- `import` mostra quantos tiles e objetos mudaram e grava. Sem `--cache` ou `--live` ele recusa, e também recusa `--cache` apontando para `data/cache/`.
- `--live` copia `data/cache/` inteiro para `mapeditor/backups/<data-hora>/` antes de gravar. Para desfazer, pare o servidor e copie o backup de volta.
- Depois de gravar no cache do servidor, reinicie o servidor. A gravação atualiza a tabela de referência do índice 5, então o cliente deve baixar só a região mudada, sem limpar o cache local.

## Testes

```bash
./gradlew :mapeditor:test
```

Decodifica e recodifica as 1.676 regiões do cache (direto e passando pelo arquivo de texto) e exige igualdade byte a byte; e grava uma região numa cópia temporária do cache.
