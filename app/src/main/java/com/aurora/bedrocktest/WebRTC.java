package com.aurora.bedrocktest;

public class WebRTC {
    public String buildCandidate(String ip, String port){
        return "candidate:1 1 udp 114514 " + ip + " " + port + " typ host generation 0 ufrag +2gl network-id 1 network-cost 10";
    }
}
