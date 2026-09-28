package main

import (
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"log"
	"net"
	"os"
	"os/signal"
	"strconv"
	"syscall"

	"github.com/pion/turn/v5"
)

type config struct {
	PublicIP string `json:"publicIp"`
	Username string `json:"username"`
	Password string `json:"password"`
	Port     int    `json:"port"`
	MinPort  int    `json:"minPort"`
	MaxPort  int    `json:"maxPort"`
}

func validate(c config) (net.IP, error) {
	ip := net.ParseIP(c.PublicIP)
	if ip == nil || ip.To4() == nil || ip.IsUnspecified() || ip.IsLoopback() || ip.IsPrivate() {
		return nil, errors.New("publicIp must be a public IPv4 address")
	}
	if len(c.Username) < 4 || len(c.Username) > 50 || len(c.Password) < 24 {
		return nil, errors.New("username or password invalid (password needs at least 24 characters)")
	}
	if c.Port < 1 || c.Port > 65535 || c.MinPort < 1024 || c.MaxPort > 65535 || c.MaxPort-c.MinPort < 63 || c.Port >= c.MinPort && c.Port <= c.MaxPort {
		return nil, errors.New("invalid UDP listen/relay port range")
	}
	return ip.To4(), nil
}

func main() {
	path := flag.String("config", "turn-config.json", "local configuration file")
	flag.Parse()
	bytes, err := os.ReadFile(*path)
	if err != nil { log.Fatal(err) }
	var c config
	if err := json.Unmarshal(bytes, &c); err != nil { log.Fatal(err) }
	ip, err := validate(c)
	if err != nil { log.Fatal(err) }
	listener, err := net.ListenPacket("udp4", net.JoinHostPort("0.0.0.0", strconv.Itoa(c.Port)))
	if err != nil { log.Fatal(err) }
	defer listener.Close()
	server, err := turn.NewServer(turn.ServerConfig{
		Realm: "ourfamily",
		AuthHandler: func(req *turn.RequestAttributes) (string, []byte, bool) {
			if req.Username != c.Username { return "", nil, false }
			return c.Username, turn.GenerateAuthKey(c.Username, "ourfamily", c.Password), true
		},
		PacketConnConfigs: []turn.PacketConnConfig{{
			PacketConn: listener,
			RelayAddressGenerator: &turn.RelayAddressGeneratorPortRange{
				RelayAddress: ip,
				Address: "0.0.0.0",
				MinPort: uint16(c.MinPort), MaxPort: uint16(c.MaxPort), MaxRetries: 200,
			},
		}},
	})
	if err != nil { log.Fatal(err) }
	log.Printf("Family TURN ready: %s UDP %d, relay UDP %d-%d", ip, c.Port, c.MinPort, c.MaxPort)
	log.Print("Keep this window open. Credentials are in turn-config.json; never publish that file.")
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)
	<-stop
	if err := server.Close(); err != nil { fmt.Fprintln(os.Stderr, err) }
}
