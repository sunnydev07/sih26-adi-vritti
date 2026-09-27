import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  images: { formats: ["image/avif", "image/webp"] },
  experimental: { optimizePackageImports: ["lucide-react", "recharts"] },
};

export default nextConfig;
