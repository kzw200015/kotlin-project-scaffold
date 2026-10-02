// Package config 负责通过 viper 加载配置：配置文件 + 环境变量覆盖。
package config

import (
	"flag"
	"fmt"
	"strings"
	"time"

	"github.com/google/wire"
	"github.com/spf13/viper"
)

var ProviderSet = wire.NewSet(New)

var configPath = flag.String("config", "configs/config.yaml", "path to config file")

type Config struct {
	Server   Server   `mapstructure:"server"`
	Database Database `mapstructure:"database"`
	Log      Log      `mapstructure:"log"`
}

type Server struct {
	Addr            string        `mapstructure:"addr"`
	ReadTimeout     time.Duration `mapstructure:"read_timeout"`
	WriteTimeout    time.Duration `mapstructure:"write_timeout"`
	IdleTimeout     time.Duration `mapstructure:"idle_timeout"`
	ShutdownTimeout time.Duration `mapstructure:"shutdown_timeout"`
	// ClientIPHeader 为反向代理写入真实客户端 IP 的请求头（如 X-Real-IP、CF-Connecting-IP），
	// 代理必须覆盖而非追加该头；留空表示服务直接暴露、使用 RemoteAddr。
	ClientIPHeader string `mapstructure:"client_ip_header"`
}

type Database struct {
	DSN             string        `mapstructure:"dsn"`
	MaxConns        int32         `mapstructure:"max_conns"`
	MinConns        int32         `mapstructure:"min_conns"`
	MaxConnLifetime time.Duration `mapstructure:"max_conn_lifetime"`
	AutoMigrate     bool          `mapstructure:"auto_migrate"`
}

type Log struct {
	Level  string `mapstructure:"level"`  // debug | info | warn | error
	Format string `mapstructure:"format"` // text | json
}

// New 解析命令行参数（-config），并从对应文件加载配置。
func New() (*Config, error) {
	if !flag.Parsed() {
		flag.Parse()
	}
	return Load(*configPath)
}

// Load 读取配置文件，并允许用 APP_ 前缀的环境变量覆盖，
// 例如 APP_DATABASE_DSN 覆盖 database.dsn。
func Load(path string) (*Config, error) {
	v := viper.New()
	setDefaults(v)

	v.SetConfigFile(path)
	v.SetEnvPrefix("APP")
	v.SetEnvKeyReplacer(strings.NewReplacer(".", "_"))
	v.AutomaticEnv()

	if err := v.ReadInConfig(); err != nil {
		return nil, fmt.Errorf("read config %q: %w", path, err)
	}

	var cfg Config
	if err := v.Unmarshal(&cfg); err != nil {
		return nil, fmt.Errorf("unmarshal config: %w", err)
	}
	return &cfg, nil
}

func setDefaults(v *viper.Viper) {
	v.SetDefault("server.addr", ":8080")
	v.SetDefault("server.read_timeout", 10*time.Second)
	v.SetDefault("server.write_timeout", 10*time.Second)
	v.SetDefault("server.idle_timeout", 60*time.Second)
	v.SetDefault("server.shutdown_timeout", 15*time.Second)
	v.SetDefault("server.client_ip_header", "")

	v.SetDefault("database.dsn", "")
	v.SetDefault("database.max_conns", 10)
	v.SetDefault("database.min_conns", 0)
	v.SetDefault("database.max_conn_lifetime", time.Hour)
	v.SetDefault("database.auto_migrate", false)

	v.SetDefault("log.level", "info")
	v.SetDefault("log.format", "text")
}
