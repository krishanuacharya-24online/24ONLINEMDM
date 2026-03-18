package com.e24online.mdm.records;

import java.net.InetAddress;

public record CidrBlock(byte[] network, int prefixLength) {

    public boolean contains(InetAddress address) {
        byte[] candidate = address.getAddress();
        if (candidate.length != network.length) {
            return false;
        }
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (network[i] != candidate[i]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = (0xFF << (8 - remainingBits)) & 0xFF;
        return (network[fullBytes] & mask) == (candidate[fullBytes] & mask);
    }
}