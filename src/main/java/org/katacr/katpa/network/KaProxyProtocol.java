package org.katacr.katpa.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/** 实现 KaTpa 后端与 KaProxy 之间的版本化二进制协议。 */
final class KaProxyProtocol {
    static final String CHANNEL = "kaproxy:main";
    private static final int MAGIC = 0x4B415058;
    private static final short VERSION = 1;
    private static final int MAX_PACKET_BYTES = 1_048_576;

    private KaProxyProtocol() {
    }

    /** 编码包含模块和动作信封的数据包。 */
    static byte[] encode(String module, String action, PacketWriter writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            output.writeUTF(module);
            output.writeUTF(action);
            writer.write(output);
        }
        return bytes.toByteArray();
    }

    /** 校验协议标识及版本并返回业务负载。 */
    static Packet decode(byte[] data) throws IOException {
        if (data.length > MAX_PACKET_BYTES) {
            throw new IOException("数据包超过大小限制");
        }
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
        if (input.readInt() != MAGIC) {
            throw new IOException("无效 KaProxy 数据包标识");
        }
        short version = input.readShort();
        if (version != VERSION) {
            throw new IOException("不支持的 KaProxy 协议版本: " + version);
        }
        return new Packet(input.readUTF(), input.readUTF(), input);
    }

    /** 写入 UUID。 */
    static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    /** 读取 UUID。 */
    static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    /** 保存解码后的模块、动作和负载。 */
    record Packet(String module, String action, DataInputStream input) {
    }

    /** 为协议编码器提供可抛出 IO 异常的负载写入动作。 */
    @FunctionalInterface
    interface PacketWriter {
        /** 写入业务负载。 */
        void write(DataOutputStream output) throws IOException;
    }
}
