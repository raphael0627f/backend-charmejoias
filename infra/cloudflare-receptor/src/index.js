// Receptor de pagamentos (Cloudflare Worker + D1) do ERP Charme Joias.
// Faz SOMENTE duas coisas: receber o webhook da InfinitePay e guardar numa fila; entregar a fila ao ERP (que busca por requisição de saída).
// NÃO decide nada, NÃO chama a InfinitePay e NÃO conhece a chave dela. Quem confirma o pagamento (payment_check) e baixa é o ERP.
//
// Rotas:
//   POST /w/{token}        InfinitePay -> guarda o evento (idempotente por transaction_nsu + evento). 404 se o token estiver errado.
//   GET  /fila?limite=N    ERP (Bearer SEGREDO_ERP) -> itens ainda sem ack, mais antigos primeiro.
//   POST /fila/ack         ERP (Bearer) -> {"ids":[...]} marca como confirmados (idempotente).
//   GET  /saude            ERP (Bearer) -> {ok, pendentes, maisAntigoEm, versao}.
// Segredos (Cloudflare secrets): TOKEN_ENTRADA (aceita vários separados por vírgula, para rotação) e SEGREDO_ERP.

const VERSAO = "1.0.0";
const MAX_CORPO = 16 * 1024;
const LIMITE_PADRAO = 50;
const LIMITE_MAXIMO = 200;
const RETENCAO_DIAS = 90;

const json = (dados, status = 200) =>
  new Response(JSON.stringify(dados), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });
const naoEncontrado = () => new Response(null, { status: 404 });
const naoAutorizado = () => json({ erro: "nao autorizado" }, 401);

// comparação em tempo constante (resumos SHA-256 de mesmo tamanho)
async function iguais(a, b) {
  const enc = new TextEncoder();
  const [ha, hb] = await Promise.all([crypto.subtle.digest("SHA-256", enc.encode(a ?? "")), crypto.subtle.digest("SHA-256", enc.encode(b ?? ""))]);
  const x = new Uint8Array(ha);
  const y = new Uint8Array(hb);
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  return diff === 0;
}

async function sha256Hex(texto) {
  const h = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(texto)));
  return Array.from(h, (b) => b.toString(16).padStart(2, "0")).join("");
}

async function tokenValido(token, env) {
  const aceitos = String(env.TOKEN_ENTRADA ?? "").split(",").map((t) => t.trim()).filter(Boolean);
  let ok = false;
  for (const t of aceitos) if (await iguais(token, t)) ok = true; // percorre todos: sem atalho
  return ok;
}

async function erpAutorizado(request, env) {
  const cab = request.headers.get("authorization") ?? "";
  if (!env.SEGREDO_ERP || !cab.startsWith("Bearer ")) return false;
  return iguais(cab.slice(7), env.SEGREDO_ERP);
}

const texto = (v) => (typeof v === "string" && v.trim() !== "" ? v.trim().slice(0, 200) : typeof v === "number" ? String(v) : null);

// extrai os campos de roteamento do payload; QUALQUER formato é guardado (o ERP valida e manda o inesperado para revisão)
function extrair(corpo) {
  let dados = null;
  try {
    dados = JSON.parse(corpo);
  } catch {
    dados = null;
  }
  const o = dados && typeof dados === "object" && !Array.isArray(dados) ? dados : {};
  return {
    evento: texto(o.event) ?? texto(o.type) ?? texto(o.evento) ?? (dados ? "desconhecido" : "invalido"),
    orderNsu: texto(o.order_nsu),
    transactionNsu: texto(o.transaction_nsu),
    invoiceSlug: texto(o.invoice_slug) ?? texto(o.slug),
  };
}

async function receber(request, env) {
  const url = new URL(request.url);
  const token = decodeURIComponent(url.pathname.slice("/w/".length));
  if (!(await tokenValido(token, env))) return naoEncontrado();
  const bruto = await request.arrayBuffer();
  if (bruto.byteLength > MAX_CORPO) return json({ erro: "corpo muito grande" }, 413);
  const corpo = new TextDecoder().decode(bruto);
  const campos = extrair(corpo);
  // sem transaction_nsu a chave de idempotência é o resumo do corpo (o item nunca é descartado)
  const chave = campos.transactionNsu ?? "sha256:" + (await sha256Hex(corpo));
  const agora = new Date().toISOString();
  const r = await env.DB.prepare(
    "INSERT OR IGNORE INTO fila (recebido_em, evento, order_nsu, transaction_nsu, invoice_slug, corpo) VALUES (?, ?, ?, ?, ?, ?)",
  )
    .bind(agora, campos.evento, campos.orderNsu, chave, campos.invoiceSlug, corpo)
    .run();
  return json({ ok: true, duplicado: (r.meta?.changes ?? 0) === 0 });
}

async function listar(request, env) {
  const url = new URL(request.url);
  let limite = parseInt(url.searchParams.get("limite") ?? String(LIMITE_PADRAO), 10);
  if (!Number.isFinite(limite) || limite < 1) limite = LIMITE_PADRAO;
  limite = Math.min(limite, LIMITE_MAXIMO);
  const { results } = await env.DB.prepare(
    "SELECT id, recebido_em, evento, order_nsu, transaction_nsu, invoice_slug, corpo FROM fila WHERE ack_em IS NULL ORDER BY id ASC LIMIT ?",
  )
    .bind(limite)
    .all();
  return json({
    itens: results.map((r) => ({
      id: r.id,
      recebidoEm: r.recebido_em,
      evento: r.evento,
      orderNsu: r.order_nsu,
      transactionNsu: r.transaction_nsu,
      invoiceSlug: r.invoice_slug,
      corpo: r.corpo,
    })),
  });
}

async function confirmar(request, env) {
  let corpo;
  try {
    corpo = await request.json();
  } catch {
    return json({ erro: "corpo invalido" }, 400);
  }
  const ids = Array.isArray(corpo?.ids) ? corpo.ids : null;
  if (!ids || ids.length === 0 || ids.length > LIMITE_MAXIMO || !ids.every((i) => Number.isInteger(i) && i > 0)) {
    return json({ erro: "informe de 1 a " + LIMITE_MAXIMO + " ids inteiros positivos" }, 400);
  }
  const agora = new Date().toISOString();
  const marcadores = ids.map(() => "?").join(",");
  const r = await env.DB.prepare("UPDATE fila SET ack_em = ? WHERE ack_em IS NULL AND id IN (" + marcadores + ")")
    .bind(agora, ...ids)
    .run();
  return json({ ok: true, confirmados: r.meta?.changes ?? 0 });
}

async function saude(env) {
  const p = await env.DB.prepare("SELECT COUNT(*) AS n, MIN(recebido_em) AS mais_antigo FROM fila WHERE ack_em IS NULL").first();
  return json({ ok: true, pendentes: p?.n ?? 0, maisAntigoEm: p?.mais_antigo ?? null, versao: VERSAO });
}

async function limpar(env, agora = new Date()) {
  const limite = new Date(agora.getTime() - RETENCAO_DIAS * 24 * 3600 * 1000).toISOString();
  const r = await env.DB.prepare("DELETE FROM fila WHERE ack_em IS NOT NULL AND ack_em < ?").bind(limite).run();
  return r.meta?.changes ?? 0;
}

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url);
    try {
      if (request.method === "POST" && pathname.startsWith("/w/")) return await receber(request, env);
      const rotaErp =
        (request.method === "GET" && (pathname === "/fila" || pathname === "/saude")) || (request.method === "POST" && pathname === "/fila/ack");
      if (!rotaErp) return naoEncontrado();
      if (!(await erpAutorizado(request, env))) return naoAutorizado();
      if (pathname === "/fila") return await listar(request, env);
      if (pathname === "/saude") return await saude(env);
      return await confirmar(request, env);
    } catch (e) {
      // nunca devolve detalhes internos nem o corpo recebido
      console.error("falha no receptor", e?.name);
      return json({ erro: "falha interna" }, 500);
    }
  },

  // retenção: itens confirmados (ack) há mais de 90 dias são apagados; itens SEM ack nunca são apagados automaticamente
  async scheduled(_evento, env, ctx) {
    ctx.waitUntil(limpar(env));
  },
};
