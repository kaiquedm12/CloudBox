#!/usr/bin/env node

// Creates one demo workload through the real master API. Requires a running agent
// and Docker daemon; it never registers fake nodes or reports invented bindings.
import { setTimeout as delay } from "node:timers/promises";

if (process.argv.includes("--help")) {
  console.log(`Valida publicação de portas com master, agente e Docker reais.

Variáveis:
  CLOUDBOX_ACCESS_TOKEN   JWT de usuário (obrigatório; nunca impresso)
  CLOUDBOX_MASTER_URL     URL do master (padrão http://localhost:8080)
  CLOUDBOX_SMOKE_TIMEOUT  Tempo limite em segundos (padrão 180)

Cria um nginx:alpine com porta 80/TCP HTTP automática, aguarda um endpoint
observado e faz GET nele. Execute de outra máquina para validar acesso remoto.
O container é preservado para demonstração; o ID Docker é exibido para limpeza.`);
  process.exit(0);
}

const token = process.env.CLOUDBOX_ACCESS_TOKEN;
const masterUrl = new URL(process.env.CLOUDBOX_MASTER_URL || "http://localhost:8080");
const timeoutSeconds = Number(process.env.CLOUDBOX_SMOKE_TIMEOUT || 180);
if (!token || !["http:", "https:"].includes(masterUrl.protocol)
    || !Number.isFinite(timeoutSeconds) || timeoutSeconds <= 0) {
  console.error("Informe CLOUDBOX_ACCESS_TOKEN, URL HTTP(S) e timeout positivo. Consulte --help.");
  process.exit(1);
}

async function api(path, options = {}) {
  const response = await fetch(new URL(path, masterUrl), {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) {
    // Do not dump API bodies, which could include sensitive diagnostics.
    throw new Error(`API ${path}: HTTP ${response.status}`);
  }
  return response.json();
}

let createdId;
let dockerId;
try {
  const nodes = await api("/api/nodes");
  if (!nodes.some((node) => node.status === "ONLINE" && node.advertiseAddress)) {
    throw new Error("Nenhum nó ONLINE com advertiseAddress. Configure/inicie um agente antes de criar a demonstração.");
  }
  const created = await api("/api/containers", {
    method: "POST",
    body: JSON.stringify({
      imageName: "nginx:alpine", cpuCores: 1, memoryMb: 256, diskMb: 100,
      ports: [{ containerPort: 80, hostPort: null, protocol: "TCP", exposure: "HTTP" }],
    }),
  });
  createdId = created.id;
  if (!createdId) throw new Error("A API não retornou o ID do container criado.");
  console.log(`Container CloudBox criado: ${createdId}`);
  const deadline = Date.now() + timeoutSeconds * 1000;
  let lastObservation = "aguardando execução";
  while (Date.now() < deadline) {
    const containers = await api("/api/containers");
    const container = containers.find((item) => item.id === createdId);
    if (!container) throw new Error("Container criado não encontrado na listagem.");
    dockerId = container.dockerContainerId;
    if (["ERROR", "FAILED", "STOPPED"].includes(container.status)) {
      throw new Error(`Container terminou com status ${container.status}. Consulte o erro no dashboard.`);
    }
    const endpoint = container.endpoints?.find((item) =>
      item.containerPort === 80 && item.protocol === "TCP" && item.url);
    lastObservation = `status=${container.status}, endpoint=${endpoint ? "recebido" : "pendente"}`;
    if (container.status === "RUNNING" && endpoint) {
      const url = new URL(endpoint.url);
      if (url.protocol !== "http:" || !Number.isInteger(endpoint.hostPort)
          || endpoint.hostPort < 1 || endpoint.hostPort > 65535) {
        throw new Error("Endpoint HTTP/porta retornado pelo master é inválido.");
      }
      try {
        const response = await fetch(url, { signal: AbortSignal.timeout(5_000), redirect: "manual" });
        const body = await response.text();
        if (response.ok && /welcome to nginx/i.test(body)) {
          console.log(`PASS: Nginx respondeu HTTP ${response.status} em ${url.href}`);
          console.log(`ID Docker: ${dockerId || "não informado"}`);
          console.log("Container preservado. O teste comprova acesso a partir desta máquina.");
          process.exit(0);
        }
        lastObservation = `endpoint respondeu HTTP ${response.status}, sem página padrão do Nginx`;
      } catch {
        lastObservation = "endpoint reportado, mas ainda não acessível desta máquina";
      }
    }
    await delay(2_000);
  }
  throw new Error(`Tempo limite: ${lastObservation}. Verifique endereço, binding e firewall.`);
} catch (error) {
  console.error(`FAIL: ${error.message}`);
  if (createdId) console.error(`Container preservado para diagnóstico: ${createdId}`);
  if (dockerId) console.error(`ID Docker: ${dockerId}`);
  process.exitCode = 1;
}
