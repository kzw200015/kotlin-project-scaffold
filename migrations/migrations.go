// Package migrations 内嵌全部 goose 迁移文件，同时作为 sqlc 的 schema 来源。
package migrations

import "embed"

//go:embed *.sql
var FS embed.FS
