"use client";

import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

const SEV_COLOR: Record<string, string> = { high: "#f43f5e", medium: "#f59e0b", low: "#6366f1" };

export interface FailureSlice {
  category: string;
  count: number;
  severity: string;
}

/** PFMS rejection-code taxonomy bar chart. Split out so the page paints money cards first. */
export function FailureChart({ data, onSelect }: { data: FailureSlice[]; onSelect: () => void }) {
  return (
    <div className="h-64 w-full" role="img" aria-label="Failure breakdown bar chart">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ left: 20, right: 20 }}>
          <CartesianGrid strokeDasharray="3 3" opacity={0.25} />
          <XAxis type="number" fontSize={12} />
          <YAxis type="category" dataKey="category" fontSize={12} width={130} />
          <Tooltip />
          <Bar dataKey="count" radius={[0, 8, 8, 0]} onClick={onSelect}>
            {data.map((b) => (
              <Cell key={b.category} fill={SEV_COLOR[b.severity] ?? "#6366f1"} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
