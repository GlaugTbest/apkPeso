#!/usr/bin/env bash
set -euo pipefail

temp=$(mktemp -d)
trap 'rm -rf -- "$temp"' EXIT

store_blob() {
    base64 -w0 "$1" | gh api --method POST "repos/$GITHUB_REPOSITORY/git/blobs" \
        -f encoding=base64 -F content=@- --jq .sha
}

for screen in evidence/02-configuracoes.png evidence/03-hoje.png evidence/05-semana.png \
    evidence/07-jantar-teclado.png evidence/falha.png; do
    [ -f "$screen" ] || continue
    ffmpeg -hide_banner -loglevel error -y -i "$screen" -vf scale=360:-1 -q:v 5 "$temp/screen.jpg"
    printf 'CAPTURA %s %s\n' "$(basename "$screen")" "$(store_blob "$temp/screen.jpg")"
done
