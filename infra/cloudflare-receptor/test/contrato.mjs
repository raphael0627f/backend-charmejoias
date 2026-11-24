// Teste de contrato do receptor contra o Worker LOCAL (wrangler dev --local, D1 local). Nunca fala com a Cloudflare nem com a InfinitePay.
// Uso: npm run db:local && npm run dev (outro terminal) && npm test
// Segredos de teste vêm de .dev.vars (arquivo ignorado pelo git): TOKEN_ENTRADA=<token>[,<token2>] e SEGREDO_ERP=<segredo>.
import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";

const BASE = process.env.RECEPTOR_URL ?? "http://127.0.0.1:8787";
const vars = Object.fromEntries(
  readFileSync(new URL("../.dev.vars", import.meta.url), "utf8").split(/\r?\n/).filter((l) => l.includes("=")).map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
);
const TOKEN = vars.TOKEN_ENTRADA.split(",")[0];
const TOKEN_ANTIGO = vars.TOKEN_ENTRADA.split(",")[1];
const SEGREDO = vars.SEGREDO_ERP;
const auth = { authorization: "Bearer " + SEGREDO };

let falhas = 0;
const conferir = (nome, ok, extra = "") => {
  console.log((ok ? "OK    " : "FALHA ") + nome + (ok ? "" : " " + extra));
  if (!ok) falhas++;
};
const req = (metodo, caminho, { cabecalhos = {}, corpo } = {}) =>
  fetch(BASE + caminho, { method: metodo, headers: { "content-type": "application/json", ...cabecalhos }, body: corpo === undefined ? undefined : typeof corpo === "string" ? corpo : JSON.stringify(corpo) });

const pago = (n, extra = {}) => ({
  event: "paid", invoice_slug: "slug" + n, amount: 1500, paid_amount: 1510, installments: 3, capture_method: "credit_card",
  transaction_nsu: "TX" + Date.now() + "-" + n, order_nsu: "CR-" + n + "-abc", receipt_url: "https://x/r", items: [], ...extra,
});

// --- entrada ---
let r = await req("POST", "/w/token-errado", { corpo: pago(1) });
conferir("token errado = 404 sem corpo", r.status === 404 && (await r.text()) === "");
r = await req("GET", "/w/" + TOKEN);
conferir("GET no webhook = 404", r.status === 404);
const p1 = pago(1);
r = await req("POST", "/w/" + TOKEN, { corpo: p1 });
let j = await r.json();
conferir("paid válido = 200 e guardado", r.status === 200 && j.ok === true && j.duplicado === false, JSON.stringify(j));
r = await req("POST", "/w/" + TOKEN, { corpo: p1 });
j = await r.json();
conferir("reenvio do mesmo transaction_nsu não duplica (200, duplicado)", r.status === 200 && j.duplicado === true);
r = await req("POST", "/w/" + TOKEN, { corpo: { ...p1, event: "refunded" } });
j = await r.json();
conferir("mesmo transaction_nsu com OUTRO evento é item novo", r.status === 200 && j.duplicado === false);
r = await req("POST", "/w/" + TOKEN, { corpo: "isto nao e json" });
conferir("corpo inválido é guardado (200), nunca descartado", r.status === 200);
r = await req("POST", "/w/" + TOKEN, { corpo: JSON.stringify({ event: "paid", order_nsu: "CR-9-x" }) });
conferir("payload sem transaction_nsu é guardado (chave = resumo do corpo)", r.status === 200);
r = await req("POST", "/w/" + TOKEN, { corpo: "x".repeat(17 * 1024) });
conferir("corpo acima de 16 KB = 413", r.status === 413);
if (TOKEN_ANTIGO) {
  r = await req("POST", "/w/" + TOKEN_ANTIGO, { corpo: pago(2) });
  conferir("token antigo (rotação) ainda aceito", r.status === 200);
}

// --- saída (ERP) ---
r = await req("GET", "/fila");
conferir("fila sem Bearer = 401", r.status === 401);
r = await req("GET", "/fila", { cabecalhos: { authorization: "Bearer errado" } });
conferir("fila com segredo errado = 401", r.status === 401);
r = await req("GET", "/fila", { cabecalhos: { authorization: "Bearer " + TOKEN } });
conferir("token de ENTRADA não abre a saída (segredos distintos) = 401", r.status === 401);
r = await req("GET", "/fila?limite=2", { cabecalhos: auth });
j = await r.json();
conferir("fila autenticada respeita o limite e a ordem", r.status === 200 && j.itens.length === 2 && j.itens[0].id < j.itens[1].id, JSON.stringify(j).slice(0, 200));
const primeiro = j.itens[0];
conferir("item traz campos e corpo bruto", primeiro.orderNsu === "CR-1-abc" && primeiro.transactionNsu === p1.transaction_nsu && primeiro.invoiceSlug === "slug1" && JSON.parse(primeiro.corpo).paid_amount === 1510);
r = await req("GET", "/fila?limite=999999", { cabecalhos: auth });
conferir("limite excessivo é limitado (200)", r.status === 200);

r = await req("GET", "/saude", { cabecalhos: auth });
j = await r.json();
const antes = j.pendentes;
conferir("saúde informa pendentes e mais antigo", r.status === 200 && j.ok && antes >= 5 && j.maisAntigoEm && j.versao);
conferir("saúde informa total, confirmados, mais antigo retido e retenção de 90 dias",
  Number.isInteger(j.total) && Number.isInteger(j.confirmados) && j.total === j.pendentes + j.confirmados && j.maisAntigoRetidoEm && j.retencaoDias === 90 && j.versao === "1.2.0", JSON.stringify(j));
const confirmadosAntes = j.confirmados;
const totalAntes = j.total;

r = await req("POST", "/fila/ack", { corpo: { ids: [primeiro.id] } });
conferir("ack sem Bearer = 401", r.status === 401);
r = await req("POST", "/fila/ack", { cabecalhos: auth, corpo: { ids: [] } });
conferir("ack vazio = 400", r.status === 400);
r = await req("POST", "/fila/ack", { cabecalhos: auth, corpo: { ids: ["a"] } });
conferir("ack com id inválido = 400", r.status === 400);
r = await req("POST", "/fila/ack", { cabecalhos: auth, corpo: "nao json" });
conferir("ack com corpo inválido = 400", r.status === 400);
r = await req("POST", "/fila/ack", { cabecalhos: auth, corpo: { ids: [primeiro.id, 999999] } });
j = await r.json();
conferir("ack confirma 1 (id inexistente é ignorado)", r.status === 200 && j.confirmados === 1, JSON.stringify(j));
r = await req("POST", "/fila/ack", { cabecalhos: auth, corpo: { ids: [primeiro.id] } });
j = await r.json();
conferir("ack repetido é idempotente (0 confirmados, 200)", r.status === 200 && j.confirmados === 0);
r = await req("GET", "/saude", { cabecalhos: auth });
j = await r.json();
conferir("pendentes diminuíram em 1", j.pendentes === antes - 1, antes + " -> " + j.pendentes);
conferir("confirmados aumentaram em 1 e o total não mudou", j.confirmados === confirmadosAntes + 1 && j.total === totalAntes, JSON.stringify(j));
conferir("saúde não expõe segredos nem dados de pagamento", !/segredo|token|corpo|nsu/i.test(JSON.stringify(j)));
r = await req("GET", "/fila?limite=200", { cabecalhos: auth });
j = await r.json();
conferir("item confirmado some da fila", !j.itens.some((i) => i.id === primeiro.id));

// --- demais rotas e métodos ---
for (const [m, c] of [["GET", "/"], ["DELETE", "/fila"], ["PUT", "/fila/ack"], ["GET", "/qualquer"], ["POST", "/saude"]]) {
  r = await req(m, c, { cabecalhos: auth });
  conferir(m + " " + c + " = 404", r.status === 404);
}

// --- retenção de 90 dias (apenas itens com ack antigo) ---
const sql = (cmd) => execFileSync(process.execPath, ["node_modules/wrangler/bin/wrangler.js", "d1", "execute", "charme-pagamentos-db", "--local", "--json", "--command", cmd], { encoding: "utf8" });
const antigo = new Date(Date.now() - 91 * 86400000).toISOString();
const recente = new Date(Date.now() - 10 * 86400000).toISOString();
sql("UPDATE fila SET ack_em='" + antigo + "' WHERE id=" + primeiro.id);
const segundo = (await (await req("GET", "/fila?limite=1", { cabecalhos: auth })).json()).itens[0];
await req("POST", "/fila/ack", { cabecalhos: auth, corpo: { ids: [segundo.id] } });
sql("UPDATE fila SET ack_em='" + recente + "' WHERE id=" + segundo.id);
r = await req("POST", "/fila/limpar", { corpo: { dias: 90 } });
conferir("limpar sem Bearer = 401", r.status === 401);
r = await req("POST", "/fila/limpar", { cabecalhos: auth, corpo: { dias: 30 } });
conferir("limpar com dias < 90 = 400", r.status === 400);
r = await req("POST", "/fila/limpar", { cabecalhos: auth, corpo: { dias: "abc" } });
conferir("limpar com dias invalido = 400", r.status === 400);
r = await req("POST", "/fila/limpar", { cabecalhos: auth, corpo: { dias: 90 } });
const lim = await r.json();
conferir("limpar apaga so o ack antigo (1 apagado)", r.status === 200 && lim.ok === true && lim.apagados === 1, JSON.stringify(lim));
const restantes = JSON.parse(sql("SELECT id, ack_em FROM fila"))[0].results;
conferir("ack > 90 dias apagado", !restantes.some((x) => x.id === primeiro.id));
conferir("ack recente (10 dias) preservado", restantes.some((x) => x.id === segundo.id));
conferir("itens SEM ack nunca apagados", restantes.some((x) => x.ack_em === null));

console.log(falhas === 0 ? "TODOS OS TESTES PASSARAM" : falhas + " FALHA(S)");
process.exit(falhas === 0 ? 0 : 1);
