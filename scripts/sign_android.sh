#!/usr/bin/env bash
set -euo pipefail
umask 077
signing=$(mktemp -d)
trap 'rm -rf -- "$signing"' EXIT

export GTHEDAY_SIGN_PASSWORD
GTHEDAY_SIGN_PASSWORD=$(openssl rand -hex 24)
printf '%s\n' "$GTHEDAY_SIGN_PASSWORD" > "$signing/password.txt"
keytool -genkeypair -storetype PKCS12 -keystore "$signing/gtheday.p12" \
    -storepass:env GTHEDAY_SIGN_PASSWORD -keypass:env GTHEDAY_SIGN_PASSWORD \
    -alias gtheday -keyalg RSA -keysize 3072 -validity 10950 \
    -dname 'CN=GtheDay Android'

tools=$(dirname "$(find "$ANDROID_HOME/build-tools" -type f -name apksigner | sort -V | tail -n 1)")
mkdir -p dist evidence
"$tools/zipalign" -P 16 -f 4 app/build/outputs/apk/release/app-release-unsigned.apk "$signing/aligned.apk"
"$tools/apksigner" sign --ks "$signing/gtheday.p12" --ks-key-alias gtheday \
    --ks-pass env:GTHEDAY_SIGN_PASSWORD --out dist/GtheDay.apk "$signing/aligned.apk"
"$tools/apksigner" verify --verbose --print-certs dist/GtheDay.apk | tee evidence/assinatura.txt
(cd dist && sha256sum GtheDay.apk > GtheDay.apk.sha256)

tar -C "$signing" -cf "$signing/backup.tar" gtheday.p12 password.txt
openssl cms -encrypt -binary -aes256 -in "$signing/backup.tar" \
    -outform DER -out dist/assinatura.cms scripts/signing-transport.pem
