// Authoring checks for the proposal. These are not validation in the running API.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { isIP } from "node:net";
import { dirname, posix, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import Ajv from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import { parseDocument } from "yaml";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const schemaPath = "docs/contracts/service-configuration.schema.json";
const readJson = (path) => JSON.parse(readFileSync(resolve(root, path), "utf8"));
const schema = readJson(schemaPath);
const examples = readJson("docs/contracts/stage2-examples.json");
const ajv = new Ajv({ allErrors: true, strict: true });
addFormats(ajv);
ajv.addSchema(schema);
const validators = Object.fromEntries(Object.keys(schema.$defs).map((name) =>
  [name, ajv.compile({ $ref: `${schema.$id}#/$defs/${name}` })]));

function semanticErrors(name, value) {
  const errors = [];
  const check = (condition, code) => { if (!condition) errors.push(code); };
  const unique = (items) => new Set(items).size === items.length;
  const canonical = (path) => path !== "/" && posix.normalize(path) === path && !path.endsWith("/");
  const overlaps = (a, b) => a === b || a.startsWith(`${b}/`) || b.startsWith(`${a}/`);
  if (["ContainerRequest", "PendingStartCommand", "ContainerResponse"].includes(name)) {
    check(value.diskMb == null || value.ephemeralDiskMb == null || value.diskMb === value.ephemeralDiskMb, "DISK_ALIAS_CONFLICT");
    const ports = value.ports ?? [];
    check(unique(ports.map((p) => `${p.containerPort}/${p.protocol ?? "TCP"}`)), "DUPLICATE_PORT");
    for (const p of ports) {
      const protocol = p.protocol ?? "TCP", exposure = p.exposure ?? "INTERNAL";
      check(exposure === "INTERNAL" ? p.hostPort == null && p.bindAddress == null
        : protocol === (exposure === "UDP" ? "UDP" : "TCP"), "PORT_COMBINATION");
      check(p.bindAddress == null || isIP(p.bindAddress) !== 0, "BIND_ADDRESS_INVALID");
    }
    for (const args of [value.command, value.args, value.healthCheck?.exec]) {
      check(args == null || args[0].trim().length > 0, "EMPTY_ARGV_FIRST_ITEM");
    }
    const restart = value.restartPolicy;
    check(!restart || restart.name === "ON_FAILURE" || (restart.maximumRetryCount ?? 0) === 0, "RETRY_POLICY_CONFLICT");
    const probe = value.healthCheck;
    check(!probe || (probe.timeoutSeconds ?? 5) <= (probe.intervalSeconds ?? 30), "PROBE_TIMEOUT_EXCEEDS_INTERVAL");
    if (probe?.type === "HTTP") check(ports.some((p) => p.containerPort === probe.containerPort
      && (p.protocol ?? "TCP") === "TCP" && ["HTTP", "TCP"].includes(p.exposure)), "HTTP_PROBE_REQUIRES_PUBLISHED_TCP");
    const refs = value.secretRefs ?? [], volumes = value.volumes ?? [];
    const envTargets = refs.filter((r) => r.injection === "ENV").map((r) => r.envName);
    const fileTargets = refs.filter((r) => r.injection === "FILE").map((r) => r.filePath);
    check(unique(envTargets) && unique(fileTargets), "DUPLICATE_SECRET_TARGET");
    check(envTargets.every((key) => !Object.hasOwn(value.environment ?? {}, key)), "SECRET_ENV_COLLISION");
    check(unique(volumes.map((v) => v.volumeId)), "DUPLICATE_VOLUME");
    check(volumes.every((v) => canonical(v.target)), "NON_CANONICAL_TARGET");
    const targets = [...volumes.map((v) => v.target), ...fileTargets];
    check(targets.every((a, index) => targets.slice(index + 1).every((b) => !overlaps(a, b))), "MOUNT_TARGET_COLLISION");
    check(Buffer.byteLength(JSON.stringify(value.environment ?? {})) <= 65536, "ENVIRONMENT_TOO_LARGE");
    if (name === "ContainerResponse") {
      check(value.healthCheck != null || value.health.status === "UNKNOWN", "NO_PROBE_NO_HEALTH");
      check(value.status === "RUNNING" || value.health.status === "UNKNOWN", "HEALTH_WITHOUT_RUNNING_PROCESS");
    }
    if (name === "PendingStartCommand") {
      const expected = [];
      if (Object.keys(value.environment).length) expected.push("ENVIRONMENT");
      if (value.command != null || value.args != null) expected.push("COMMAND_ARGS");
      if (value.restartPolicy.name !== "NONE") expected.push("RESTART_POLICY");
      if (envTargets.length) expected.push("SECRET_ENV");
      if (fileTargets.length) expected.push("SECRET_FILE");
      if (volumes.length) expected.push("NAMED_VOLUMES");
      if (probe) expected.push(`HEALTH_${probe.type}`);
      check(JSON.stringify([...value.requiredCapabilities].sort()) === JSON.stringify(expected.sort()), "CAPABILITIES_MISMATCH");
      check(unique(value.resolvedVolumes.map((v) => v.volumeId)) && value.resolvedVolumes.length === volumes.length
        && volumes.every((v) => value.resolvedVolumes.some((r) => r.volumeId === v.volumeId)), "RESOLVED_VOLUMES_MISMATCH");
      check(value.resolvedVolumes.every((v) => v.dockerName === `cloudbox-volume-${v.volumeId}`), "VOLUME_IDENTITY_MISMATCH");
      check(new Set(value.resolvedVolumes.map((v) => v.nodeId)).size <= 1, "VOLUME_NODE_CONFLICT");
    }
  }
  if (["ContainerStatusUpdateRequest", "HealthObservation"].includes(name)) {
    const health = name === "HealthObservation" ? value : value.health;
    if (health) {
      check(health.status === "UNKNOWN" || health.checkedAt != null, "HEALTH_TIMESTAMP_REQUIRED");
      check(!["HEALTHY", "STARTING"].includes(health.status) || health.reasonCode == null, "HEALTH_REASON_CONFLICT");
      check(name === "HealthObservation" || value.status === "RUNNING" || health.status === "UNKNOWN", "HEALTH_WITHOUT_RUNNING_PROCESS");
    }
    if (name === "ContainerStatusUpdateRequest") check(value.status !== "RUNNING" || Boolean(value.dockerContainerId), "RUNNING_REQUIRES_DOCKER_ID");
  }
  if (["VolumeCreateRequest", "VolumeMetadata", "ResolvedVolume"].includes(name)) {
    check(value.diskMode !== "REQUIRED" || value.volumeSizeMb != null, "VOLUME_QUOTA_REQUIRES_SIZE");
    if (name === "VolumeMetadata") {
      check(value.state !== "BOUND" || value.nodeId != null, "BOUND_VOLUME_REQUIRES_NODE");
      check(value.state !== "UNBOUND" || value.nodeId == null, "UNBOUND_VOLUME_HAS_NO_NODE");
    }
  }
  if (name === "SecretCreateRequest") check(Buffer.byteLength(value.value) <= 65536, "SECRET_TOO_LARGE");
  return errors;
}

function errorsFor(name, data) {
  const valid = validators[name](data);
  return valid ? semanticErrors(name, data) : validators[name].errors.map((error) => `SCHEMA:${error.keyword}`);
}

for (const [name, example] of Object.entries(examples)) test(`exemplo: ${name}`, () => {
  assert.deepEqual(errorsFor(example.schema, example.data), []);
});

const reject = (name, example, mutate, expected) => test(`rejeita: ${name}`, () => {
  const { schema, data } = structuredClone(examples[example]);
  mutate(data);
  const errors = errorsFor(schema, data);
  assert(errors.length > 0, "A entrada inválida foi aceita");
  if (expected) assert(errors.includes(expected), errors.join(", "));
});
reject("campo ainda fora da etapa (network)", "legacy", (v) => { v.network = {}; });
reject("alias de disco conflitante", "postgresRequest", (v) => { v.diskMb = 1; }, "DISK_ALIAS_CONFLICT");
reject("disco sem unidade inteira", "legacy", (v) => { v.diskMb = 1.5; });
reject("command em shell implícito", "postgresRequest", (v) => { v.command = "echo hello"; });
reject("command vazio", "postgresRequest", (v) => { v.command = []; });
reject("executável em branco", "postgresRequest", (v) => { v.command = [" "]; }, "EMPTY_ARGV_FIRST_ITEM");
reject("variável com nome inválido", "postgresRequest", (v) => { v.environment["A=B"] = "c"; });
reject("NUL em argumento", "postgresRequest", (v) => { v.args = ["a\u0000b"]; });
reject("segredo inline na referência", "postgresRequest", (v) => { v.secretRefs[0].value = "secret"; });
reject("valor devolvido nos metadados", "secretMetadata", (v) => { v.value = "secret"; });
reject("lista arbitrária na resolução de segredos", "resolveRequest", (v) => { v.secretIds = []; });
reject("destino de segredo repetido", "postgresRequest", (v) => { v.secretRefs.push(v.secretRefs[0]); }, "DUPLICATE_SECRET_TARGET");
reject("colisão environment/secretRefs", "postgresRequest", (v) => {
  v.secretRefs = [{ secretId: v.secretRefs[0].secretId, injection: "ENV", envName: "POSTGRES_USER" }];
}, "SECRET_ENV_COLLISION");
reject("arquivo de segredo fora do diretório", "postgresRequest", (v) => { v.secretRefs[0].filePath = "/etc/passwd"; });
reject("volume com caminho relativo", "postgresRequest", (v) => { v.volumes[0].target = "data"; });
reject("volume com traversal", "postgresRequest", (v) => { v.volumes[0].target = "/data/../etc"; }, "NON_CANONICAL_TARGET");
reject("volume cobre arquivo secreto", "postgresRequest", (v) => { v.volumes[0].target = "/run"; }, "MOUNT_TARGET_COLLISION");
reject("retenção destrutiva", "volumeCreate", (v) => { v.retentionPolicy = "DELETE"; });
reject("quota sem tamanho", "volumeCreate", (v) => { v.diskMode = "REQUIRED"; v.volumeSizeMb = null; }, "VOLUME_QUOTA_REQUIRES_SIZE");
reject("tentativas com restart ALWAYS", "postgresRequest", (v) => { v.restartPolicy = { name: "ALWAYS", maximumRetryCount: 2 }; }, "RETRY_POLICY_CONFLICT");
reject("probe HTTP em porta INTERNAL", "httpProbe", (v) => { v.ports[0].exposure = "INTERNAL"; }, "HTTP_PROBE_REQUIRES_PUBLISHED_TCP");
reject("probe para URL arbitrária", "httpProbe", (v) => { v.healthCheck.path = "http://example.com"; });
reject("probe com destino relativo a host", "httpProbe", (v) => { v.healthCheck.path = "//example.com"; });
reject("probe com timeout superior ao intervalo", "httpProbe", (v) => { v.healthCheck.timeoutSeconds = 11; }, "PROBE_TIMEOUT_EXCEEDS_INTERVAL");
reject("probe EXEC como string", "postgresRequest", (v) => { v.healthCheck.exec = "pg_isready"; });
reject("porta HTTP sobre UDP", "httpProbe", (v) => { v.ports[0].protocol = "UDP"; }, "PORT_COMBINATION");
reject("INTERNAL com hostPort", "postgresRequest", (v) => { v.ports[0].hostPort = 5432; }, "PORT_COMBINATION");
reject("porta duplicada", "httpProbe", (v) => { v.ports.push(v.ports[0]); }, "DUPLICATE_PORT");
reject("RUNNING sem Docker ID", "unhealthyReport", (v) => { delete v.dockerContainerId; }, "RUNNING_REQUIRES_DOCKER_ID");
reject("saúde fictícia sem probe", "postgresResponse", (v) => { v.healthCheck = null; v.health.status = "HEALTHY"; }, "NO_PROBE_NO_HEALTH");
reject("HEALTHY com processo parado", "unhealthyReport", (v) => { v.status = "STOPPED"; v.health = { status: "HEALTHY", checkedAt: "2026-10-06T18:01:00Z", reasonCode: null }; }, "HEALTH_WITHOUT_RUNNING_PROCESS");
reject("saúde observada sem data", "unhealthyReport", (v) => { v.health.checkedAt = null; }, "HEALTH_TIMESTAMP_REQUIRED");
reject("capacidade omitida do comando", "postgresCommand", (v) => { v.requiredCapabilities = []; }, "CAPABILITIES_MISMATCH");
reject("volume não resolvido no comando", "postgresCommand", (v) => { v.resolvedVolumes = []; }, "RESOLVED_VOLUMES_MISMATCH");
reject("nome Docker não corresponde ao UUID", "postgresCommand", (v) => { v.resolvedVolumes[0].dockerName = "cloudbox-volume-90000000-0000-4000-8000-000000000009"; }, "VOLUME_IDENTITY_MISMATCH");

test("aliases iguais e requisição legada com campos nulos são compatíveis", () => {
  const value = structuredClone(examples.legacy.data);
  Object.assign(value, { ephemeralDiskMb: value.diskMb, environment: null, secretRefs: null,
    volumes: null, ports: null, command: null, args: null, healthCheck: null, restartPolicy: null });
  assert.deepEqual(errorsFor("ContainerRequest", value), []);
});

function readYaml(path) {
  const doc = parseDocument(readFileSync(path, "utf8"), { uniqueKeys: true });
  assert.deepEqual(doc.errors, []);
  return doc.toJS();
}
test("OpenAPI vigente e proposta: YAML sem duplicatas e referências locais resolvidas", () => {
  const checked = new Set();
  const walkFile = (path) => {
    if (checked.has(path)) return;
    checked.add(path);
    const document = path.endsWith(".json") ? JSON.parse(readFileSync(path, "utf8")) : readYaml(path);
    const walk = (node) => {
      if (!node || typeof node !== "object") return;
      if (node.$ref) {
        const [file, pointer = ""] = node.$ref.split("#");
        assert(!file.includes(":"), "As referências do contrato devem ser locais");
        const targetPath = file ? resolve(dirname(path), file) : path;
        const target = targetPath === path ? document
          : targetPath.endsWith(".json") ? JSON.parse(readFileSync(targetPath, "utf8")) : readYaml(targetPath);
        const resolved = pointer.split("/").slice(1).reduce((obj, key) => obj?.[key.replaceAll("~1", "/").replaceAll("~0", "~")], target);
        assert.notEqual(resolved, undefined, node.$ref);
        walkFile(targetPath);
      }
      for (const child of Object.values(node)) walk(child);
    };
    walk(document);
  };
  walkFile(resolve(root, "docs/cloudbox-openapi.yaml"));
  walkFile(resolve(root, "docs/cloudbox-stage2-proposal.openapi.yaml"));
});
test("proposta não anuncia rotas/campos novos como API vigente", () => {
  const current = readYaml(resolve(root, "docs/cloudbox-openapi.yaml"));
  const proposal = readYaml(resolve(root, "docs/cloudbox-stage2-proposal.openapi.yaml"));
  assert.equal(proposal["x-implementation-status"], "proposed");
  assert.equal(current["x-stage2-proposal"].status, "proposed-not-implemented");
  assert.equal(current.paths["/api/secrets"], undefined);
  assert.equal(current.components.schemas.ContainerRequest.properties.secretRefs, undefined);
});
