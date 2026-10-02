package main

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)

	// 不使用 defer：os.Exit 会跳过 defer，这里显式按顺序收尾后再统一退出。
	app, cleanup, err := initApp(ctx)
	if err == nil {
		err = app.Run(ctx)
		cleanup()
	}
	stop()

	if err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(1)
	}
}
