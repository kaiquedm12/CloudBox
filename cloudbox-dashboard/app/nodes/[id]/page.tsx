import type { Metadata } from "next";
import { NodeDetail } from "@/components/node-detail";

export const metadata: Metadata = {
  title: "Detalhes do nó",
};

export default async function NodePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <NodeDetail nodeId={id} />;
}
