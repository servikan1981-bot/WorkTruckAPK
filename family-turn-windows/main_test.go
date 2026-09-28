package main

import "testing"

func TestValidateConfig(t *testing.T) {
	c := config{PublicIP:"5.227.60.178", Username:"familyvideo", Password:"01234567890123456789012345678901", Port:3478, MinPort:49160, MaxPort:49259}
	if _, err := validate(c); err != nil { t.Fatal(err) }
	c.PublicIP = "192.168.1.139"
	if _, err := validate(c); err == nil { t.Fatal("private advertised address accepted") }
	c.PublicIP = "5.227.60.178"
	c.Password = "short"
	if _, err := validate(c); err == nil { t.Fatal("weak password accepted") }
}
