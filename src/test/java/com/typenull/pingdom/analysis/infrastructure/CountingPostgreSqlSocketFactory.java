package com.typenull.pingdom.analysis.infrastructure;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.net.SocketFactory;

/** 로컬 PostgreSQL 비교에서 현재 요청 스레드가 송수신한 protocol byte만 집계한다. */
public class CountingPostgreSqlSocketFactory extends SocketFactory {

    private static final ThreadLocal<long[]> COUNTS = new ThreadLocal<>();

    static void startCounting() {
        COUNTS.set(new long[2]);
    }

    static long receivedBytes() {
        return COUNTS.get()[0];
    }

    static long sentBytes() {
        return COUNTS.get()[1];
    }

    static void stopCounting() {
        COUNTS.remove();
    }

    private static void received(int bytes) {
        long[] counts = COUNTS.get();
        if (counts != null && bytes > 0) {
            counts[0] += bytes;
        }
    }

    private static void sent(int bytes) {
        long[] counts = COUNTS.get();
        if (counts != null && bytes > 0) {
            counts[1] += bytes;
        }
    }

    @Override
    public Socket createSocket() {
        return new CountingSocket();
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return connect(InetAddress.getByName(host), port, null, 0);
    }

    @Override
    public Socket createSocket(InetAddress address, int port) throws IOException {
        return connect(address, port, null, 0);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException {
        return connect(InetAddress.getByName(host), port, localAddress, localPort);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException {
        return connect(address, port, localAddress, localPort);
    }

    private Socket connect(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        Socket socket = createSocket();
        try {
            if (localAddress != null) {
                socket.bind(new InetSocketAddress(localAddress, localPort));
            }
            socket.connect(new InetSocketAddress(address, port));
            return socket;
        } catch (IOException exception) {
            socket.close();
            throw exception;
        }
    }

    private static class CountingSocket extends Socket {
        private InputStream countedInput;
        private OutputStream countedOutput;

        @Override
        public InputStream getInputStream() throws IOException {
            if (countedInput == null) {
                countedInput = new FilterInputStream(super.getInputStream()) {
                    @Override
                    public int read() throws IOException {
                        int value = in.read();
                        received(value < 0 ? 0 : 1);
                        return value;
                    }

                    @Override
                    public int read(byte[] bytes, int offset, int length) throws IOException {
                        int count = in.read(bytes, offset, length);
                        received(count);
                        return count;
                    }
                };
            }
            return countedInput;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            if (countedOutput == null) {
                countedOutput = new FilterOutputStream(super.getOutputStream()) {
                    @Override
                    public void write(int value) throws IOException {
                        out.write(value);
                        sent(1);
                    }

                    @Override
                    public void write(byte[] bytes, int offset, int length) throws IOException {
                        out.write(bytes, offset, length);
                        sent(length);
                    }
                };
            }
            return countedOutput;
        }
    }
}
