package main

import (
	"net"
	"testing"

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
