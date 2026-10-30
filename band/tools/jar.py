"""Fetch the current balance of a public monobank jar and write it to a JSON file the site reads.

Usage: python3 jar.py <short jar id from send.monobank.ua/jar/...> <output json>

Uses monobank's public (undocumented) jar endpoints, the same ones the jar page itself calls.
Amounts are in kopecks. The file only changes when the amount or goal changes.
"""
import json
import secrets
import sys
import urllib.request
from datetime import datetime, timezone

SHORT_ID, OUT = sys.argv[1], sys.argv[2]
HEADERS = {
    "User-Agent": "Mozilla/5.0 (jar balance for a band site)",
    "Accept": "application/json",
    "Content-Type": "application/json",
    "Origin": "https://send.monobank.ua",
    "Referer": f"https://send.monobank.ua/jar/{SHORT_ID}",
}


def request(url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, headers=HEADERS, method="POST" if data else "GET")
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.load(r)


errors, info = [], {}
for body in ({"c": "hello", "clientId": SHORT_ID, "Pc": secrets.token_hex(16)}, {"c": "hello", "clientId": SHORT_ID}):
    try:
        info = request("https://send.monobank.ua/api/handler", body)
        break
    except Exception as e:  # noqa: BLE001 - log and try the next shape
        errors.append(f"handler: {e!r}")
print("handler fields:", sorted(info))

amount, goal, currency = info.get("jarAmount"), info.get("jarGoal"), 980
long_id = info.get("extJarId") or info.get("longJarId")
if long_id:
    try:
        jar = request(f"https://api.monobank.ua/bank/jar/{long_id}")
        print("jar fields:", sorted(jar))
        amount = jar.get("amount", amount)
        goal = jar.get("goal", goal)
        currency = jar.get("currency", currency)
    except Exception as e:  # noqa: BLE001
        errors.append(f"jar: {e!r}")

if amount is None:
    print("\n".join(errors) or "no amount in the responses")
    sys.exit(1)

print(f"amount={amount} goal={goal} currency={currency}")
try:
    with open(OUT) as f:
        old = json.load(f)
except (OSError, ValueError):
    old = {}
if old.get("amount") == amount and old.get("goal") == goal:
    print("unchanged")
    sys.exit(0)
with open(OUT, "w") as f:
    json.dump({"amount": amount, "goal": goal or None, "currency": currency, "longId": long_id,
               "updated": datetime.now(timezone.utc).isoformat(timespec="seconds")}, f, ensure_ascii=False)
    f.write("\n")
print("written")
