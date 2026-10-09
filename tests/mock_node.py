"""A stand-in Gate^Flame node-agent for the e2e tests.

Mirrors the routes and response shapes of node-agent/gateflame/main.py,
vpn.py and vpngate.py that this app uses — including the quirks that matter:
MACs arrive URL-encoded and are lower-cased, a 401 means a dead token, a PUT
that fails still answers 200 with `lastError`, and vpngate-config is only
available for an enabled vpngate selection.

Test controls (not part of the real API): GET /__log, POST /__reset,
POST /__set {"regionsFail": true, "revoke": true, ...}
"""
import json, sys, urllib.parse as U
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CODE = "482913"
def fresh():
    return {
        "token": None, "log": [], "regionsFail": False, "headscale": True,
        "devs": {},
        "names": {},
        "clients": [
            {"ip": "192.168.124.20", "mac": "aa:bb:cc:00:00:01", "hostname": None, "interface": "eth0", "vendor": "Samsung", "randomisedMac": False},
            {"ip": "192.168.124.21", "mac": "da:aa:bb:00:00:02", "hostname": None, "interface": "eth0", "vendor": None, "randomisedMac": True},
        ],
    }
ST = fresh()

def regions():
    r = []
    if ST["headscale"]:
        r += [{"code": "za", "label": "South Africa", "provider": "headscale", "available": True},
              {"code": "uk", "label": "United Kingdom", "provider": "headscale", "available": False}]
    r += [{"code": c, "label": l, "provider": "vpngate", "available": True, "serverCount": n}
          for c, l, n in [("DE", "Germany", 4), ("JP", "Japan", 30), ("US", "United States", 12)]]
    return r

def label(c):
    n = ST["names"].get(c["mac"])
    return n or (f'{c["vendor"]} {c["mac"][-5:].replace(":", "").upper()}' if c["vendor"] else c["mac"])

class H(BaseHTTPRequestHandler):
    def cors(s):
        s.send_header("Access-Control-Allow-Origin", "*"); s.send_header("Access-Control-Allow-Headers", "*"); s.send_header("Access-Control-Allow-Methods", "*")
    def out(s, code, obj):
        b = json.dumps(obj).encode(); s.send_response(code); s.cors(); s.send_header("Content-Type", "application/json"); s.end_headers(); s.wfile.write(b)
    def do_OPTIONS(s): s.send_response(204); s.cors(); s.end_headers()
    def body(s):
        n = int(s.headers.get("Content-Length") or 0); return json.loads(s.rfile.read(n) or b"{}")
    def authed(s):
        return ST["token"] and s.headers.get("Authorization") == f'Bearer {ST["token"]}'
    def log_message(s, *a): pass
    def route(s, method):
        p = U.urlparse(s.path).path
        b = s.body() if method in ("PUT", "POST") else None
        if not p.startswith("/__"): ST["log"].append({"m": method, "p": p, "b": b, "auth": s.headers.get("Authorization")})
        if p == "/__log": return s.out(200, ST["log"])
        if p == "/__reset": ST.clear(); ST.update(fresh()); return s.out(200, {"ok": True})
        if p == "/__set": ST.update(b or {}); return s.out(200, {"ok": True})
        p = p.removeprefix("/api/v1")
        if p == "/system/status": return s.out(200, {"nodeId": "GF-TEST01", "agentVersion": "0.1.0", "provisioned": True, "storageHealthy": True})
        if p == "/pair/claim" and method == "POST":
            if str(b.get("code")) == CODE:
                ST["token"] = "tok-" + CODE
                return s.out(200, {"deviceToken": ST["token"], "deviceId": "d1", "nodeId": "GF-TEST01", "nodeName": "Gate^Flame Node", "scopes": ["read", "control"]})
            return s.out(401, {"detail": {"error": "invalid_code", "attemptsRemaining": 4}})
        if not s.authed(): return s.out(401, {"detail": {"error": "insufficient_scope", "required": ["read"]}})
        if p == "/vpn/regions" and method == "GET":
            if ST["regionsFail"]: return s.out(500, {"detail": "boom"})
            return s.out(200, {"label": "Gate^Flame Shield", "controlPlaneReachable": ST["headscale"], "vpnGateAvailable": True, "refreshing": False, "regions": regions()})
        if p == "/vpn/continents":
            return s.out(200, {"continents": [
                {"code": "asia", "label": "Asia", "provider": "vpngate", "available": True, "bestCountryCode": "JP", "bestCountryLabel": "Japan", "countryCount": 1, "serverCount": 30},
                {"code": "europe", "label": "Europe", "provider": "vpngate", "available": True, "bestCountryCode": "DE", "bestCountryLabel": "Germany", "countryCount": 1, "serverCount": 4}],
                "vpnGateAvailable": True, "refreshing": False})
        if p == "/vpn/devices" and method == "GET": return s.out(200, {"devices": list(ST["devs"].values())})
        if p == "/clients" and method == "GET":
            return s.out(200, {"clients": [{**c, "ownerName": ST["names"].get(c["mac"]), "label": label(c)} for c in ST["clients"]], "gap": None})
        parts = p.split("/")
        if p.startswith("/vpn/devices/") and p.endswith("/vpngate-config"):
            mac = U.unquote(parts[3]).lower(); d = ST["devs"].get(mac)
            if not d or not d["enabled"] or d.get("provider") != "vpngate" or not d["region"]:
                return s.out(200, {"available": False, "error": "this device has no active VPN Gate selection"})
            return s.out(200, {"available": True, "error": None, "countryCode": d["region"], "countryName": d["region"], "hostname": "vpn123.opengw.net", "ip": "203.0.113.7", "score": 1, "pingMs": 31.0, "speedMbps": 54.2, "configText": "client\ndev tun\n"})
        if p.startswith("/vpn/devices/") and method == "PUT":
            mac = U.unquote(parts[3]).lower()
            err = None
            ST["devs"][mac] = {"mac": mac, "region": b["region"], "enabled": b["enabled"], "provider": b.get("provider", "headscale"), "peerRegistered": False, "updatedAt": 1}
            if b["enabled"] and b["region"] is not None:
                r = {x["code"]: x for x in regions() if x["provider"] == b.get("provider", "headscale")}
                if b["region"] not in r: err = f"'{b['region']}' is not a region this box currently offers"
                elif not r[b["region"]]["available"]: err = f"the exit server for '{b['region']}' is not reachable right now"
                elif b.get("provider") == "headscale": ST["devs"][mac]["peerRegistered"] = True
            return s.out(200, {**ST["devs"][mac], "applying": False, "lastError": err})
        if p.startswith("/clients/") and p.endswith("/name") and method == "PUT":
            mac = U.unquote(parts[2]).lower()
            if b.get("name"): ST["names"][mac] = b["name"]
            else: ST["names"].pop(mac, None)
            return s.out(200, {"clients": ST["clients"]})
        return s.out(404, {"detail": "not found"})
    def do_GET(s): s.route("GET")
    def do_PUT(s): s.route("PUT")
    def do_POST(s): s.route("POST")

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    ThreadingHTTPServer(("127.0.0.1", port), H).serve_forever()
