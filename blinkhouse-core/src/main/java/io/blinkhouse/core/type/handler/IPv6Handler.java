package io.blinkhouse.core.type.handler;

import io.blinkhouse.core.protocol.ChInputStream;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Handles ClickHouse {@code IPv6} ↔ Java {@link Inet6Address}.
 *
 * <p>Wire format: 16 bytes in big-endian (network byte order). ClickHouse stores
 * IPv6 addresses in network byte order, which is the same order that
 * {@link Inet6Address#getAddress()} returns.
 */
public final class IPv6Handler implements TypeHandler<Inet6Address> {

    @Override
    public String clickHouseTypeName() {
        return "IPv6";
    }

    @Override
    public void write(ChOutputStream out, Inet6Address value) throws IOException {
        byte[] addr = value.getAddress(); // 16 bytes, network (big-endian) order
        out.writeBytes(addr);
    }

    @Override
    public Inet6Address read(ChInputStream in) throws IOException {
        byte[] addr = in.readBytes(16);
        try {
            return (Inet6Address) InetAddress.getByAddress(addr);
        } catch (UnknownHostException e) {
            // getByAddress only throws if length != 4 or 16 — we always pass 16 bytes
            throw new IOException("Failed to create Inet6Address from 16-byte address", e);
        }
    }
}
