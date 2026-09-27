"use client";

import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip, Area, AreaChart, CartesianGrid, XAxis, YAxis } from "recharts";
import { formatPaiseCompact } from "@/lib/utils";

const COLORS = ["#312E81", "#6366F1", "#F59E0B", "#10B981", "#8B5CF6"];

export function SchemeDonut({ data }: { data: { scheme: string; count: number }[] }) {
  return (
    <div className="h-56 w-full" role="img" aria-label="Scheme distribution donut chart">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie data={data} dataKey="count" nameKey="scheme" innerRadius={55} outerRadius={85} paddingAngle={3}>
            {data.map((_, i) => (
              <Cell key={i} fill={COLORS[i % COLORS.length]} />
            ))}
          </Pie>
          <Tooltip formatter={(v) => (typeof v === "number" ? v.toLocaleString("en-IN") : String(v ?? ""))} />
        </PieChart>
      </ResponsiveContainer>
      <div className="mt-1 flex flex-wrap gap-2">
        {data.map((d, i) => (
          <span key={d.scheme} className="flex items-center gap-1.5 text-xs">
            <span className="h-2.5 w-2.5 rounded-full" style={{ background: COLORS[i % COLORS.length] }} />
            {d.scheme} · {d.count.toLocaleString("en-IN")}
          </span>
        ))}
      </div>
    </div>
  );
}

export function DisbursementTrend({ data }: { data: { month: string; sanctionedPaise: number; paidPaise: number }[] }) {
  const rows = data.map((d) => ({
    month: d.month,
    sanctioned: Math.round(d.sanctionedPaise / 100),
    paid: Math.round(d.paidPaise / 100),
  }));
  return (
    <div className="h-56 w-full" role="img" aria-label="Disbursement trend area chart">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={rows} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" opacity={0.25} />
          <XAxis dataKey="month" fontSize={12} />
          <YAxis fontSize={12} tickFormatter={(v: number) => formatPaiseCompact(v * 100)} />
          <Tooltip formatter={(v) => (typeof v === "number" ? formatPaiseCompact(v * 100) : String(v ?? ""))} />
          <Area type="monotone" dataKey="sanctioned" stroke="#6366F1" fill="#6366F1" fillOpacity={0.18} name="Sanctioned" />
          <Area type="monotone" dataKey="paid" stroke="#10B981" fill="#10B981" fillOpacity={0.22} name="Paid" />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
