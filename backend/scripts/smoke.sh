#!/usr/bin/env bash
set -euo pipefail
# The running app must point to a disposable local PostgreSQL instance.
# Login responses are redacted before output. Never enable shell tracing here.
base_url=${FINLAB_URL:-http://localhost:8080}
scratch=$(mktemp -d)
trap 'rm -f "$scratch/response" "$scratch/first" "$scratch/login" "$scratch/openapi" "$scratch/swagger" "$scratch/headers" "$scratch/assets" "$scratch/asset" "$scratch/api-headers" "$scratch/request-headers"; rmdir "$scratch"' EXIT

request() {
  local method=$1 path=$2 expected=$3 body=${4:-} key=${5:-}
  local args=(-sS --fail-with-body -D "$scratch/request-headers" -X "$method" "$base_url$path" -H 'Content-Type: application/json' -H 'X-Request-Id: finlab-smoke' -H 'Origin: http://localhost:5173')
  if [[ -n ${token:-} ]]; then args+=(-H "Authorization: Bearer $token"); fi
  if [[ -n $key ]]; then args+=(-H "Idempotency-Key: $key"); fi
  if [[ -n $body ]]; then args+=(--data-binary "$body"); fi
  # curl --fail-with-body exits 22 for intentional 4xx tests; HTTP status is asserted below.
  local status curl_status=0
  status=$(curl "${args[@]}" -o "$scratch/response" -w '%{http_code}' 2>/dev/null) || curl_status=$?
  if [[ $curl_status != 0 && $curl_status != 22 ]]; then
    printf 'FAIL transport: %s %s (curl exit %s)\n' "$method" "$path" "$curl_status" >&2; exit 1
  fi
  if [[ $status != "$expected" ]]; then
    printf 'FAIL %s %s expected %s, received %s\n' "$method" "$path" "$expected" "$status" >&2; exit 1
  fi
  printf 'PASS %s %s -> HTTP %s\n' "$method" "$path" "$status"
  python3 - "$scratch/response" <<'PY'
import json,sys
from pathlib import Path
raw=Path(sys.argv[1]).read_text()
data=json.loads(raw)
if isinstance(data,dict) and 'token' in data:
    data['token']='[REDACTED]'
    print(json.dumps(data,ensure_ascii=False,separators=(',',':')))
else:
    print(raw.strip())
PY
}

login_body=$(python3 - <<'PY'
import json,os
print(json.dumps({'email':os.getenv('FINLAB_EMAIL','demo@finlab.app'),'password':os.getenv('FINLAB_PASSWORD','FinLabDemo!2026')}))
PY
)
printf 'FinLab local HTTP smoke, %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
request GET /actuator/health 200
request POST /v1/auth/login 200 "$login_body"
cp "$scratch/response" "$scratch/login"
token=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["token"])' "$scratch/login")
user_id=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["userId"])' "$scratch/login")
unset login_body
request POST /v1/portfolios 201 "{\"userId\":$user_id,\"name\":\"Local smoke\",\"baseCurrency\":\"USD\"}"
portfolio_id=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' "$scratch/response")
path="/v1/portfolios/$portfolio_id/transactions"
buy="{\"portfolioId\":$portfolio_id,\"type\":\"BUY\",\"symbol\":\"AAPL\",\"qty\":10,\"price\":100,\"fee\":1,\"timestamp\":\"2024-01-02T14:30:00Z\"}"
request POST "$path" 201 "$buy" smoke-buy
cp "$scratch/response" "$scratch/first"
request POST "$path" 201 "$buy" smoke-buy
cmp "$scratch/first" "$scratch/response"
printf 'PASS idempotent replay is byte-for-byte identical\n'
request GET "$path" 200
python3 - "$scratch/first" "$scratch/response" <<'PY'
import json,sys
from decimal import Decimal
from pathlib import Path
created_raw=Path(sys.argv[1]).read_text()
created=json.loads(created_raw,parse_float=Decimal)
listed=json.load(open(sys.argv[2]),parse_float=Decimal)
assert listed==[created], (created,listed)
assert '"qty":10.00000000' in created_raw and '"price":100.0000' in created_raw and '"fee":1.0000' in created_raw, created_raw
PY
printf 'PASS create/read numeric values agree; quantity scale 8 and money scale 4; no exponent notation\n'
request POST "$path" 422 "${buy/\"qty\":10/\"qty\":11}" smoke-buy
request POST "$path" 201 "{\"portfolioId\":$portfolio_id,\"type\":\"SELL\",\"symbol\":\"AAPL\",\"qty\":4,\"price\":110,\"fee\":1,\"timestamp\":\"2024-01-03T14:30:00Z\"}"
request POST "$path" 422 "{\"portfolioId\":$portfolio_id,\"type\":\"SELL\",\"symbol\":\"AAPL\",\"qty\":7,\"price\":110,\"fee\":0,\"timestamp\":\"2024-01-04T14:30:00Z\"}"
request GET "$path" 200
python3 - "$scratch/response" "$scratch/request-headers" <<'PYCOUNT'
import json,sys
rows=json.load(open(sys.argv[1])); headers=open(sys.argv[2]).read().lower()
assert [row['type'] for row in rows]==['SELL','BUY'], rows
assert 'x-total-count: 2' in headers, headers
assert 'x-total-count' in next(line for line in headers.splitlines() if line.startswith('access-control-expose-headers:')), headers
PYCOUNT
printf 'PASS transactions newest first, X-Total-Count=2, count header exposed through CORS\n'
request GET "/v1/portfolios/$portfolio_id/holdings" 200
python3 - "$scratch/response" <<'PY'
import json,sys
holdings=json.load(open(sys.argv[1]))
assert len(holdings)==1 and holdings[0]['shares']==6, holdings
PY
request GET "/v1/metrics/portfolio/$portfolio_id" 200
request DELETE "/v1/portfolios/$portfolio_id" 200
unset token
curl -fsS "$base_url/v3/api-docs" -o "$scratch/openapi"
curl -fsS -D "$scratch/headers" "$base_url/swagger-ui/index.html" -o "$scratch/swagger"
curl -fsS -D "$scratch/api-headers" "$base_url/actuator/health" -o "$scratch/response"
python3 - "$scratch" "$base_url" <<'PY'
import json,sys
from pathlib import Path
from html.parser import HTMLParser
from urllib.parse import urljoin,urlsplit
root=Path(sys.argv[1]); base=sys.argv[2]
document=json.loads((root/'openapi').read_text())
assert document['components']['securitySchemes']['bearerAuth']['scheme']=='bearer'
assert document['paths']['/v1/auth/login']['post']['security']==[]
headers=(root/'headers').read_text().lower()
assert "style-src 'self' 'unsafe-inline'" in headers
assert "'unsafe-inline'" not in (root/'api-headers').read_text().lower()
class Assets(HTMLParser):
    def __init__(self): super().__init__(); self.urls=[]; self.inline_script=False
    def handle_starttag(self,tag,attrs):
        attrs=dict(attrs)
        if tag=='script':
            self.inline_script=not attrs.get('src')
            if attrs.get('src'): self.urls.append(attrs['src'])
        if tag=='link' and attrs.get('rel')=='stylesheet': self.urls.append(attrs['href'])
    def handle_data(self,data):
        assert not self.inline_script or not data.strip(), 'Swagger has inline JavaScript that strict CSP would block'
    def handle_endtag(self,tag):
        if tag=='script': self.inline_script=False
assets=Assets(); assets.feed((root/'swagger').read_text())
assert any('.js' in item for item in assets.urls) and any('.css' in item for item in assets.urls)
urls=[urljoin(base+'/swagger-ui/index.html',item) for item in assets.urls]
assert all(urlsplit(url).netloc==urlsplit(base).netloc for url in urls), urls
(root/'assets').write_text('\n'.join(urls)+'\n')
PY
while IFS= read -r asset_url; do
  curl -fsS "$asset_url" -o "$scratch/asset"
done < "$scratch/assets"
printf 'PASS OpenAPI bearer scheme, public login, Swagger HTML and all referenced JS/CSS assets\n'
printf 'PASS Swagger inline styles permitted; API CSP remains strict; no blocked inline JavaScript\n'
printf 'PASS all local smoke assertions\n'
