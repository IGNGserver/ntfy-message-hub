import type { NextConfig } from "next";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const appVersion = readFileSync(resolve(process.cwd(), "..", "VERSION"), "utf8").trim();

const nextConfig: NextConfig = {
  env: {
    NEXT_PUBLIC_APP_VERSION: appVersion
  }
};

export default nextConfig;
