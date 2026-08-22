/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: ["class"],
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        card: "hsl(var(--card))",
        "card-foreground": "hsl(var(--card-foreground))",
        popover: "hsl(var(--popover))",
        "popover-foreground": "hsl(var(--popover-foreground))",
        primary: "hsl(var(--primary))",
        "primary-foreground": "hsl(var(--primary-foreground))",
        secondary: "hsl(var(--secondary))",
        "secondary-foreground": "hsl(var(--secondary-foreground))",
        muted: "hsl(var(--muted))",
        "muted-foreground": "hsl(var(--muted-foreground))",
        accent: "hsl(var(--accent))",
        "accent-foreground": "hsl(var(--accent-foreground))",
        destructive: "hsl(var(--destructive))",
        "destructive-foreground": "hsl(var(--destructive-foreground))",
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        "chat-user": "hsl(var(--chat-user))",
        "chat-assistant": "hsl(var(--chat-assistant))",
        // 暖黄色主题扩展色
        honey: {
          50: "#FFF9F0",
          100: "#FFF5E6",
          200: "#FFEFD5",
          300: "#FFE8C5",
          400: "#FFDEAD",
          500: "#FFB84D",
          600: "#F59E0B",
          700: "#D97706",
          800: "#B45309",
          900: "#92400E",
        },
        warm: {
          50: "#FFFAF4",
          100: "#FFF4E0",
          200: "#FFEFD5",
          300: "#FFE4B5",
          400: "#FFD699",
          500: "#FFCF40",
          600: "#C77700",
          700: "#A36200",
          800: "#7F4E00",
          900: "#5C3900",
        },
      },
      fontFamily: {
        display: ["'Fraunces'", "'Georgia'", "serif"],
        body: ["'Inter Variable'", "'Inter'", "ui-sans-serif", "system-ui"],
        mono: ["'JetBrains Mono'", "'Fira Code'", "ui-monospace", "monospace"],
      },
      boxShadow: {
        soft: "0 24px 60px -30px rgba(121, 85, 72, 0.4)",
        glow: "0 0 0 1px rgba(245, 158, 11, 0.3), 0 16px 40px rgba(245, 158, 11, 0.3)",
        neon: "0 0 30px rgba(255, 191, 64, 0.5)",
        warm: "0 8px 32px rgba(245, 158, 11, 0.2)",
      },
      keyframes: {
        "fade-up": {
          "0%": { opacity: 0, transform: "translateY(10px)" },
          "100%": { opacity: 1, transform: "translateY(0)" }
        },
        "pulse-soft": {
          "0%, 100%": { opacity: 1 },
          "50%": { opacity: 0.5 }
        },
        "blink": {
          "0%, 100%": { opacity: 1 },
          "50%": { opacity: 0 }
        },
        "spin-slow": {
          "0%": { transform: "rotate(0deg)" },
          "100%": { transform: "rotate(360deg)" }
        },
        "glow": {
          "0%, 100%": { opacity: 0.5, filter: "drop-shadow(0 0 8px rgba(255, 191, 64, 0.5))" },
          "50%": { opacity: 1, filter: "drop-shadow(0 0 16px rgba(255, 191, 64, 0.8))" }
        },
        "float": {
          "0%, 100%": { transform: "translateY(0)" },
          "50%": { transform: "translateY(-6px)" }
        },
        "shimmer": {
          "0%": { backgroundPosition: "-200% 0" },
          "100%": { backgroundPosition: "200% 0" }
        }
      },
      animation: {
        "fade-up": "fade-up 0.35s ease-out",
        "pulse-soft": "pulse-soft 1.4s ease-in-out infinite",
        "blink": "blink 1s step-end infinite",
        "spin-slow": "spin-slow 4s linear infinite",
        "glow": "glow 2.6s ease-in-out infinite",
        "float": "float 6s ease-in-out infinite",
        "shimmer": "shimmer 3s linear infinite"
      },
      backgroundImage: {
        "gradient-radial": "radial-gradient(var(--tw-gradient-stops))",
        "gradient-warm": "linear-gradient(135deg, #FFE8C5 0%, #FFD699 100%)",
        "gradient-sunset": "linear-gradient(135deg, #FFB84D 0%, #F59E0B 50%, #D97706 100%)",
        "gradient-honey": "linear-gradient(180deg, #FFF9F0 0%, #FEF3C7 100%)",
        "grid-pattern":
          "linear-gradient(rgba(245, 158, 11, 0.08) 1px, transparent 1px), linear-gradient(90deg, rgba(245, 158, 11, 0.08) 1px, transparent 1px)",
        "noise": "url(\"data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' /%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)' opacity='0.05'/%3E%3C/svg%3E\")"
      }
    }
  },
  plugins: [require("@tailwindcss/typography")]
};
