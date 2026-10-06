#!/usr/bin/env node

// Integration acceptance against disposable PostgreSQL and the real Docker daemon.
// Only this run's processes and containers are removed; logs/evidence are retained.
import assert from "node:assert/strict";
import { execFile, spawn } from "node:child_process";
import { randomBytes, randomUUID } from "node:crypto";
import { closeSync, openSync } from "node:fs";
import { access, mkdtemp, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

if (process.argv.includes("--help")) {
  console.log(`Aceite local da etapa 1 com PostgreSQL, master, agente e Docker reais.

Pré-requisitos: Node.js 22+, Java 21, Docker local acessível e JARs atualizados:
  ./mvnw -B -pl cloudbox-master,cloudbox-agent -am package
  node scripts/accept-service-ports.mjs

Usa o contexto Docker selecionado (ou DOCKER_HOST), que deve usar socket Unix
local. Baixa postgres:16 e nginx:alpine. Cria banco temporário sem volume
persistente, credenciais aleatórias, master/agente locais e quatro workloads.
Todas as portas publicadas ficam em 127.0.0.1. Remove somente os recursos deste
ensaio, inclusive em falha; mantém logs privados e evidence.json em /tmp.
Não comprova acesso de outra máquina, interface do dashboard ou recuperação
da identidade do agente. Não usa o banco, credenciais ou master de produção.`);
  process.exit(0);
}
if (process.argv.length > 2) throw new Error("Argumento desconhecido. Consulte --help.");

const run = promisify(execFile);
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const runId = randomUUID();
const runLabel = `com.cloudbox.acceptance-run=${runId}`;
const directory = await mkdtemp(join(tmpdir(), "cloudbox-ports-"));
const credentials = {
  POSTGRES_PASSWORD: randomBytes(24).toString("hex"),
  ADMIN_EMAIL: "acceptance@cloudbox.test",
  ADMIN_PASSWORD: randomBytes(24).toString("hex"),
  JWT_SECRET: randomBytes(48).toString("base64"),
};
const evidence = {
  runId, startedAt: new Date().toISOString(), scope: "local-loopback",
  checks: [], workloads: [],
  limitations: ["Sem acesso de outra máquina", "Sem navegador/dashboard",
    "Sem teste de reinício do agente ou admissão de nós"],
};
const children = new Set();
let masterUrl;
let token;
let interrupted = false;
for (const signal of ["SIGINT", "SIGTERM"]) process.on(signal, () => { interrupted = true; });

async function command(file, args, options = {}) {
  const result = await run(file, args, {
    cwd: root, timeout: 120_000, maxBuffer: 2 * 1024 * 1024, ...options,
  });
  return result.stdout.trim();
}
const docker = (args, options) => command("docker", args, options);

async function eventually(description, check, seconds = 90) {
  const deadline = Date.now() + seconds * 1000;
  while (Date.now() < deadline) {
    if (interrupted) throw new Error("Ensaio interrompido");
    const result = await check();
    if (result) return result;
    await delay(500);
  }
  throw new Error(`Timeout: ${description}. Consulte os logs em ${directory}`);
}

function launch(module, env, logName) {
  const logPath = join(directory, logName);
  const fd = openSync(logPath, "a", 0o600);
  // Limit inherited settings so an existing datasource/profile cannot select user data.
  const child = spawn("java", [module === "cloudbox-master" ? "-Xmx256m" : "-Xmx128m",
    "-jar", join(root, module, "target", `${module}-0.1.0-SNAPSHOT.jar`),
    "--spring.config.location=classpath:/application.yml"], {
    cwd: directory,
    env: { PATH: process.env.PATH, JAVA_HOME: process.env.JAVA_HOME,
      LANG: process.env.LANG, ...env },
    stdio: ["ignore", fd, fd],
  });
  closeSync(fd);
  child.on("error", (error) => { child.launchError = error; });
  children.add(child);
  return child;
}

function assertAlive(child, name) {
  if (child.launchError) throw child.launchError;
  assert(child.exitCode === null && child.signalCode === null, `${name} encerrou; consulte ${directory}`);
}

async function stop(child) {
  if (!child.pid) {
    children.delete(child);
    return;
  }
  if (child.exitCode === null && child.signalCode === null) {
    child.kill("SIGTERM");
    const exited = new Promise((done) => child.once("exit", done));
    const result = await Promise.race([exited.then(() => true), delay(15_000, false, { ref: false })]);
    if (!result) {
      child.kill("SIGKILL");
      await exited;
    }
  }
  children.delete(child);
}

async function api(path, { method = "GET", body, expected = 200, authenticated = true } = {}) {
  const response = await fetch(new URL(path, masterUrl), {
    method, headers: { "Content-Type": "application/json",
      ...(authenticated ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(5_000), redirect: "error",
  });
  assert.equal(response.status, expected, `${method} ${path}: HTTP ${response.status}, esperado ${expected}`);
  return response.status === 204 ? null : response.json();
}

function pass(name, details = {}) {
  evidence.checks.push({ name, ...details });
  console.log(`PASS: ${name}`);
}

async function inspect(id) {
  return JSON.parse(await docker(["inspect", id]))[0];
}

async function workload(name, ports, expectedStatus = "RUNNING") {
  await eventually("nó elegível: CPU >= 1, RAM >= 128 MB, disco >= 32 MB, temperatura < 75 °C", async () => {
    const nodes = await api("/api/nodes");
    evidence.lastNodeResources = nodes.map(({ id, status, cpuFree, ramFreeMb, diskFreeMb, temperatureCelsius }) =>
      ({ id, status, cpuFree, ramFreeMb, diskFreeMb, temperatureCelsius }));
    return nodes.some((node) => node.status === "ONLINE" && node.advertiseAddress
      && node.cpuFree >= 1 && node.ramFreeMb >= 128 && node.diskFreeMb >= 32
      && (node.temperatureCelsius == null || node.temperatureCelsius < 75));
  });
  const created = await api("/api/containers", {
    method: "POST", expected: 201,
    body: { imageName: "nginx:alpine", cpuCores: 1, memoryMb: 128, diskMb: 32,
      ...(ports === undefined ? {} : { ports }) },
  });
  assert.match(created.id, /^[0-9a-f-]{36}$/);
  const record = { name, id: created.id };
  evidence.workloads.push(record);
  await writeEvidence();
  return eventually(name, async () => {
    const container = (await api("/api/containers")).find((item) => item.id === created.id);
    assert(container, `${name}: registro ausente`);
    if (container.status === "PENDING") return false;
    assert.equal(container.status, expectedStatus, `${name}: estado inesperado; consulte o log do agente`);
    Object.assign(record, { status: container.status, dockerId: container.dockerContainerId,
      endpoints: container.endpoints });
    return container;
  });
}

async function writeEvidence() {
  await writeFile(join(directory, "evidence.json"), `${JSON.stringify(evidence, null, 2)}\n`, { mode: 0o600 });
}

async function cleanup() {
  for (const child of [...children].reverse()) await stop(child);
  // Match both a UUID returned by our private master and the agent ownership label.
  for (const workload of evidence.workloads) {
    const ids = await docker(["ps", "-aq", "--filter", "label=com.cloudbox.managed=true",
      "--filter", `label=com.cloudbox.container-id=${workload.id}`]);
    for (const id of ids.split(/\s+/).filter(Boolean)) await docker(["rm", "-f", id]);
  }
  const databases = await docker(["ps", "-aq", "--filter", `label=${runLabel}`]);
  for (const id of databases.split(/\s+/).filter(Boolean)) await docker(["rm", "-f", id]);
}

console.log(`Evidências e logs privados: ${directory}`);
await writeEvidence();
try {
  for (const module of ["cloudbox-master", "cloudbox-agent"]) {
    await access(join(root, module, "target", `${module}-0.1.0-SNAPSHOT.jar`));
  }
  evidence.commit = await command("git", ["rev-parse", "HEAD"]);
  evidence.dirtyWorktree = Boolean(await command("git", ["status", "--porcelain"]));
  const contextHost = () => docker(["context", "inspect", "--format", "{{.Endpoints.docker.Host}}"]);
  const dockerHost = process.env.DOCKER_CONTEXT ? await contextHost() : process.env.DOCKER_HOST || await contextHost();
  assert(dockerHost.startsWith("unix:///"), "Use um daemon Docker local com socket Unix; ensaio publica em loopback");
  evidence.dockerVersion = await docker(["info", "--format", "{{.ServerVersion}}"]);
  for (const image of ["postgres:16", "nginx:alpine"]) {
    console.log(`Preparando ${image}...`);
    await docker(["pull", image], { timeout: 300_000 });
    evidence[image] = JSON.parse(await docker(["image", "inspect", image, "--format", "{{json .RepoDigests}}"]));
  }
  const databaseId = await docker(["run", "-d", "--name", `cloudbox-acceptance-${runId}`,
    "--label", runLabel, "--tmpfs", "/var/lib/postgresql/data",
    "-p", "127.0.0.1::5432", "-e", "POSTGRES_PASSWORD", "-e", "POSTGRES_USER=cloudbox",
    "-e", "POSTGRES_DB=cloudbox", "postgres:16"], {
    env: { ...process.env, POSTGRES_PASSWORD: credentials.POSTGRES_PASSWORD },
  });
  evidence.databaseId = databaseId;
  await writeEvidence();
  const postgresPort = (await inspect(databaseId)).NetworkSettings.Ports["5432/tcp"][0].HostPort;
  await eventually("PostgreSQL pronto", async () => {
    try {
      await docker(["exec", databaseId, "pg_isready", "-h", "127.0.0.1", "-U", "cloudbox", "-d", "cloudbox"]);
      return true;
    } catch { return false; }
  });
  const masterEnv = { ...credentials, PORT: "0", SERVER_ADDRESS: "127.0.0.1",
    SPRING_DATASOURCE_URL: `jdbc:postgresql://127.0.0.1:${postgresPort}/cloudbox`,
    SPRING_DATASOURCE_USERNAME: "cloudbox", SPRING_DATASOURCE_PASSWORD: credentials.POSTGRES_PASSWORD };
  async function startMaster(logName) {
    const child = launch("cloudbox-master", masterEnv, logName);
    masterUrl = await eventually("master pronto", async () => {
      assertAlive(child, "Master");
      const log = await readFile(join(directory, logName), "utf8");
      const match = log.match(/Tomcat started on port (\d+)/);
      if (!match) return false;
      const url = `http://127.0.0.1:${match[1]}`;
      try {
        const response = await fetch(`${url}/actuator/health`, { signal: AbortSignal.timeout(2000) });
        return response.ok && (await response.json()).status === "UP" ? url : false;
      } catch { return false; }
    });
    return child;
  }
  let master = await startMaster("master.log");
  token = (await api("/api/auth/login", { method: "POST", authenticated: false,
    body: { email: credentials.ADMIN_EMAIL, password: credentials.ADMIN_PASSWORD } })).token;
  assert(token, "Login sem token");
  const migrations = await docker(["exec", databaseId, "psql", "-U", "cloudbox", "-d", "cloudbox",
    "-Atc", "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank"]);
  assert.deepEqual(migrations.split("\n"), ["1", "2", "3", "4", "5", "6", "7", "8", "9"]);
  pass("Migrations V1–V9 e login em PostgreSQL real", { migrations: migrations.split("\n") });

  const agent = launch("cloudbox-agent", { CLOUDBOX_MASTER_URL: masterUrl,
    AGENT_NAME: `acceptance-${runId}`, AGENT_TOKEN_FILE: join(directory, "agent.properties"),
    AGENT_ADVERTISE_ADDRESS: "127.0.0.1", AGENT_AUTO_DETECT_ADVERTISE_ADDRESS: "false",
    AGENT_PORT_BIND_ADDRESS: "127.0.0.1", DOCKER_HOST: dockerHost }, "agent.log");
  const node = await eventually("nó ONLINE", async () => {
    assertAlive(agent, "Agente");
    return (await api("/api/nodes")).find((item) => item.status === "ONLINE");
  });
  evidence.nodeId = node.id;
  await api(`/api/nodes/${node.id}/heartbeat`, { method: "POST", authenticated: false, expected: 401,
    body: { cpuFree: 1, ramFreeMb: 256, diskFreeMb: 100, temperatureCelsius: null } });
  pass("Agente real ONLINE; heartbeat sem token rejeitado");
  const httpPort = { containerPort: 80, hostPort: null, protocol: "TCP", exposure: "HTTP" };
  const published = await workload("HTTP com porta automática", [httpPort]);
  const endpoint = published.endpoints.find((item) => item.containerPort === 80 && item.protocol === "TCP");
  assert(endpoint, "Endpoint HTTP ausente");
  assert.equal(endpoint.address, "127.0.0.1");
  assert(Number.isInteger(endpoint.hostPort) && endpoint.hostPort > 0 && endpoint.hostPort <= 65535);
  assert.equal(endpoint.url, `http://127.0.0.1:${endpoint.hostPort}`);
  const dockerContainer = await inspect(published.dockerContainerId);
  assert(dockerContainer.NetworkSettings.Ports["80/tcp"].some((binding) =>
    binding.HostIp === endpoint.address && Number(binding.HostPort) === endpoint.hostPort));
  await eventually("GET Nginx", async () => {
    try {
      const response = await fetch(endpoint.url, { signal: AbortSignal.timeout(2000), redirect: "error" });
      return response.status === 200 && /welcome to nginx/i.test(await response.text());
    } catch { return false; }
  });
  pass("Nginx HTTP 200 na porta automática inspecionada no Docker", { endpoint });

  const conflict = await workload("Porta explícita ocupada", [{ ...httpPort, hostPort: endpoint.hostPort }], "ERROR");
  assert.deepEqual(conflict.endpoints, []);
  assert.match(conflict.errorMessage, /port|porta|bind|address/i);
  pass("Porta explícita ocupada produz ERROR, mensagem de binding e nenhum endpoint");
  for (const [name, ports] of [["Requisição legada sem portas", undefined],
    ["Exposição INTERNAL", [{ containerPort: 80, protocol: "TCP", exposure: "INTERNAL" }]]]) {
    const container = await workload(name, ports);
    assert.deepEqual(container.endpoints, []);
    const inspected = await inspect(container.dockerContainerId);
    assert.equal(Object.keys(inspected.HostConfig.PortBindings ?? {}).length, 0);
    assert(Object.values(inspected.NetworkSettings.Ports ?? {}).every((bindings) => !bindings?.length));
    pass(`${name}: RUNNING sem publicação no host`);
  }

  const beforeRestart = await api("/api/containers");
  await stop(agent);
  await stop(master);
  master = await startMaster("master-restarted.log");
  const afterRestart = await api("/api/containers");
  const normalize = (items) => [...items].sort((a, b) => a.id.localeCompare(b.id));
  assert.deepEqual(normalize(afterRestart), normalize(beforeRestart));
  assert.equal((await api("/api/nodes")).find((item) => item.id === node.id).advertiseAddress, "127.0.0.1");
  pass("Portas, endpoints, erros e endereço persistem após reinício do master");
  evidence.result = "PASS";
} catch (error) {
  evidence.result = "FAIL";
  // Do not serialize execFile errors (they may contain command output or secrets).
  evidence.failure = error.code && error.code !== "ERR_ASSERTION"
    ? `Falha de comando/arquivo (${error.code}); consulte logs locais` : error.message;
  console.error(`FAIL: ${evidence.failure}`);
  process.exitCode = 1;
} finally {
  try {
    await cleanup();
    evidence.cleanup = "PASS";
    console.log("Recursos temporários deste ensaio removidos; imagens, logs e evidências preservados.");
  } catch {
    evidence.cleanup = "FAIL";
    process.exitCode = 1;
    console.error(`Limpeza incompleta. Use os IDs/labels de ${directory}/evidence.json para conferir os recursos.`);
  }
  evidence.finishedAt = new Date().toISOString();
  await writeEvidence();
  console.log(`Resultado: ${evidence.result}; evidências: ${directory}/evidence.json`);
}
