import type { Metadata } from "next";

import "./globals.css";

export const metadata: Metadata = {
  title: "讯笺",
  description: "ntfy 消息归档与浏览站点。",
  icons: {
    icon: "/icon.png",
    apple: "/icon.png"
  }
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
