"""Simulador LOCAL da API de checkout da InfinitePay (somente para testes; nenhum dado real).
Uso: python infinitepay-simulador.py [porta]   (padrao 9099)
Tambem simula: POST /payment_check (por transaction_nsu), o RECEPTOR (GET /fila, POST /fila/ack, POST /fila/limpar, GET /saude; Bearer segredo-simulador-local)
e POST /__pagamento (cria pagamento + aviso na fila). Modo "receptor-fora" faz o receptor responder 503; modo numerico tambem vale para payment_check.
Endpoint simulado: POST /links  (exige o cabecalho X-Api-Key; qualquer valor nao vazio e aceito)
Controle (sem autenticacao, so localhost):
  GET  /__modo                  -> modo atual e contagem de requisicoes
  POST /__modo  {"modo": "..."} -> troca o comportamento. Modos:
        ok        200 {"url": "https://checkout.simulado.local/<order_nsu>"}   (padrao)
        aninhado  200 {"data": {"checkout_url": "..."}}
        texto     200 text/plain com a URL
        semurl    200 {"mensagem": "criado"}
        401 | 403 | 422 | 400 | 429 | 500 | 503   responde com esse status e um corpo sigiloso falso
        timeout   demora 40 s (acima do limite de 20 s do backend)
        lento     demora 3 s e responde ok
  GET  /__requisicoes           -> ultimas requisicoes recebidas (cabecalho X-Api-Key mascarado)
  POST /__reset                 -> zera o registro e volta ao modo ok
"""
import json, sys, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ESTADO = {"modo": "ok", "requisicoes": []}
# pagamentos simulados (para o payment_check) e a fila do receptor simulado (para o ERP buscar)
PAGAMENTOS = {}
FILA = []  # itens: {id, recebidoEm, evento, orderNsu, transactionNsu, invoiceSlug, corpo, ack}
SEQ = [0]
SEGREDO_ERP = "segredo-simulador-local"


class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _responder(self, status, corpo, tipo="application/json"):
        dados = corpo if isinstance(corpo, bytes) else corpo.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(dados)))
        self.end_headers()
        self.wfile.write(dados)

    def _corpo(self):
        if (self.headers.get("Transfer-Encoding") or "").lower() == "chunked":
            partes = b""
            while True:
                tam = int(self.rfile.readline().strip().split(b";")[0] or b"0", 16)
                if tam == 0:
                    self.rfile.readline()
                    break
                partes += self.rfile.read(tam)
                self.rfile.readline()
            return partes.decode("utf-8")
        n = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(n).decode("utf-8") if n else ""

    def log_message(self, *a):
        pass

    def do_GET(self):
        if self.path == "/__modo":
            return self._responder(200, json.dumps({"modo": ESTADO["modo"], "requisicoes": len(ESTADO["requisicoes"])}))
        if self.path == "/__requisicoes":
            return self._responder(200, json.dumps(ESTADO["requisicoes"][-50:], ensure_ascii=False))
        if self.path.startswith("/fila") or self.path == "/saude":
            return self._receptor("GET", "")
        self._responder(404, "{}")

    def _receptor(self, metodo, corpo):
        """Receptor SIMULADO (mesmo contrato do Worker): /fila, /fila/ack, /fila/limpar, /saude com Bearer = SEGREDO_ERP."""
        if (self.headers.get("Authorization") or "") != "Bearer " + SEGREDO_ERP:
            return self._responder(401, json.dumps({"erro": "nao autorizado"}))
        if ESTADO["modo"] == "receptor-fora":
            return self._responder(503, "{}")
        caminho = self.path.split("?")[0]
        if metodo == "GET" and caminho == "/saude":
            pend = [i for i in FILA if not i["ack"]]
            return self._responder(200, json.dumps({"ok": True, "pendentes": len(pend), "maisAntigoEm": pend[0]["recebidoEm"] if pend else None, "versao": "sim"}))
        if metodo == "GET" and caminho == "/fila":
            itens = [{k: v for k, v in i.items() if k != "ack"} for i in FILA if not i["ack"]][:200]
            return self._responder(200, json.dumps({"itens": itens}))
        if metodo == "POST" and caminho == "/fila/ack":
            ids = json.loads(corpo or "{}").get("ids", [])
            n = 0
            for i in FILA:
                if i["id"] in ids and not i["ack"]:
                    i["ack"] = True
                    n += 1
            return self._responder(200, json.dumps({"ok": True, "confirmados": n}))
        if metodo == "POST" and caminho == "/fila/limpar":
            antes = len(FILA)
            FILA[:] = [i for i in FILA if not (i["ack"] and i.get("antigo"))]
            return self._responder(200, json.dumps({"ok": True, "apagados": antes - len(FILA)}))
        return self._responder(404, "{}")

    def do_POST(self):
        corpo = self._corpo()
        if self.path.startswith("/fila"):
            return self._receptor("POST", corpo)
        if self.path == "/__modo":
            ESTADO["modo"] = json.loads(corpo or "{}").get("modo", "ok")
            return self._responder(200, json.dumps({"modo": ESTADO["modo"]}))
        if self.path == "/__reset":
            ESTADO["modo"] = "ok"
            ESTADO["requisicoes"].clear()
            PAGAMENTOS.clear()
            FILA.clear()
            return self._responder(200, json.dumps({"modo": "ok"}))
        if self.path == "/__pagamento":
            # cria um pagamento simulado: {"order_nsu","transaction_nsu","amount","paid_amount","capture_method","installments","paid"(padrao true),"event"(padrao paid),"webhook"(padrao true)}
            d = json.loads(corpo or "{}")
            tx = d.get("transaction_nsu") or "SIM-TX-" + str(int(time.time() * 1000))
            PAGAMENTOS[tx] = {"success": True, "paid": d.get("paid", True), "amount": d["amount"], "paid_amount": d.get("paid_amount", d["amount"]),
                              "installments": d.get("installments", 1), "capture_method": d.get("capture_method", "pix")}
            if d.get("webhook", True):
                SEQ[0] += 1
                evento = d.get("event", "paid")
                payload = {"event": evento, "invoice_slug": "sim-slug", "amount": d["amount"], "paid_amount": d.get("paid_amount", d["amount"]),
                           "installments": d.get("installments", 1), "capture_method": d.get("capture_method", "pix"), "transaction_nsu": tx,
                           "order_nsu": d["order_nsu"], "receipt_url": "https://recibo.simulado.local/" + tx, "items": []}
                FILA.append({"id": SEQ[0], "recebidoEm": time.strftime("%Y-%m-%dT%H:%M:%SZ"), "evento": evento, "orderNsu": d["order_nsu"], "transactionNsu": tx,
                             "invoiceSlug": "sim-slug", "corpo": json.dumps(payload), "ack": False})
            return self._responder(200, json.dumps({"transaction_nsu": tx}))
        if self.path == "/payment_check":
            p = json.loads(corpo or "{}")
            ESTADO["requisicoes"].append({"quando": time.strftime("%H:%M:%S"), "modo": ESTADO["modo"], "payment_check": {k: p.get(k) for k in ("handle", "order_nsu", "transaction_nsu")}})
            if ESTADO["modo"].isdigit():
                return self._responder(int(ESTADO["modo"]), json.dumps({"erro": "DETALHE-INTERNO-SIGILOSO-DO-SIMULADOR"}))
            r = PAGAMENTOS.get(p.get("transaction_nsu"))
            return self._responder(200, json.dumps(r if r else {"success": False, "paid": False}))
        if self.path != "/links":
            return self._responder(404, "{}")
        chave = self.headers.get("X-Api-Key") or ""
        try:
            pedido = json.loads(corpo)
        except Exception:
            pedido = None
        ESTADO["requisicoes"].append({"quando": time.strftime("%H:%M:%S"), "modo": ESTADO["modo"], "chaveRecebida": bool(chave),
                                      "chaveFinal": chave[-4:] if chave else None, "pedido": pedido})
        modo = ESTADO["modo"]
        if not chave:
            return self._responder(401, json.dumps({"erro": "sem chave"}))
        nsu = (pedido or {}).get("order_nsu", "sem-nsu")
        url = "https://checkout.simulado.local/" + nsu
        if modo in ("ok", "lento"):
            if modo == "lento":
                time.sleep(3)
            return self._responder(200, json.dumps({"url": url}))
        if modo == "aninhado":
            return self._responder(200, json.dumps({"data": {"checkout_url": url}}))
        if modo == "texto":
            return self._responder(200, url, "text/plain")
        if modo == "semurl":
            return self._responder(200, json.dumps({"mensagem": "criado"}))
        if modo == "timeout":
            time.sleep(40)
            return self._responder(200, json.dumps({"url": url}))
        if modo.isdigit():
            return self._responder(int(modo), json.dumps({"erro": "DETALHE-INTERNO-SIGILOSO-DO-SIMULADOR", "chave": chave}))
        self._responder(500, "{}")


if __name__ == "__main__":
    porta = int(sys.argv[1]) if len(sys.argv) > 1 else 9099
    ThreadingHTTPServer(("127.0.0.1", porta), H).serve_forever()
