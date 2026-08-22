import * as React from "react";
import { Eye, EyeOff, Lock, User } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { useAuthStore } from "@/stores/authStore";

export function LoginPage() {
  const navigate = useNavigate();
  const { login, isLoading } = useAuthStore();
  const [showPassword, setShowPassword] = React.useState(false);
  const [remember, setRemember] = React.useState(true);
  const [form, setForm] = React.useState({ username: "admin", password: "admin" });
  const [error, setError] = React.useState<string | null>(null);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!form.username.trim() || !form.password.trim()) {
      setError("请输入用户名和密码。");
      return;
    }
    try {
      await login(form.username.trim(), form.password.trim());
      if (!remember) {
        // 如需仅在内存中保存登录态，可在此扩展。
      }
      navigate("/chat");
    } catch (err) {
      setError((err as Error).message || "登录失败，请稍后重试。");
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center px-4 bg-gradient-honey">
      <div className="absolute inset-0 bg-gradient-to-br from-honey-50 via-warm-100 to-honey-200" />
      {/* 装饰性光晕 */}
      <div className="absolute top-20 left-20 w-96 h-96 bg-honey-400 rounded-full mix-blend-multiply filter blur-3xl opacity-20 animate-float" />
      <div className="absolute bottom-20 right-20 w-80 h-80 bg-warm-400 rounded-full mix-blend-multiply filter blur-3xl opacity-20 animate-float" style={{ animationDelay: "2s" }} />

      <div className="relative z-10 w-full max-w-md rounded-3xl border border-honey-300/50 bg-honey-50/90 p-8 shadow-warm backdrop-blur">
        <div className="mb-6">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-sunset mb-4 shadow-glow">
            <svg className="w-8 h-8 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
          </div>
          <p className="font-display text-3xl font-semibold text-warm-900">欢迎回来</p>
          <p className="mt-2 text-sm text-warm-700">
            登录 Athena RAG 开始智能对话
          </p>
        </div>
        <form className="space-y-4" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <label className="text-xs font-semibold uppercase tracking-wide text-warm-700">
              用户名
            </label>
            <div className="relative">
              <User className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-warm-600" />
              <Input
                placeholder="请输入用户名"
                value={form.username}
                onChange={(event) => setForm((prev) => ({ ...prev, username: event.target.value }))}
                className="pl-10 border-honey-300 bg-white/80 focus:border-honey-500 focus:ring-honey-500/20"
                autoComplete="username"
              />
            </div>
          </div>
          <div className="space-y-2">
            <label className="text-xs font-semibold uppercase tracking-wide text-warm-700">
              密码
            </label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-warm-600" />
              <Input
                type={showPassword ? "text" : "password"}
                placeholder="请输入密码"
                value={form.password}
                onChange={(event) => setForm((prev) => ({ ...prev, password: event.target.value }))}
                className="pl-10 pr-10 border-honey-300 bg-white/80 focus:border-honey-500 focus:ring-honey-500/20"
                autoComplete="current-password"
              />
              <button
                type="button"
                onClick={() => setShowPassword((prev) => !prev)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-warm-600 hover:text-warm-800"
                aria-label="显示或隐藏密码"
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>
          <div className="flex items-center justify-between text-sm">
            <label className="flex items-center gap-2 text-warm-700">
              <Checkbox checked={remember} onCheckedChange={(value) => setRemember(Boolean(value))} />
              记住我
            </label>
            <span className="text-xs text-warm-600">账号由管理员初始化</span>
          </div>
          {error ? <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{error}</p> : null}
          <Button type="submit" className="w-full bg-gradient-sunset hover:opacity-90 transition-opacity shadow-warm text-white font-medium" disabled={isLoading}>
            {isLoading ? "正在登录..." : "登录"}
          </Button>
        </form>
      </div>
    </div>
  );
}
