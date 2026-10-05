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

// The router may not reflect packets addressed to its own public IPv4 back
// into the LAN. Two allocations on this TURN server must still be able to
// exchange media through their advertised public relay addresses.
type hairpinRelayGenerator struct {
	turn.RelayAddressGenerator
	publicIP net.IP
	minPort  int
	maxPort  int
}

func (g *hairpinRelayGenerator) AllocatePacketConn(c turn.AllocateListenerConfig) (net.PacketConn, net.Addr, error) {
	conn, addr, err := g.RelayAddressGenerator.AllocatePacketConn(c)
	if err != nil { return nil, nil, err }
	return &hairpinPacketConn{PacketConn: conn, publicIP: g.publicIP, minPort: g.minPort, maxPort: g.maxPort}, addr, nil
}

type hairpinPacketConn struct {
	net.PacketConn
	publicIP net.IP
	minPort  int
	maxPort  int
}

func (c *hairpinPacketConn) WriteTo(p []byte, addr net.Addr) (int, error) {
	if peer, ok := addr.(*net.UDPAddr); ok && peer.IP.Equal(c.publicIP) && peer.Port >= c.minPort && peer.Port <= c.maxPort {
		// The relay sockets listen on all local interfaces. Loopback keeps
		// relay-to-relay traffic on this machine instead of relying on NAT hairpin.
		local := *peer
		local.IP = net.IPv4(127, 0, 0, 1)
		local.Zone = ""
		return c.PacketConn.WriteTo(p, &local)
	}
	return c.PacketConn.WriteTo(p, addr)
}

func (c *hairpinPacketConn) ReadFrom(p []byte) (int, net.Addr, error) {
	n, addr, err := c.PacketConn.ReadFrom(p)
	if err != nil { return n, addr, err }
	if peer, ok := addr.(*net.UDPAddr); ok && peer.IP.IsLoopback() && peer.Port >= c.minPort && peer.Port <= c.maxPort {
		// TURN permissions match the advertised public peer address. Restore
		// that address for packets sent by another allocation on this server.
		advertised := *peer
		advertised.IP = c.publicIP
		return n, &advertised, nil
	}
	return n, addr, nil
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

func newServer(c config, ip net.IP, listener net.PacketConn) (*turn.Server, error) {
	base := &turn.RelayAddressGeneratorPortRange{
		RelayAddress: ip,
		Address: "0.0.0.0",
		MinPort: uint16(c.MinPort), MaxPort: uint16(c.MaxPort), MaxRetries: 200,
	}
	return turn.NewServer(turn.ServerConfig{
		Realm: "ourfamily",
		AuthHandler: func(req *turn.RequestAttributes) (string, []byte, bool) {
			if req.Username != c.Username { return "", nil, false }
			return c.Username, turn.GenerateAuthKey(c.Username, "ourfamily", c.Password), true
		},
		PacketConnConfigs: []turn.PacketConnConfig{{
			PacketConn: listener,
			RelayAddressGenerator: &hairpinRelayGenerator{RelayAddressGenerator: base, publicIP: ip, minPort: c.MinPort, maxPort: c.MaxPort},
		}},
	})
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
	server, err := newServer(c, ip, listener)
	if err != nil { log.Fatal(err) }
	log.Printf("Family TURN ready: %s UDP %d, relay UDP %d-%d", ip, c.Port, c.MinPort, c.MaxPort)
	log.Print("Keep this window open. Credentials are in turn-config.json; never publish that file.")
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)
	<-stop
	if err := server.Close(); err != nil { fmt.Fprintln(os.Stderr, err) }
}
