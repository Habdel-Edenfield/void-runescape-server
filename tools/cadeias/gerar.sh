#!/usr/bin/env bash
# Regera as páginas das cadeias de produção: o guia em trilha (cadeias-guia.html) e o catálogo (cadeias-de-producao.html),
# os dois em ~/Documents/void-downloads/.
# Rode na raiz do Void: bash tools/cadeias/gerar.sh
# Passos: 1) exporta do cache nome, bônus e ingredientes de Summoning (itens.json);
#         2) desenha os ícones dos itens, se a pasta ainda não existe (uns 2 minutos; apague-a para refazer);
#         3) monta o HTML a partir de data/, do itens.json, dos ícones e da exportação do Montador de Interfaces.
set -euo pipefail
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
SAIDA_DIR="${CADEIAS_DIR:-$HOME/Documents/void-downloads/cadeias}"
HTML="${CADEIAS_HTML:-$HOME/Documents/void-downloads/cadeias-de-producao.html}"
MONTADOR="${MONTADOR_EXPORT:-$HOME/Documents/void-montador-de-interfaces/cache/jogo/exportado}"
export LANG=C.UTF-8 LC_ALL=C.UTF-8   # formato de número com ponto para o Gradle; esta locale sempre existe
cd "$RAIZ"
mkdir -p "$SAIDA_DIR"

echo "1/3 nomes e bônus do cache"
./gradlew -q :tools:cadeias:cadeiasExport -Pargs="./data/cache/ ./data/ $SAIDA_DIR/itens.json"

if [ ! -d "$SAIDA_DIR/icones" ] || [ -z "$(ls -A "$SAIDA_DIR/icones" 2>/dev/null)" ]; then
  echo "2/3 ícones dos itens (demora uns 2 minutos)"
  ./gradlew -q :tools:icon:dumpItemSprites -Pargs="./data/cache/ $SAIDA_DIR/icones"
else
  echo "2/3 ícones já existem em $SAIDA_DIR/icones (apague a pasta para refazer)"
fi

if [ ! -f "$MONTADOR/interfaces.json" ]; then
  echo "A exportação do Montador de Interfaces não está em $MONTADOR."
  echo "Rode: ./gradlew :tools:montador:montadorExport -Pargs=\"data/cache/ data/ $MONTADOR\""
  exit 1
fi

echo "3/3 as páginas"
python3 -I tools/cadeias/gerar.py --void "$RAIZ" --cadeias "$SAIDA_DIR" --montador "$MONTADOR" --saida "$HTML"
python3 -I tools/cadeias/guia.py --void "$RAIZ" --cadeias "$SAIDA_DIR" --montador "$MONTADOR" --saida "${CADEIAS_GUIA:-$HOME/Documents/void-downloads/cadeias-guia.html}"
