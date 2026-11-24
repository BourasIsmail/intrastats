import type { NextConfig } from "next";

// L'API reste sur la même origine côté navigateur : Next relaie /api vers le backend.
// (En production, Nginx fait ce relais ; cette règle sert au développement et à `next start`.)
const API_URL = process.env.API_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${API_URL}/api/:path*` }];
  },
};

export default nextConfig;
