#!/usr/bin/env bash
# Erzeugt eine Wegwerf-Test-CA und zwei Serverzertifikate für GreenMail:
#   valid.p12   – gültig, ausgestellt für "localhost"
#   expired.p12 – seit 2020 abgelaufen, ausgestellt für "localhost"
# Die Schlüssel entstehen bei jedem Lauf neu und werden nie eingecheckt (.gitignore).
set -euo pipefail

OUT="${1:-$(cd "$(dirname "$0")" && pwd)/certs}"
PASS="changeit" # GreenMail-Standardpasswort für den Keystore; schützt nur Testschlüssel.
rm -rf "$OUT"
mkdir -p "$OUT/newcerts"
cd "$OUT"

cat > ca-req.cnf <<'CNF'
[req]
distinguished_name = dn
x509_extensions = v3_ca
prompt = no
[dn]
CN = Email-Rethink Test-CA (nur lokal)
[v3_ca]
basicConstraints = critical,CA:TRUE
keyUsage = critical,keyCertSign,cRLSign
subjectKeyIdentifier = hash
CNF

cat > ca.cnf <<'CNF'
[ca]
default_ca = test
[test]
dir = .
database = ./index.txt
new_certs_dir = ./newcerts
serial = ./serial
default_md = sha256
policy = pol
unique_subject = no
copy_extensions = none
[pol]
commonName = supplied
CNF

cat > server-ext.cnf <<'CNF'
subjectAltName = DNS:localhost
basicConstraints = CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
CNF

: > index.txt
echo 1000 > serial

openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 30 \
  -config ca-req.cnf -keyout ca.key -out ca.pem 2>/dev/null

for name in valid expired; do
  openssl req -newkey rsa:2048 -nodes -keyout "$name.key" -out "$name.csr" \
    -subj "/CN=localhost" 2>/dev/null
done

openssl ca -batch -config ca.cnf -cert ca.pem -keyfile ca.key -notext \
  -days 30 -extfile server-ext.cnf -in valid.csr -out valid.pem 2>/dev/null
openssl ca -batch -config ca.cnf -cert ca.pem -keyfile ca.key -notext \
  -startdate 20200101000000Z -enddate 20200102000000Z \
  -extfile server-ext.cnf -in expired.csr -out expired.pem 2>/dev/null

for name in valid expired; do
  openssl pkcs12 -export -name greenmail -inkey "$name.key" -in "$name.pem" \
    -certfile ca.pem -out "$name.p12" -passout "pass:$PASS"
  chmod 644 "$name.p12"
done

rm -f ./*.csr ./*.cnf index.txt* serial* ca.srl
rm -rf newcerts
echo "Testzertifikate in $OUT erzeugt."
