import type { Metadata } from "next";
import { NodesOverview } from "@/components/nodes-overview";

export const metadata: Metadata = {
  title: "Visão geral",
};

export default function Home() {
  return <NodesOverview />;
}
