// Package logger 基于 log/slog 构造全局日志器。
package logger

import (
	"log/slog"
	"os"
	"strings"

	"github.com/kzw200015/go-project-template/internal/platform/config"
)

func New(cfg *config.Config) *slog.Logger {
	opts := &slog.HandlerOptions{Level: parseLevel(cfg.Log.Level)}

	var h slog.Handler
	if strings.EqualFold(cfg.Log.Format, "json") {
		h = slog.NewJSONHandler(os.Stdout, opts)
	} else {
		h = slog.NewTextHandler(os.Stdout, opts)
	}

	l := slog.New(h)
	slog.SetDefault(l)
	return l
}

func parseLevel(s string) slog.Level {
	var lv slog.Level
	if err := lv.UnmarshalText([]byte(s)); err != nil {
		return slog.LevelInfo
	}
	return lv
}
