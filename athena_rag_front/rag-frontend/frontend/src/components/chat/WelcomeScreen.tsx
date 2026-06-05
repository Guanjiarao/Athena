import * as React from "react";
import { ArrowUpRight, BookOpen, Bot, Brain, Check, Lightbulb, Send, Square } from "lucide-react";

import { cn } from "@/lib/utils";
import { listSampleQuestions } from "@/services/sampleQuestionService";
import { useChatStore } from "@/stores/chatStore";

type PromptPreset = {
  id?: string;
  title: string;
  description: string;
  prompt: string;
  icon: React.ComponentType<{ className?: string }>;
};

const PRESET_ICONS = [BookOpen, Check, Lightbulb];

const DEFAULT_PRESETS: PromptPreset[] = [
  {
    title: "月经周期",
    description: "了解月经周期的变化与健康",
    prompt: "我想了解月经周期相关的知识",
    icon: BookOpen
  },
  {
    title: "备孕指导",
    description: "获取科学的备孕建议",
    prompt: "我想咨询备孕相关的建议",
    icon: Check
  },
  {
    title: "健康咨询",
    description: "女性健康问题解答",
    prompt: "我有一些女性健康方面的问题想咨询",
    icon: Lightbulb
  }
];

export function WelcomeScreen() {
  const [value, setValue] = React.useState("");
  const [isFocused, setIsFocused] = React.useState(false);
  const [promptPresets, setPromptPresets] = React.useState<PromptPreset[]>(DEFAULT_PRESETS);
  const isComposingRef = React.useRef(false);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const { sendMessage, isStreaming, cancelGeneration, deepThinkingEnabled, setDeepThinkingEnabled } =
    useChatStore();

  const focusInput = React.useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.focus({ preventScroll: true });
  }, []);

  const adjustHeight = React.useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    const next = Math.min(el.scrollHeight, 160);
    el.style.height = `${next}px`;
  }, []);

  React.useEffect(() => {
    adjustHeight();
  }, [value, adjustHeight]);

  React.useEffect(() => {
    let active = true;

    const loadPresets = async () => {
      const data = await listSampleQuestions().catch(() => null);
      if (!active || !data || data.length === 0) {
        return;
      }
      const mapped = data
        .filter((item) => item.question && item.question.trim())
        .slice(0, 3)
        .map((item, index) => {
          const question = item.question.trim();
          const title =
            item.title?.trim() ||
            (question.length > 12 ? `${question.slice(0, 12)}...` : question) ||
            `推荐问法 ${index + 1}`;
          const description = item.description?.trim() || "直接点选即可开始对话";
          return {
            id: item.id,
            title,
            description,
            prompt: question,
            icon: PRESET_ICONS[index % PRESET_ICONS.length]
          };
        });
      if (mapped.length > 0) {
        setPromptPresets(mapped);
      }
    };

    loadPresets();
    return () => {
      active = false;
    };
  }, []);

  const applyPreset = React.useCallback(
    (prompt: string) => {
      if (isStreaming) return;
      setValue(prompt);
      focusInput();
    },
    [isStreaming, focusInput]
  );

  const handleSubmit = async () => {
    if (isStreaming) {
      cancelGeneration();
      focusInput();
      return;
    }
    if (!value.trim()) return;
    const next = value;
    setValue("");
    focusInput();
    await sendMessage(next);
    focusInput();
  };

  const hasContent = value.trim().length > 0;

  return (
    <div className="relative flex min-h-full items-center justify-center overflow-hidden px-4 py-16 sm:px-6">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-gradient-to-br from-honey-50 via-warm-50 to-honey-100"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-grid-pattern opacity-30 [background-size:40px_40px]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-32 right-[-40px] h-72 w-72 rounded-full bg-gradient-radial from-honey-300/50 via-transparent to-transparent blur-3xl animate-float"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -bottom-36 left-[-80px] h-80 w-80 rounded-full bg-gradient-radial from-warm-300/40 via-transparent to-transparent blur-3xl animate-float"
        style={{ animationDelay: "2s" }}
      />

      <div className="relative w-full max-w-[860px]">
        <div
          className="text-center opacity-0 animate-fade-up"
          style={{ animationFillMode: "both" }}
        >
          <span className="inline-flex items-center gap-2.5 rounded-full border border-honey-300/60 bg-honey-100/80 px-6 py-2.5 text-sm font-semibold text-warm-800 shadow-sm backdrop-blur">
            <Bot className="h-4 w-4" />
            女性健康科普系统
          </span>
          <h1 className="mt-6 font-display text-4xl leading-tight tracking-tight text-warm-900 sm:text-5xl md:text-6xl">
            把问题变成
            <span className="bg-gradient-sunset bg-clip-text text-transparent"> 清晰答案</span>
          </h1>
          <p className="mt-5 text-base text-warm-700 sm:text-lg">
            用专业知识守护你的健康，每个问题都认真解答
          </p>
        </div>

        <div
          className="mt-10 opacity-0 animate-fade-up"
          style={{ animationDelay: "80ms", animationFillMode: "both" }}
        >
          <div
            className={cn(
              "relative flex flex-col rounded-3xl border bg-honey-50/90 px-5 pt-4 pb-3 shadow-warm backdrop-blur-xl transition-all duration-200",
              isFocused
                ? "border-honey-400 shadow-glow ring-2 ring-honey-300/30"
                : "border-honey-200/80 hover:border-honey-300"
            )}
          >
            <div className="relative">
              <textarea
                ref={textareaRef}
                value={value}
                onChange={(event) => setValue(event.target.value)}
                placeholder={deepThinkingEnabled ? "输入需要深度分析的问题..." : "输入你的问题..."}
                className="max-h-40 min-h-[52px] w-full resize-none border-0 bg-transparent px-2 pt-2 pb-2 text-[15px] text-warm-900 placeholder:text-warm-700/60 focus:outline-none sm:text-base"
                rows={1}
                onFocus={() => setIsFocused(true)}
                onBlur={() => setIsFocused(false)}
                onCompositionStart={() => {
                  isComposingRef.current = true;
                }}
                onCompositionEnd={() => {
                  isComposingRef.current = false;
                }}
                onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.shiftKey) {
                    const nativeEvent = event.nativeEvent as KeyboardEvent;
                    if (nativeEvent.isComposing || isComposingRef.current || nativeEvent.keyCode === 229) {
                      return;
                    }
                    event.preventDefault();
                    handleSubmit();
                  }
                }}
                aria-label="发送消息"
              />
              <div className="pointer-events-none absolute bottom-0 left-0 right-0 h-[10px] bg-gradient-to-b from-honey-50/0 via-honey-50/40 to-honey-50/90" />
            </div>
            <div className="mt-3 flex flex-wrap items-center gap-3">
              <button
                type="button"
                onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)}
                disabled={isStreaming}
                aria-pressed={deepThinkingEnabled}
                className={cn(
                  "rounded-full border px-3 py-1.5 text-xs font-medium transition-all",
                  deepThinkingEnabled
                    ? "border-honey-400 bg-honey-200 text-warm-800 shadow-sm"
                    : "border-transparent bg-warm-100 text-warm-600 hover:bg-warm-200",
                  isStreaming && "cursor-not-allowed opacity-60"
                )}
              >
                <span className="inline-flex items-center gap-2">
                  <Brain className={cn("h-3.5 w-3.5", deepThinkingEnabled && "text-honey-600")} />
                  深度思考
                  {deepThinkingEnabled ? (
                    <span className="h-2 w-2 rounded-full bg-honey-600 animate-pulse" />
                  ) : null}
                </span>
              </button>
              <button
                type="button"
                onClick={handleSubmit}
                disabled={!hasContent && !isStreaming}
                aria-label={isStreaming ? "停止生成" : "发送消息"}
                className={cn(
                  "ml-auto inline-flex items-center justify-center rounded-full p-2.5 transition-all duration-200",
                  isStreaming
                    ? "bg-red-100 text-red-600 hover:bg-red-200"
                    : hasContent
                      ? "bg-gradient-sunset text-white hover:opacity-90 shadow-warm"
                      : "cursor-not-allowed bg-warm-100 text-warm-300"
                )}
              >
                {isStreaming ? <Square className="h-4 w-4" /> : <Send className="h-4 w-4" />}
              </button>
            </div>
          </div>
          {deepThinkingEnabled ? (
            <p className="mt-3 text-xs text-honey-700">
              <span className="inline-flex items-center gap-1.5">
                <Lightbulb className="h-3.5 w-3.5" />
                深度思考模式已开启，AI将进行更深入的分析推理
              </span>
            </p>
          ) : null}
          <p className="mt-3 text-center text-xs text-warm-600">
            <kbd className="rounded border border-honey-200 bg-warm-100 px-1.5 py-0.5 text-warm-800 shadow-sm">
              Enter
            </kbd>{" "}
            发送
            <span className="px-1.5">·</span>
            <kbd className="rounded border border-honey-200 bg-warm-100 px-1.5 py-0.5 text-warm-800 shadow-sm">
              Shift + Enter
            </kbd>{" "}
            换行
            {isStreaming ? <span className="ml-2 animate-pulse-soft">生成中...</span> : null}
          </p>
        </div>

        <div
          className="mt-10 opacity-0 animate-fade-up"
          style={{ animationDelay: "160ms", animationFillMode: "both" }}
        >
          <div className="flex items-center justify-center gap-2 text-xs uppercase tracking-[0.24em] text-warm-700 font-medium">
            <span className="h-px w-8 bg-honey-300" />
            试试这些开场
            <span className="h-px w-8 bg-honey-300" />
          </div>
          <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {promptPresets.map((preset) => {
              const Icon = preset.icon;
              return (
                <button
                  key={preset.id ?? preset.title}
                  type="button"
                  onClick={() => applyPreset(preset.prompt)}
                  disabled={isStreaming}
                  className={cn(
                    "group rounded-2xl border border-honey-200/80 bg-honey-50/80 backdrop-blur p-5 text-left shadow-sm transition-all duration-200 hover:-translate-y-1 hover:border-honey-400 hover:shadow-warm",
                    isStreaming && "cursor-not-allowed opacity-60"
                  )}
                >
                  <div className="flex items-center gap-3">
                    <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-sunset text-white shadow-sm">
                      <Icon className="h-5 w-5" />
                    </span>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold text-warm-900">{preset.title}</p>
                      <p className="text-xs text-warm-600 mt-0.5">{preset.description}</p>
                    </div>
                  </div>
                  <div className="mt-3 flex items-center gap-2 text-xs text-warm-700">
                    <span className="min-w-0 flex-1 truncate">推荐问法：{preset.prompt}</span>
                    <ArrowUpRight className="h-3.5 w-3.5 text-honey-600 transition-colors group-hover:text-honey-700" />
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
