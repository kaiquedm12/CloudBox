import { z } from "zod";

export const createContainerSchema = z.object({
  imageName: z
    .string()
    .trim()
    .min(1, "Informe a imagem Docker.")
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
});
