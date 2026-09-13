import { z } from "zod";

const portNumber = (message: string) =>
  z.coerce
    .number({ error: message })
    .int("A porta deve ser um número inteiro.")
    .min(1, "A porta deve estar entre 1 e 65535.")
    .max(65535, "A porta deve estar entre 1 e 65535.");

const ipLiteral = z.union([z.ipv4(), z.ipv6()]);

const optionalPortNumber = z.preprocess(
  (value) => (value === "" || value === null || value === undefined ? null : value),
  portNumber("Informe uma porta do host válida.").nullable(),
);

const optionalBindAddress = z.preprocess(
  (value) => (typeof value === "string" ? value.trim() || null : value),
  z
    .string()
    .refine((value) => ipLiteral.safeParse(value).success, "Use um endereço IPv4 ou IPv6 literal.")
    .nullable(),
);

export const containerPortSchema = z
  .object({
    containerPort: portNumber("Informe a porta interna."),
    hostPort: optionalPortNumber,
    protocol: z.enum(["TCP", "UDP"]).default("TCP"),
    exposure: z.enum(["INTERNAL", "HTTP", "TCP", "UDP"]).default("INTERNAL"),
    bindAddress: optionalBindAddress.default(null),
  })
  .superRefine((port, context) => {
    if (port.exposure === "INTERNAL" && (port.hostPort !== null || port.bindAddress !== null)) {
      context.addIssue({
        code: "custom",
        path: ["hostPort"],
        message: "Portas internas não aceitam porta do host nem endereço de bind.",
      });
    }
    if ((port.exposure === "HTTP" || port.exposure === "TCP") && port.protocol !== "TCP") {
      context.addIssue({
        code: "custom",
        path: ["protocol"],
        message: "Exposição HTTP/TCP exige protocolo TCP.",
      });
    }
    if (port.exposure === "UDP" && port.protocol !== "UDP") {
      context.addIssue({
        code: "custom",
        path: ["protocol"],
        message: "Exposição UDP exige protocolo UDP.",
      });
    }
  });

export const createContainerSchema = z.object({
  imageName: z
    .string()
    .trim()
    .min(1, "Informe a imagem Docker.")
    .regex(/^\S+$/, "O nome da imagem Docker não pode conter espaços.")
    .max(255, "A imagem deve ter no máximo 255 caracteres."),
  cpuCores: z.coerce
    .number({ error: "Informe a quantidade de CPUs." })
    .int("A quantidade de CPUs deve ser um número inteiro.")
    .min(1, "Solicite pelo menos 1 CPU."),
  memoryMb: z.coerce
    .number({ error: "Informe a memória solicitada." })
    .int("A memória deve ser um número inteiro.")
    .min(1, "Solicite pelo menos 1 MB de RAM."),
  diskMb: z.coerce
    .number({ error: "Informe o disco solicitado." })
    .int("O disco deve ser um número inteiro.")
    .min(1, "Solicite pelo menos 1 MB de disco."),
  ports: z.preprocess(
    (value) => (value === null || value === undefined ? [] : value),
    z.array(containerPortSchema).max(32, "Informe no máximo 32 portas."),
  ),
}).superRefine((request, context) => {
  const seen = new Set<string>();
  request.ports.forEach((port, index) => {
    const key = `${port.containerPort}/${port.protocol}`;
    if (seen.has(key)) {
      context.addIssue({
        code: "custom",
        path: ["ports", index, "containerPort"],
        message: "A porta interna e o protocolo não podem ser repetidos.",
      });
    }
    seen.add(key);
  });
});
