package com.swiftroute.orderservice.grpc.routing;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest.LocationPoint;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest.StopItem;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationResponse;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationResponse.OrderedStop;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteInsertRequest;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteInsertResponse;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteReoptimizeRequest;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteReoptimizeResponse;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteStopState;

/**
 * Pure Java 21 gRPC HTTP/2 (h2c) + Protocol Buffers v3 wire implementation for
 * {@code com.swiftroute.routing.v1.RoutingEngineGrpcService} defined in {@code routing.proto}.
 *
 * <p>Speaks standard binary {@code application/grpc+proto} over HTTP/2 directly with
 * Python's C-core {@code grpcio} server without requiring native {@code protoc} plugins.
 */
public final class RoutingGrpcProtocol {

    public static final String SERVICE_NAME = "com.swiftroute.routing.v1.RoutingEngineGrpcService";
    public static final String OPTIMIZE_BATCH_PATH = "/" + SERVICE_NAME + "/OptimizeBatch";
    public static final String OPTIMIZE_INSERT_PATH = "/" + SERVICE_NAME + "/OptimizeInsert";
    public static final String OPTIMIZE_REOPTIMIZE_PATH = "/" + SERVICE_NAME + "/OptimizeReoptimize";

    private static final byte[] HTTP2_PREFACE =
            "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    private static final byte FRAME_DATA = 0x00;
    private static final byte FRAME_HEADERS = 0x01;
    private static final byte FRAME_RST_STREAM = 0x03;
    private static final byte FRAME_SETTINGS = 0x04;
    private static final byte FRAME_PING = 0x06;
    private static final byte FRAME_GOAWAY = 0x07;

    private static final byte FLAG_END_STREAM = 0x01;
    private static final byte FLAG_ACK = 0x01;
    private static final byte FLAG_END_HEADERS = 0x04;
    private static final byte FLAG_PADDED = 0x08;

    private static final int WIRETYPE_VARINT = 0;
    private static final int WIRETYPE_FIXED64 = 1;
    private static final int WIRETYPE_LENGTH_DELIMITED = 2;
    private static final int WIRETYPE_FIXED32 = 5;

    private RoutingGrpcProtocol() {}

    // =========================================================================
    // High-level Unary gRPC Calls
    // =========================================================================

    public static BatchOptimizationResponse callOptimizeBatch(
            String host, int port, BatchOptimizationRequest request, String internalKey, Duration timeout)
            throws IOException {
        byte[] reqBytes = encodeBatchRequest(request);
        byte[] respBytes = invokeUnaryGrpc(host, port, OPTIMIZE_BATCH_PATH, reqBytes, internalKey, timeout);
        return decodeBatchResponse(respBytes);
    }

    public static RouteInsertResponse callOptimizeInsert(
            String host, int port, RouteInsertRequest request, String internalKey, Duration timeout)
            throws IOException {
        byte[] reqBytes = encodeInsertRequest(request);
        byte[] respBytes = invokeUnaryGrpc(host, port, OPTIMIZE_INSERT_PATH, reqBytes, internalKey, timeout);
        return decodeInsertResponse(respBytes);
    }

    public static RouteReoptimizeResponse callOptimizeReoptimize(
            String host, int port, RouteReoptimizeRequest request, String internalKey, Duration timeout)
            throws IOException {
        byte[] reqBytes = encodeReoptimizeRequest(request);
        byte[] respBytes = invokeUnaryGrpc(host, port, OPTIMIZE_REOPTIMIZE_PATH, reqBytes, internalKey, timeout);
        return decodeReoptimizeResponse(respBytes);
    }

    // =========================================================================
    // HTTP/2 (h2c) + gRPC Length-Prefixed Framing Transport
    // =========================================================================

    public static byte[] invokeUnaryGrpc(
            String host,
            int port,
            String methodPath,
            byte[] protobufPayload,
            String internalKey,
            Duration timeout) throws IOException {
        int timeoutMs = (int) Math.min(Integer.MAX_VALUE, timeout.toMillis());
        try (Socket socket = new Socket()) {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            OutputStream out = socket.getOutputStream();
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // 1. Send HTTP/2 client connection preface + empty SETTINGS frame
            out.write(HTTP2_PREFACE);
            writeFrame(out, FRAME_SETTINGS, (byte) 0, 0, new byte[0]);

            // 2. Build HPACK headers for stream 1
            byte[] hpackBlock = buildGrpcRequestHeaders(host + ":" + port, methodPath, internalKey);
            writeFrame(out, FRAME_HEADERS, FLAG_END_HEADERS, 1, hpackBlock);

            // 3. Build 5-byte gRPC frame (1-byte compression=0 + 4-byte length) + protobuf payload
            ByteBuffer grpcFrame = ByteBuffer.allocate(5 + protobufPayload.length);
            grpcFrame.order(ByteOrder.BIG_ENDIAN);
            grpcFrame.put((byte) 0); // uncompressed
            grpcFrame.putInt(protobufPayload.length);
            grpcFrame.put(protobufPayload);
            writeFrame(out, FRAME_DATA, FLAG_END_STREAM, 1, grpcFrame.array());
            out.flush();

            // 4. Read HTTP/2 frames until stream 1 ends
            ByteArrayOutputStream dataAccum = new ByteArrayOutputStream();
            boolean streamEnded = false;
            while (!streamEnded) {
                int b0 = in.read();
                if (b0 < 0) {
                    throw new IOException("Unexpected EOF from gRPC server before END_STREAM");
                }
                int b1 = in.readUnsignedByte();
                int b2 = in.readUnsignedByte();
                int length = (b0 << 16) | (b1 << 8) | b2;
                byte type = in.readByte();
                byte flags = in.readByte();
                int streamId = in.readInt() & 0x7FFFFFFF;

                byte[] payload = new byte[length];
                if (length > 0) {
                    in.readFully(payload);
                }

                if (type == FRAME_SETTINGS && (flags & FLAG_ACK) == 0) {
                    // Acknowledge server SETTINGS
                    writeFrame(out, FRAME_SETTINGS, FLAG_ACK, 0, new byte[0]);
                    out.flush();
                } else if (type == FRAME_PING && (flags & FLAG_ACK) == 0) {
                    writeFrame(out, FRAME_PING, FLAG_ACK, 0, payload);
                    out.flush();
                } else if (type == FRAME_RST_STREAM && streamId == 1) {
                    throw new IOException("gRPC stream reset by server");
                } else if (type == FRAME_GOAWAY) {
                    int errorCode = payload.length >= 8
                            ? ByteBuffer.wrap(payload, 4, 4).getInt()
                            : -1;
                    if (errorCode != 0) {
                        throw new IOException("gRPC server sent GOAWAY error=" + errorCode);
                    }
                } else if (type == FRAME_DATA && streamId == 1) {
                    int offset = 0;
                    int dataLen = payload.length;
                    if ((flags & FLAG_PADDED) != 0 && payload.length > 0) {
                        int padLen = payload[0] & 0xFF;
                        offset = 1;
                        dataLen -= (1 + padLen);
                    }
                    if (dataLen > 0) {
                        dataAccum.write(payload, offset, dataLen);
                    }
                }

                if (streamId == 1 && (flags & FLAG_END_STREAM) != 0) {
                    streamEnded = true;
                }
            }

            byte[] rawGrpcData = dataAccum.toByteArray();
            if (rawGrpcData.length < 5) {
                throw new IOException("gRPC response missing 5-byte length-prefixed header (len=" + rawGrpcData.length + ")");
            }
            ByteBuffer respBuf = ByteBuffer.wrap(rawGrpcData);
            respBuf.order(ByteOrder.BIG_ENDIAN);
            byte compressed = respBuf.get();
            if (compressed != 0) {
                throw new IOException("Compressed gRPC responses are not supported");
            }
            int msgLen = respBuf.getInt();
            if (rawGrpcData.length < 5 + msgLen) {
                throw new IOException("Truncated gRPC message: expected " + msgLen + " bytes, got " + (rawGrpcData.length - 5));
            }
            byte[] protobufResp = new byte[msgLen];
            respBuf.get(protobufResp);
            return protobufResp;
        }
    }

    private static void writeFrame(OutputStream out, byte type, byte flags, int streamId, byte[] payload)
            throws IOException {
        int len = payload.length;
        byte[] header = new byte[9];
        header[0] = (byte) ((len >>> 16) & 0xFF);
        header[1] = (byte) ((len >>> 8) & 0xFF);
        header[2] = (byte) (len & 0xFF);
        header[3] = type;
        header[4] = flags;
        int sid = streamId & 0x7FFFFFFF;
        header[5] = (byte) ((sid >>> 24) & 0xFF);
        header[6] = (byte) ((sid >>> 16) & 0xFF);
        header[7] = (byte) ((sid >>> 8) & 0xFF);
        header[8] = (byte) (sid & 0xFF);
        out.write(header);
        if (len > 0) {
            out.write(payload);
        }
    }

    private static byte[] buildGrpcRequestHeaders(String authority, String path, String internalKey)
            throws IOException {
        ByteArrayOutputStream hpack = new ByteArrayOutputStream(256);
        // :method: POST (static table index 3 -> 0x83)
        hpack.write(0x83);
        // :scheme: http (static table index 6 -> 0x86)
        hpack.write(0x86);
        // :path: <path> (literal without indexing, name index 4 -> 0x04)
        hpack.write(0x04);
        writeHpackString(hpack, path);
        // :authority: <authority> (literal without indexing, name index 1 -> 0x01)
        hpack.write(0x01);
        writeHpackString(hpack, authority);
        // content-type: application/grpc+proto (literal without indexing, name index 31 -> 0x0f 0x10)
        hpack.write(0x0f);
        hpack.write(0x10);
        writeHpackString(hpack, "application/grpc+proto");
        // te: trailers (literal without indexing, new name -> 0x00)
        writeHpackLiteralHeader(hpack, "te", "trailers");
        // user-agent: swiftroute-order-grpc/1.0
        writeHpackLiteralHeader(hpack, "user-agent", "swiftroute-order-grpc/1.0");
        if (internalKey != null && !internalKey.isEmpty()) {
            writeHpackLiteralHeader(hpack, "x-internal-key", internalKey);
        }
        return hpack.toByteArray();
    }

    private static void writeHpackLiteralHeader(ByteArrayOutputStream out, String name, String value)
            throws IOException {
        out.write(0x00);
        writeHpackString(out, name);
        writeHpackString(out, value);
    }

    private static void writeHpackString(ByteArrayOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        // Uncompressed string literal (H=0), 7-bit prefix length
        if (bytes.length < 127) {
            out.write(bytes.length);
        } else {
            out.write(0x7F);
            int rem = bytes.length - 127;
            while ((rem & ~0x7F) != 0) {
                out.write((rem & 0x7F) | 0x80);
                rem >>>= 7;
            }
            out.write(rem);
        }
        out.write(bytes);
    }

    // =========================================================================
    // Protobuf v3 Wire Format Primitives
    // =========================================================================

    private static final class ProtoWriter {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream(128);

        void writeTag(int fieldNumber, int wireType) {
            writeVarint((fieldNumber << 3) | wireType);
        }

        void writeVarint(long value) {
            long v = value;
            while ((v & ~0x7FL) != 0L) {
                baos.write((int) ((v & 0x7FL) | 0x80L));
                v >>>= 7;
            }
            baos.write((int) v);
        }

        void writeInt32(int fieldNumber, int value) {
            if (value != 0) {
                writeTag(fieldNumber, WIRETYPE_VARINT);
                writeVarint(value);
            }
        }

        void writeBool(int fieldNumber, boolean value) {
            if (value) {
                writeTag(fieldNumber, WIRETYPE_VARINT);
                baos.write(1);
            }
        }

        void writeDouble(int fieldNumber, double value) {
            if (value != 0.0) {
                writeTag(fieldNumber, WIRETYPE_FIXED64);
                long bits = Double.doubleToRawLongBits(value);
                for (int i = 0; i < 8; i++) {
                    baos.write((int) ((bits >>> (8 * i)) & 0xFFL));
                }
            }
        }

        void writeString(int fieldNumber, String value) {
            if (value != null && !value.isEmpty()) {
                byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
                writeBytes(fieldNumber, utf8);
            }
        }

        void writeBytes(int fieldNumber, byte[] bytes) {
            if (bytes != null && bytes.length > 0) {
                writeTag(fieldNumber, WIRETYPE_LENGTH_DELIMITED);
                writeVarint(bytes.length);
                baos.writeBytes(bytes);
            }
        }

        byte[] toByteArray() {
            return baos.toByteArray();
        }
    }

    private static final class ProtoReader {
        private final byte[] buf;
        private int pos;
        private final int limit;

        ProtoReader(byte[] buf) {
            this.buf = buf;
            this.pos = 0;
            this.limit = buf.length;
        }

        boolean isAtEnd() {
            return pos >= limit;
        }

        int readTag() throws IOException {
            if (isAtEnd()) {
                return 0;
            }
            return (int) readVarint();
        }

        long readVarint() throws IOException {
            long result = 0;
            int shift = 0;
            while (shift < 64) {
                if (pos >= limit) {
                    throw new IOException("Truncated varint in protobuf payload");
                }
                byte b = buf[pos++];
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
                shift += 7;
            }
            throw new IOException("Malformed varint in protobuf payload");
        }

        int readInt32() throws IOException {
            return (int) readVarint();
        }

        boolean readBool() throws IOException {
            return readVarint() != 0;
        }

        double readDouble() throws IOException {
            if (pos + 8 > limit) {
                throw new IOException("Truncated fixed64 in protobuf payload");
            }
            long bits = 0;
            for (int i = 0; i < 8; i++) {
                bits |= ((long) (buf[pos++] & 0xFF)) << (8 * i);
            }
            return Double.longBitsToDouble(bits);
        }

        byte[] readBytes() throws IOException {
            int len = (int) readVarint();
            if (len < 0 || pos + len > limit) {
                throw new IOException("Invalid length-delimited field length: " + len);
            }
            byte[] out = new byte[len];
            System.arraycopy(buf, pos, out, 0, len);
            pos += len;
            return out;
        }

        String readString() throws IOException {
            return new String(readBytes(), StandardCharsets.UTF_8);
        }

        void skipField(int tag) throws IOException {
            int wireType = tag & 0x07;
            switch (wireType) {
                case WIRETYPE_VARINT -> readVarint();
                case WIRETYPE_FIXED64 -> {
                    if (pos + 8 > limit) throw new IOException("Truncated fixed64");
                    pos += 8;
                }
                case WIRETYPE_LENGTH_DELIMITED -> {
                    int len = (int) readVarint();
                    if (pos + len > limit) throw new IOException("Truncated length-delimited field");
                    pos += len;
                }
                case WIRETYPE_FIXED32 -> {
                    if (pos + 4 > limit) throw new IOException("Truncated fixed32");
                    pos += 4;
                }
                default -> throw new IOException("Unsupported protobuf wire type: " + wireType);
            }
        }
    }

    // =========================================================================
    // Message Encoders & Decoders matching routing.proto
    // =========================================================================

    private static byte[] encodeLocation(LocationPoint loc) {
        if (loc == null) {
            return new byte[0];
        }
        ProtoWriter w = new ProtoWriter();
        w.writeDouble(1, loc.lat());
        w.writeDouble(2, loc.lng());
        w.writeString(3, loc.address());
        return w.toByteArray();
    }

    private static BatchOptimizationResponse.LocationPoint decodeResponseLocation(byte[] bytes) throws IOException {
        ProtoReader r = new ProtoReader(bytes);
        double lat = 0.0;
        double lng = 0.0;
        String address = null;
        while (!r.isAtEnd()) {
            int tag = r.readTag();
            switch (tag >>> 3) {
                case 1 -> lat = r.readDouble();
                case 2 -> lng = r.readDouble();
                case 3 -> address = r.readString();
                default -> r.skipField(tag);
            }
        }
        return new BatchOptimizationResponse.LocationPoint(lat, lng, address);
    }

    private static byte[] encodeStopItem(StopItem item) {
        ProtoWriter w = new ProtoWriter();
        w.writeString(1, item.id());
        w.writeString(2, item.name());
        if (item.location() != null) {
            w.writeBytes(3, encodeLocation(item.location()));
        }
        w.writeDouble(4, item.demandKg());
        w.writeString(5, item.timeWindowStart());
        w.writeString(6, item.timeWindowEnd());
        return w.toByteArray();
    }

    private static byte[] encodeRouteStopState(RouteStopState item) {
        ProtoWriter w = new ProtoWriter();
        w.writeString(1, item.id());
        w.writeString(2, item.name());
        if (item.location() != null) {
            w.writeBytes(3, encodeLocation(item.location()));
        }
        w.writeDouble(4, item.demandKg());
        w.writeString(5, item.timeWindowStart());
        w.writeString(6, item.timeWindowEnd());
        w.writeBool(7, item.isCompleted());
        return w.toByteArray();
    }

    private static OrderedStop decodeOrderedStop(byte[] bytes) throws IOException {
        ProtoReader r = new ProtoReader(bytes);
        int stopIndex = 0;
        String stopId = "";
        BatchOptimizationResponse.LocationPoint location = null;
        double cumulativeDist = 0.0;
        double estArrival = 0.0;
        while (!r.isAtEnd()) {
            int tag = r.readTag();
            switch (tag >>> 3) {
                case 1 -> stopIndex = r.readInt32();
                case 2 -> stopId = r.readString();
                case 3 -> location = decodeResponseLocation(r.readBytes());
                case 4 -> cumulativeDist = r.readDouble();
                case 5 -> estArrival = r.readDouble();
                default -> r.skipField(tag);
            }
        }
        return new OrderedStop(stopIndex, stopId, location, cumulativeDist, estArrival);
    }

    public static byte[] encodeBatchRequest(BatchOptimizationRequest req) {
        ProtoWriter w = new ProtoWriter();
        if (req.depot() != null) {
            w.writeBytes(1, encodeLocation(req.depot()));
        }
        if (req.stops() != null) {
            for (StopItem stop : req.stops()) {
                w.writeBytes(2, encodeStopItem(stop));
            }
        }
        w.writeString(3, req.algorithm());
        if (req.timeWeight() != null) {
            w.writeDouble(4, req.timeWeight());
        }
        if (req.costWeight() != null) {
            w.writeDouble(5, req.costWeight());
        }
        return w.toByteArray();
    }

    public static BatchOptimizationResponse decodeBatchResponse(byte[] bytes) throws IOException {
        ProtoReader r = new ProtoReader(bytes);
        String jobId = "";
        String algorithmUsed = "";
        double totalDistanceKm = 0.0;
        double totalDurationMinutes = 0.0;
        List<OrderedStop> orderedStops = new ArrayList<>();
        double executionTimeMs = 0.0;
        String timestamp = "";
        while (!r.isAtEnd()) {
            int tag = r.readTag();
            switch (tag >>> 3) {
                case 1 -> jobId = r.readString();
                case 2 -> algorithmUsed = r.readString();
                case 3 -> totalDistanceKm = r.readDouble();
                case 4 -> totalDurationMinutes = r.readDouble();
                case 5 -> orderedStops.add(decodeOrderedStop(r.readBytes()));
                case 6 -> executionTimeMs = r.readDouble();
                case 7 -> timestamp = r.readString();
                default -> r.skipField(tag);
            }
        }
        return new BatchOptimizationResponse(
                jobId, algorithmUsed, totalDistanceKm, totalDurationMinutes, orderedStops, executionTimeMs, timestamp);
    }

    public static byte[] encodeInsertRequest(RouteInsertRequest req) {
        ProtoWriter w = new ProtoWriter();
        if (req.driverLocation() != null) {
            w.writeBytes(1, encodeLocation(req.driverLocation()));
        }
        if (req.currentStops() != null) {
            for (RouteStopState s : req.currentStops()) {
                w.writeBytes(2, encodeRouteStopState(s));
            }
        }
        if (req.newStop() != null) {
            w.writeBytes(3, encodeStopItem(req.newStop()));
        }
        w.writeDouble(4, req.maxAddedDurationMinutes());
        if (req.timeWeight() != null) {
            w.writeDouble(5, req.timeWeight());
        }
        if (req.costWeight() != null) {
            w.writeDouble(6, req.costWeight());
        }
        return w.toByteArray();
    }

    public static RouteInsertResponse decodeInsertResponse(byte[] bytes) throws IOException {
        ProtoReader r = new ProtoReader(bytes);
        String jobId = "";
        String algorithmUsed = "";
        boolean feasible = false;
        int insertedStopIndex = 0;
        double addedDistanceKm = 0.0;
        double addedDurationMinutes = 0.0;
        double totalDistanceKm = 0.0;
        double totalDurationMinutes = 0.0;
        List<OrderedStop> orderedStops = new ArrayList<>();
        double executionTimeMs = 0.0;
        String timestamp = "";
        while (!r.isAtEnd()) {
            int tag = r.readTag();
            switch (tag >>> 3) {
                case 1 -> jobId = r.readString();
                case 2 -> algorithmUsed = r.readString();
                case 3 -> feasible = r.readBool();
                case 4 -> insertedStopIndex = r.readInt32();
                case 5 -> addedDistanceKm = r.readDouble();
                case 6 -> addedDurationMinutes = r.readDouble();
                case 7 -> totalDistanceKm = r.readDouble();
                case 8 -> totalDurationMinutes = r.readDouble();
                case 9 -> orderedStops.add(decodeOrderedStop(r.readBytes()));
                case 10 -> executionTimeMs = r.readDouble();
                case 11 -> timestamp = r.readString();
                default -> r.skipField(tag);
            }
        }
        return new RouteInsertResponse(
                jobId,
                algorithmUsed,
                feasible,
                insertedStopIndex,
                addedDistanceKm,
                addedDurationMinutes,
                totalDistanceKm,
                totalDurationMinutes,
                orderedStops,
                executionTimeMs,
                timestamp);
    }

    public static byte[] encodeReoptimizeRequest(RouteReoptimizeRequest req) {
        ProtoWriter w = new ProtoWriter();
        if (req.driverLocation() != null) {
            w.writeBytes(1, encodeLocation(req.driverLocation()));
        }
        if (req.currentStops() != null) {
            for (RouteStopState s : req.currentStops()) {
                w.writeBytes(2, encodeRouteStopState(s));
            }
        }
        w.writeString(3, req.reason());
        w.writeDouble(4, req.minImprovementKm());
        if (req.timeWeight() != null) {
            w.writeDouble(5, req.timeWeight());
        }
        if (req.costWeight() != null) {
            w.writeDouble(6, req.costWeight());
        }
        return w.toByteArray();
    }

    public static RouteReoptimizeResponse decodeReoptimizeResponse(byte[] bytes) throws IOException {
        ProtoReader r = new ProtoReader(bytes);
        String jobId = "";
        String algorithmUsed = "";
        boolean stopsChanged = false;
        double distanceSavedKm = 0.0;
        double totalDistanceKm = 0.0;
        double totalDurationMinutes = 0.0;
        List<OrderedStop> orderedStops = new ArrayList<>();
        double executionTimeMs = 0.0;
        String timestamp = "";
        while (!r.isAtEnd()) {
            int tag = r.readTag();
            switch (tag >>> 3) {
                case 1 -> jobId = r.readString();
                case 2 -> algorithmUsed = r.readString();
                case 3 -> stopsChanged = r.readBool();
                case 4 -> distanceSavedKm = r.readDouble();
                case 5 -> totalDistanceKm = r.readDouble();
                case 6 -> totalDurationMinutes = r.readDouble();
                case 7 -> orderedStops.add(decodeOrderedStop(r.readBytes()));
                case 8 -> executionTimeMs = r.readDouble();
                case 9 -> timestamp = r.readString();
                default -> r.skipField(tag);
            }
        }
        return new RouteReoptimizeResponse(
                jobId,
                algorithmUsed,
                stopsChanged,
                distanceSavedKm,
                totalDistanceKm,
                totalDurationMinutes,
                orderedStops,
                executionTimeMs,
                timestamp);
    }
}
