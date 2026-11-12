#!/usr/bin/env bash
set -euo pipefail

temp=$(mktemp -d)
trap 'rm -rf -- "$temp"' EXIT

store_blob() {
    base64 -w0 "$1" | gh api --method POST "repos/$GITHUB_REPOSITORY/git/blobs" \
        -f encoding=base64 -F content=@- --jq .sha
}

cat > "$temp/Thumbnail.java" <<'JAVA'
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

class Thumbnail {
    public static void main(String[] args) throws Exception {
        BufferedImage input = ImageIO.read(new File(args[0]));
        int width = 360;
        int height = input.getHeight() * width / input.getWidth();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(input, 0, 0, width, height, null);
        graphics.dispose();
        ImageIO.write(image, "jpg", new File(args[1]));
    }
}
JAVA

for screen in evidence/02-configuracoes.png evidence/03-hoje.png evidence/05-semana.png \
    evidence/07-jantar-teclado.png evidence/falha.png prior-evidence/evidence/falha.png; do
    [ -f "$screen" ] || continue
    java -Djava.awt.headless=true "$temp/Thumbnail.java" "$screen" "$temp/screen.jpg"
    printf 'CAPTURA %s %s\n' "$(basename "$screen")" "$(store_blob "$temp/screen.jpg")"
done
