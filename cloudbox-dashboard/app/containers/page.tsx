import type { Metadata } from "next";
import { ContainersOverview } from "@/components/containers-overview";

export const metadata: Metadata = {
  title: "Containers",
};

export default function ContainersPage() {
  return <ContainersOverview />;
}
