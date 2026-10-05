package main

import (
	"net"
	"testing"
	"time"

	"github.com/pion/turn/v5"
)

func TestValidateConfig(t *testing.T) {
	c := config{PublicIP:"5.227.60.178", Username:"familyvideo", Password:"01234567890123456789012345678901", Port:3478, MinPort:49160, MaxPort:49259}
	if _, err := validate(c); err != nil { t.Fatal(err) }
	c.PublicIP = "192.168.1.139"
	if _, err := validate(c); err == nil { t.Fatal("private advertised address accepted") }
	c.PublicIP = "5.227.60.178"
	c.Password = "short"
	if _, err := validate(c); err == nil { t.Fatal("weak password accepted") }
}

func TestTwoAllocationsExchangePacketsWithoutRouterHairpin(t *testing.T) {
	// The advertised address is intentionally not assigned to this machine.
	// A normal UDP write to that address cannot reach the other allocation.
	publicIP := net.IPv4(5, 227, 60, 178)
	c := config{PublicIP: publicIP.String(), Username: "familyvideo", Password: "01234567890123456789012345678901", Port: 3478, MinPort: 54000, MaxPort: 54100}
	listener, err := net.ListenPacket("udp4", "127.0.0.1:0")
	if err != nil { t.Fatal(err) }
	defer listener.Close()
	server, err := newServer(c, publicIP, listener)
	if err != nil { t.Fatal(err) }
	defer server.Close()

	allocate := func() (net.PacketConn, *turn.Client) {
		t.Helper()
		transport, err := net.ListenPacket("udp4", "127.0.0.1:0")
		if err != nil { t.Fatal(err) }
		client, err := turn.NewClient(&turn.ClientConfig{
			STUNServerAddr: listener.LocalAddr().String(), TURNServerAddr: listener.LocalAddr().String(),
			Conn: transport, Username: c.Username, Password: c.Password, Realm: "ourfamily",
		})
		if err != nil { transport.Close(); t.Fatal(err) }
		if err := client.Listen(); err != nil { client.Close(); transport.Close(); t.Fatal(err) }
		conn, err := client.Allocate()
		if err != nil { client.Close(); transport.Close(); t.Fatal(err) }
		t.Cleanup(func() { conn.Close(); client.Close(); transport.Close() })
		if addr := conn.LocalAddr().(*net.UDPAddr); !addr.IP.Equal(publicIP) { t.Fatalf("advertised relay address: %v", addr) }
		return conn, client
	}
	a, _ := allocate()
	b, _ := allocate()

	// The first send creates A's TURN permission for B. B then replies;
	// subsequent packets must flow in both directions, as WebRTC ICE needs.
	if _, err := a.WriteTo([]byte("warmup"), b.LocalAddr()); err != nil { t.Fatal(err) }
	if _, err := b.WriteTo([]byte("from B"), a.LocalAddr()); err != nil { t.Fatal(err) }
	buf := make([]byte, 64)
	if err := a.SetReadDeadline(time.Now().Add(3 * time.Second)); err != nil { t.Fatal(err) }
	n, from, err := a.ReadFrom(buf)
	if err != nil { t.Fatalf("B -> A relay delivery failed: %v", err) }
	if string(buf[:n]) != "from B" || from.String() != b.LocalAddr().String() { t.Fatalf("B -> A: %q from %v", buf[:n], from) }
	if _, err := a.WriteTo([]byte("from A"), b.LocalAddr()); err != nil { t.Fatal(err) }
	if err := b.SetReadDeadline(time.Now().Add(3 * time.Second)); err != nil { t.Fatal(err) }
	for {
		n, from, err = b.ReadFrom(buf)
		if err != nil { t.Fatalf("A -> B relay delivery failed: %v", err) }
		if string(buf[:n]) != "warmup" { break }
	}
	if string(buf[:n]) != "from A" || from.String() != a.LocalAddr().String() { t.Fatalf("A -> B: %q from %v", buf[:n], from) }
}

func TestTurnAllocation(t *testing.T) {
	c := config{PublicIP:"127.0.0.1", Username:"familyvideo", Password:"01234567890123456789012345678901", Port:3478, MinPort:49160, MaxPort:49259}
	listener, err := net.ListenPacket("udp4", "127.0.0.1:0")
	if err != nil { t.Fatal(err) }
	defer listener.Close()
	server, err := newServer(c, net.IPv4(127,0,0,1), listener)
	if err != nil { t.Fatal(err) }
	defer server.Close()
	transport, err := net.ListenPacket("udp4", "127.0.0.1:0")
	if err != nil { t.Fatal(err) }
	defer transport.Close()
	client, err := turn.NewClient(&turn.ClientConfig{
		STUNServerAddr: listener.LocalAddr().String(), TURNServerAddr: listener.LocalAddr().String(),
		Conn: transport, Username:c.Username, Password:c.Password, Realm:"ourfamily",
	})
	if err != nil { t.Fatal(err) }
	defer client.Close()
	if err := client.Listen(); err != nil { t.Fatal(err) }
	allocation, err := client.Allocate()
	if err != nil { t.Fatal(err) }
	defer allocation.Close()
	address, ok := allocation.LocalAddr().(*net.UDPAddr)
	if !ok || address.Port < c.MinPort || address.Port > c.MaxPort { t.Fatalf("invalid relay address: %v", allocation.LocalAddr()) }
}
